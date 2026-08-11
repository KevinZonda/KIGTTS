package com.lhtstudio.kigtts.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

internal data class DebugAudioClip(
    val samples: FloatArray,
    val sampleRate: Int,
    val sourceLabel: String
)

internal enum class SimulatedAudioScenario(val id: String) {
    CLEAN("clean"),
    QUIET("quiet"),
    MUFFLED("muffled"),
    NOISY("noisy"),
    REVERBERANT("reverberant"),
    HEADSHELL_NOISY("headshell_noisy");

    companion object {
        fun parse(raw: String?): List<SimulatedAudioScenario> {
            val requested = raw
                ?.lowercase(Locale.ROOT)
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
            if (requested.isEmpty() || "all" in requested) return entries
            return requested.mapNotNull { id -> entries.firstOrNull { it.id == id } }.distinct()
                .ifEmpty { listOf(CLEAN) }
        }
    }
}

internal object SimulatedAudioFixtures {
    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        require(sourceRate > 0 && targetRate > 0)
        if (samples.isEmpty() || sourceRate == targetRate) return samples.copyOf()
        val outputSize = (
            samples.size.toLong() * targetRate.toLong() / sourceRate.toLong()
            ).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (samples.size == 1 || outputSize == 1) return FloatArray(outputSize) { samples[0] }
        val scale = (samples.size - 1).toDouble() / (outputSize - 1).toDouble()
        return FloatArray(outputSize) { outputIndex ->
            val sourcePosition = outputIndex * scale
            val lower = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val upper = minOf(lower + 1, samples.lastIndex)
            val fraction = (sourcePosition - lower).toFloat()
            samples[lower] + (samples[upper] - samples[lower]) * fraction
        }
    }

    fun readPcm16Wave(file: File): DebugAudioClip {
        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF") { "Not a RIFF file" }
            input.readLittleEndianInt()
            require(input.readAscii(4) == "WAVE") { "Not a WAVE file" }
            var format = 0
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = 0
            while (input.filePointer + 8 <= input.length()) {
                val chunk = input.readAscii(4)
                val size = input.readLittleEndianInt()
                require(size >= 0) { "Invalid WAV chunk size" }
                val next = input.filePointer + size + (size and 1)
                when (chunk) {
                    "fmt " -> {
                        format = input.readLittleEndianShort()
                        channels = input.readLittleEndianShort()
                        sampleRate = input.readLittleEndianInt()
                        input.seek(input.filePointer + 6)
                        bitsPerSample = input.readLittleEndianShort()
                    }
                    "data" -> {
                        dataOffset = input.filePointer
                        dataSize = size
                    }
                }
                input.seek(next.coerceAtMost(input.length()))
                if (dataOffset >= 0 && sampleRate > 0) break
            }
            require(format == 1 && channels in 1..2 && bitsPerSample == 16 && dataOffset >= 0) {
                "Expected mono/stereo PCM16 WAV"
            }
            input.seek(dataOffset)
            val frameBytes = channels * 2
            val frames = dataSize / frameBytes
            val samples = FloatArray(frames)
            for (frame in 0 until frames) {
                var mixed = 0f
                repeat(channels) {
                    mixed += input.readLittleEndianShort().toShort() / 32768f
                }
                samples[frame] = mixed / channels
            }
            return DebugAudioClip(samples, sampleRate, "wav:${file.name}")
        }
    }

    fun prepareScenario(
        source: DebugAudioClip,
        scenario: SimulatedAudioScenario,
        leadingSilenceMs: Int = 350,
        trailingSilenceMs: Int = 1100
    ): DebugAudioClip {
        val transformed = when (scenario) {
            SimulatedAudioScenario.CLEAN -> source.samples.copyOf()
            SimulatedAudioScenario.QUIET -> scale(source.samples, 0.24f)
            SimulatedAudioScenario.MUFFLED -> scale(lowPass(source.samples, source.sampleRate, 1050.0), 0.72f)
            SimulatedAudioScenario.NOISY -> addDeterministicNoise(source.samples, snrDb = 7.0, seed = 20260809L)
            SimulatedAudioScenario.REVERBERANT -> addReverberation(source.samples, source.sampleRate)
            SimulatedAudioScenario.HEADSHELL_NOISY -> addDeterministicNoise(
                scale(lowPass(source.samples, source.sampleRate, 900.0), 0.58f),
                snrDb = 5.0,
                seed = 20260810L
            )
        }
        val leading = source.sampleRate * leadingSilenceMs.coerceAtLeast(0) / 1000
        val trailing = source.sampleRate * trailingSilenceMs.coerceAtLeast(0) / 1000
        val padded = FloatArray(leading + transformed.size + trailing)
        System.arraycopy(transformed, 0, padded, leading, transformed.size)
        return DebugAudioClip(
            samples = padded,
            sampleRate = source.sampleRate,
            sourceLabel = "${source.sourceLabel}/${scenario.id}"
        )
    }

    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        return sqrt(samples.sumOf { it.toDouble() * it.toDouble() } / samples.size)
    }

    private fun scale(samples: FloatArray, gain: Float): FloatArray =
        FloatArray(samples.size) { index -> (samples[index] * gain).coerceIn(-0.98f, 0.98f) }

    private fun lowPass(samples: FloatArray, sampleRate: Int, cutoffHz: Double): FloatArray {
        if (samples.isEmpty()) return samples.copyOf()
        val dt = 1.0 / sampleRate.coerceAtLeast(1)
        val rc = 1.0 / (2.0 * PI * cutoffHz.coerceAtLeast(20.0))
        val alpha = (dt / (rc + dt)).toFloat()
        val firstPass = FloatArray(samples.size)
        var state = samples[0]
        for (index in samples.indices) {
            state += alpha * (samples[index] - state)
            firstPass[index] = state
        }
        val secondPass = FloatArray(samples.size)
        state = firstPass[0]
        for (index in firstPass.indices) {
            state += alpha * (firstPass[index] - state)
            secondPass[index] = state.coerceIn(-0.98f, 0.98f)
        }
        return secondPass
    }

    private fun addDeterministicNoise(
        samples: FloatArray,
        snrDb: Double,
        seed: Long
    ): FloatArray {
        if (samples.isEmpty()) return samples.copyOf()
        val signalRms = rms(samples).coerceAtLeast(0.001)
        val noiseRms = signalRms / 10.0.pow(snrDb / 20.0)
        val random = Random(seed)
        var previous = 0.0
        return FloatArray(samples.size) { index ->
            val white = random.nextGaussian()
            val colored = white * 0.72 + previous * 0.28
            previous = colored
            (samples[index] + colored * noiseRms).toFloat().coerceIn(-0.98f, 0.98f)
        }
    }

    private fun addReverberation(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples.copyOf()
        val taps = listOf(
            65 to 0.32f,
            125 to 0.22f,
            205 to 0.14f,
            310 to 0.08f
        )
        val output = samples.copyOf()
        taps.forEach { (delayMs, gain) ->
            val delay = sampleRate * delayMs / 1000
            for (index in delay until output.size) {
                val phase = index.toDouble() / sampleRate.coerceAtLeast(1)
                val diffuseGain = gain * (0.96 + 0.04 * cos(phase * 2.0 * PI)).toFloat()
                output[index] += samples[index - delay] * diffuseGain
            }
        }
        val peak = output.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1f) ?: 1f
        return FloatArray(output.size) { index -> (output[index] / peak).coerceIn(-0.98f, 0.98f) }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readLittleEndianInt(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)
}
