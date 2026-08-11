package com.lhtstudio.kigtts.app.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.lhtstudio.kigtts.app.util.AppLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin

internal object SpeechBrainEcapaEmbedder {
    private const val SAMPLE_RATE = 16_000
    private const val MAX_SAMPLES = SAMPLE_RATE * 8
    private const val MIN_SAMPLES = SAMPLE_RATE
    private const val EMBEDDING_DIM = 192

    private val lock = Any()
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var modelPath: String? = null
    private var session: OrtSession? = null
    private var extractor: SpeechBrainFbankExtractor? = null

    fun compute(
        context: Context,
        modelFile: File,
        samples: FloatArray,
        sampleRate: Int
    ): FloatArray? = synchronized(lock) {
        if (!modelFile.isFile || samples.isEmpty() || sampleRate <= 0) return@synchronized null
        val audio16k = LinearAudioResampler.resample(samples, sampleRate, SAMPLE_RATE)
        if (audio16k.size < MIN_SAMPLES) return@synchronized null
        val clipped = if (audio16k.size > MAX_SAMPLES) {
            audio16k.copyOfRange(0, MAX_SAMPLES)
        } else {
            audio16k
        }
        try {
            val activeSession = ensureSession(modelFile)
            val activeExtractor = extractor ?: SpeechBrainFbankExtractor.fromAssets(context).also {
                extractor = it
            }
            val features = activeExtractor.compute(clipped)
            val frames = features.size / SpeechBrainFbankExtractor.N_MELS
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(features),
                longArrayOf(1L, frames.toLong(), SpeechBrainFbankExtractor.N_MELS.toLong())
            ).use { input ->
                activeSession.run(mapOf("features" to input)).use { result ->
                    val output = result.get("embedding").orElseGet { result.get(0) } as? OnnxTensor
                        ?: return@synchronized null
                    val buffer = output.floatBuffer
                    if (buffer.remaining() < EMBEDDING_DIM) return@synchronized null
                    FloatArray(EMBEDDING_DIM) { index -> buffer.get(index) }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("SpeechBrain ECAPA embedding failed", t)
            null
        }
    }

    fun releaseModel() = synchronized(lock) {
        runCatching { session?.close() }
        session = null
        modelPath = null
    }

    private fun ensureSession(modelFile: File): OrtSession {
        val path = modelFile.absolutePath
        session?.takeIf { modelPath == path }?.let { return it }
        releaseModel()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
            setInterOpNumThreads(1)
        }
        return try {
            environment.createSession(path, options).also {
                session = it
                modelPath = path
            }
        } finally {
            options.close()
        }
    }
}

internal class SpeechBrainFbankExtractor private constructor(
    private val filterbank: FloatArray
) {
    private val window = FloatArray(N_FFT) { index ->
        (0.54 - 0.46 * cos(2.0 * PI * index / N_FFT)).toFloat()
    }
    private val cosTable = FloatArray(N_STFT * N_FFT)
    private val sinTable = FloatArray(N_STFT * N_FFT)

    init {
        require(filterbank.size == N_STFT * N_MELS)
        for (bin in 0 until N_STFT) {
            val row = bin * N_FFT
            for (sample in 0 until N_FFT) {
                val angle = 2.0 * PI * bin * sample / N_FFT
                cosTable[row + sample] = cos(angle).toFloat()
                sinTable[row + sample] = sin(angle).toFloat()
            }
        }
    }

    fun compute(audio: FloatArray): FloatArray {
        val frames = 1 + audio.size / HOP_LENGTH
        val output = FloatArray(frames * N_MELS)
        val frame = FloatArray(N_FFT)
        val power = FloatArray(N_STFT)
        var maxDb = Float.NEGATIVE_INFINITY
        for (frameIndex in 0 until frames) {
            val paddedStart = frameIndex * HOP_LENGTH
            for (i in 0 until N_FFT) {
                val audioIndex = paddedStart + i - N_FFT / 2
                frame[i] = if (audioIndex in audio.indices) audio[audioIndex] * window[i] else 0f
            }
            for (bin in 0 until N_STFT) {
                val tableOffset = bin * N_FFT
                var real = 0.0
                var imaginary = 0.0
                for (i in 0 until N_FFT) {
                    val value = frame[i]
                    real += value * cosTable[tableOffset + i]
                    imaginary -= value * sinTable[tableOffset + i]
                }
                power[bin] = (real * real + imaginary * imaginary).toFloat()
            }
            val outputOffset = frameIndex * N_MELS
            for (mel in 0 until N_MELS) {
                var energy = 0.0
                for (bin in 0 until N_STFT) {
                    energy += power[bin] * filterbank[bin * N_MELS + mel]
                }
                val db = (10.0 * log10(energy.coerceAtLeast(1e-10))).toFloat()
                output[outputOffset + mel] = db
                if (db > maxDb) maxDb = db
            }
        }
        val floor = maxDb - 80f
        for (index in output.indices) {
            if (output[index] < floor) output[index] = floor
        }
        return output
    }

    companion object {
        const val N_MELS = 80
        private const val N_FFT = 400
        private const val HOP_LENGTH = 160
        private const val N_STFT = N_FFT / 2 + 1
        private const val FILTERBANK_ASSET = "neural_speaker_filter/fbank_filterbank.bin"

        fun fromAssets(context: Context): SpeechBrainFbankExtractor {
            val bytes = context.assets.open(FILTERBANK_ASSET).use { it.readBytes() }
            require(bytes.size == N_STFT * N_MELS * Float.SIZE_BYTES)
            val floats = FloatArray(N_STFT * N_MELS)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
            return SpeechBrainFbankExtractor(floats)
        }

        internal fun fromFilterbank(filterbank: FloatArray): SpeechBrainFbankExtractor {
            return SpeechBrainFbankExtractor(filterbank.copyOf())
        }
    }
}

internal object LinearAudioResampler {
    fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (input.isEmpty() || sourceRate <= 0 || targetRate <= 0 || sourceRate == targetRate) {
            return input.copyOf()
        }
        val outputSize = ((input.size.toLong() * targetRate + sourceRate / 2L) / sourceRate)
            .toInt()
            .coerceAtLeast(1)
        if (input.size == 1) return FloatArray(outputSize) { input[0] }
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        return FloatArray(outputSize) { index ->
            val sourcePosition = index * ratio
            val lower = sourcePosition.toInt().coerceIn(0, input.lastIndex)
            val upper = (lower + 1).coerceAtMost(input.lastIndex)
            val fraction = (sourcePosition - lower).toFloat()
            input[lower] + (input[upper] - input[lower]) * fraction
        }
    }
}
