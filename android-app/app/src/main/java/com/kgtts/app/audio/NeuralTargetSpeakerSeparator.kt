package com.lhtstudio.kigtts.app.audio

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.lhtstudio.kigtts.app.util.AppLogger
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedHashMap
import kotlin.math.min

internal data class NeuralSeparationResult(
    val audio: FloatArray,
    val elapsedMs: Long,
    val realtimeFactor: Float
)

internal class NeuralTargetSpeakerSeparator(
    modelFile: File
) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = createSession(modelFile)
    private val audioInputName = session.inputNames.firstOrNull { it == "audio_chunk" }
        ?: error("TSE model is missing audio_chunk input")
    private val conditionInputName = session.inputNames.firstOrNull {
        it.contains("cond", ignoreCase = true) && !it.startsWith("state_")
    } ?: error("TSE model is missing condition input")
    private val extractedOutputName = session.outputNames.firstOrNull { it == "extracted_chunk" }
        ?: error("TSE model is missing extracted_chunk output")

    private val audioSlot = TensorSlot(environment, longArrayOf(1L, CHUNK_SAMPLES.toLong()))
    private val conditionSlot = TensorSlot(environment, longArrayOf(1L, CONDITION_DIM.toLong()))
    private val extractedSlot = TensorSlot(environment, longArrayOf(1L, CHUNK_SAMPLES.toLong()))
    private val stateInputNames = session.inputNames
        .filter { it.startsWith("state_in_") }
        .sortedBy(::stateIndex)
    private val stateOutputNames = session.outputNames
        .filter { it.startsWith("state_out_") }
        .sortedBy(::stateIndex)
    private val stateBankA = createStateBank(session.inputInfo, stateInputNames)
    private val stateBankB = createStateBank(session.outputInfo, stateOutputNames)
    private val inputsA = buildInputs(stateBankA)
    private val inputsB = buildInputs(stateBankB.mapKeys { (name, _) -> name.replace("state_out_", "state_in_") })
    private val outputsA = buildOutputs(stateBankA.mapKeys { (name, _) -> name.replace("state_in_", "state_out_") })
    private val outputsB = buildOutputs(stateBankB)
    private var inputUsesBankA = true
    private var executionFailed = false
    private var closed = false

    init {
        require(stateInputNames.size == stateOutputNames.size) {
            "TSE state input/output count mismatch"
        }
        require(stateInputNames.isNotEmpty()) { "TSE model has no streaming state" }
    }

    @Synchronized
    fun separate(
        audio: FloatArray,
        sampleRate: Int,
        condition: FloatArray
    ): NeuralSeparationResult? {
        if (
            closed ||
            executionFailed ||
            audio.isEmpty() ||
            sampleRate <= 0 ||
            condition.size != CONDITION_DIM
        ) return null
        resetState()
        conditionSlot.put(condition)
        val audio48k = LinearAudioResampler.resample(audio, sampleRate, MODEL_SAMPLE_RATE)
        val output48k = FloatArray(audio48k.size)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var offset = 0
        try {
            while (offset < audio48k.size) {
                val count = min(CHUNK_SAMPLES, audio48k.size - offset)
                audioSlot.put(audio48k, offset, count)
                val inputs = if (inputUsesBankA) inputsA else inputsB
                val outputs = if (inputUsesBankA) outputsB else outputsA
                session.run(inputs, outputs).use {
                    extractedSlot.copyTo(output48k, offset, count)
                }
                inputUsesBankA = !inputUsesBankA
                offset += count
            }
        } catch (t: Throwable) {
            executionFailed = true
            AppLogger.e("Neural target speaker separation failed", t)
            return null
        }
        val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
        val durationMs = audio.size * 1000f / sampleRate
        val restored = LinearAudioResampler.resample(output48k, MODEL_SAMPLE_RATE, sampleRate)
        val exactLength = if (restored.size == audio.size) restored else restored.copyOf(audio.size)
        return NeuralSeparationResult(
            audio = exactLength,
            elapsedMs = elapsedMs,
            realtimeFactor = elapsedMs / durationMs.coerceAtLeast(1f)
        )
    }

    @Synchronized
    fun resetState() {
        if (closed) return
        stateBankA.values.forEach(TensorSlot::clear)
        stateBankB.values.forEach(TensorSlot::clear)
        audioSlot.clear()
        extractedSlot.clear()
        inputUsesBankA = true
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { session.close() }
        runCatching { audioSlot.close() }
        runCatching { conditionSlot.close() }
        runCatching { extractedSlot.close() }
        stateBankA.values.forEach { slot -> runCatching { slot.close() } }
        stateBankB.values.forEach { slot -> runCatching { slot.close() } }
    }

    private fun buildInputs(states: Map<String, TensorSlot>): Map<String, OnnxTensor> {
        return LinkedHashMap<String, OnnxTensor>(states.size + 2).apply {
            put(audioInputName, audioSlot.tensor)
            put(conditionInputName, conditionSlot.tensor)
            states.forEach { (name, slot) -> put(name, slot.tensor) }
        }
    }

    private fun buildOutputs(states: Map<String, TensorSlot>): Map<String, OnnxTensor> {
        return LinkedHashMap<String, OnnxTensor>(states.size + 1).apply {
            put(extractedOutputName, extractedSlot.tensor)
            states.forEach { (name, slot) -> put(name, slot.tensor) }
        }
    }

    private fun createStateBank(
        info: Map<String, NodeInfo>,
        names: List<String>
    ): Map<String, TensorSlot> {
        return LinkedHashMap<String, TensorSlot>(names.size).apply {
            names.forEach { name ->
                val tensorInfo = info[name]?.info as? TensorInfo
                    ?: error("TSE state $name is not a tensor")
                val shape = tensorInfo.shape
                require(shape.all { it > 0L }) { "TSE state $name has dynamic shape" }
                put(name, TensorSlot(environment, shape))
            }
        }
    }

    private fun createSession(modelFile: File): OrtSession {
        require(modelFile.isFile) { "TSE model not found: ${modelFile.absolutePath}" }
        val threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        val accelerated = runCatching {
            OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                options.addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                environment.createSession(modelFile.absolutePath, options)
            }
        }.onFailure {
            AppLogger.e("TSE XNNPACK session unavailable, falling back to CPU", it)
        }.getOrNull()
        if (accelerated != null) return accelerated

        val cpuOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(1)
        }
        return try {
            environment.createSession(modelFile.absolutePath, cpuOptions)
        } finally {
            cpuOptions.close()
        }
    }

    private fun stateIndex(name: String): Int = name.substringAfterLast('_').toIntOrNull() ?: Int.MAX_VALUE

    private class TensorSlot(
        environment: OrtEnvironment,
        val shape: LongArray
    ) : Closeable {
        private val elementCount = shape.fold(1L) { total, dimension -> total * dimension }
            .toInt()
        private val buffer: FloatBuffer = ByteBuffer
            .allocateDirect(elementCount * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val tensor: OnnxTensor = OnnxTensor.createTensor(environment, buffer, shape)

        fun put(values: FloatArray, offset: Int = 0, count: Int = values.size) {
            clear()
            val safeCount = min(count, elementCount)
            buffer.position(0)
            buffer.put(values, offset, safeCount)
            buffer.position(0)
        }

        fun copyTo(target: FloatArray, offset: Int, count: Int) {
            val safeCount = min(count, elementCount)
            for (index in 0 until safeCount) {
                target[offset + index] = buffer.get(index)
            }
        }

        fun clear() {
            for (index in 0 until elementCount) buffer.put(index, 0f)
            buffer.position(0)
        }

        override fun close() {
            tensor.close()
        }
    }

    companion object {
        const val MODEL_SAMPLE_RATE = 48_000
        private const val CHUNK_SAMPLES = 480
        const val CONDITION_DIM = 192
    }
}
