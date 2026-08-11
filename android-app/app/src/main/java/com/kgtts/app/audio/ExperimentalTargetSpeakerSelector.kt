package com.lhtstudio.kigtts.app.audio

import kotlin.math.max
import kotlin.math.sqrt

object ExperimentalTargetSpeakerBackend {
    const val AUTO = 0
    const val NEURAL = 1
    const val LIGHTWEIGHT = 2

    fun normalize(mode: Int): Int = mode.coerceIn(AUTO, LIGHTWEIGHT)

    fun shouldUseNeural(
        mode: Int,
        resourcesInstalled: Boolean,
        neuralProfileReady: Boolean
    ): Boolean {
        if (normalize(mode) == LIGHTWEIGHT) return false
        return resourcesInstalled && neuralProfileReady
    }
}

internal object NeuralSeparationActivationPolicy {
    private const val AUTO_CONFIDENT_MARGIN = 0.10f

    fun shouldRun(mode: Int, baselineSimilarity: Float, verificationThreshold: Float): Boolean {
        return when (ExperimentalTargetSpeakerBackend.normalize(mode)) {
            ExperimentalTargetSpeakerBackend.NEURAL -> true
            ExperimentalTargetSpeakerBackend.LIGHTWEIGHT -> false
            else -> baselineSimilarity <
                (verificationThreshold + AUTO_CONFIDENT_MARGIN).coerceAtMost(0.98f)
        }
    }
}

internal object NeuralSeparationQualityPolicy {
    private const val THRESHOLD_MARGIN = 0.04f
    private const val BASELINE_MARGIN = 0.10f
    private const val MIN_ENERGY_RATIO = 0.03
    private const val MAX_ENERGY_RATIO = 2.2

    fun accepts(
        candidateSimilarity: Float,
        baselineSimilarity: Float,
        verificationThreshold: Float,
        energyRatio: Double
    ): Boolean {
        val minimumSimilarity = max(
            (verificationThreshold - THRESHOLD_MARGIN).coerceAtLeast(0.05f),
            baselineSimilarity - BASELINE_MARGIN
        )
        return candidateSimilarity >= minimumSimilarity &&
            energyRatio in MIN_ENERGY_RATIO..MAX_ENERGY_RATIO
    }
}

internal object NeuralSeparationPerformancePolicy {
    private const val MAX_AUTO_REALTIME_FACTOR = 0.85f

    fun shouldDisableAuto(realtimeFactor: Float): Boolean =
        !realtimeFactor.isFinite() || realtimeFactor > MAX_AUTO_REALTIME_FACTOR
}

internal object ExperimentalRecognitionSensitivityPolicy {
    fun minSegmentRms(baseRms: Double, sensitivity: Int): Double {
        val multiplier = 0.85 - 0.65 * fraction(sensitivity)
        return max(0.0025, baseRms * multiplier)
    }

    fun classicVadThreshold(base: Double, sensitivity: Int): Double {
        val multiplier = 1.0 - 0.55 * fraction(sensitivity)
        return max(base * 0.35, base * multiplier)
    }

    fun endpointSilenceThreshold(base: Double, sensitivity: Int): Double {
        val multiplier = 1.0 - 0.15 * fraction(sensitivity)
        return max(base * 0.8, base * multiplier)
    }

    fun applyInputGain(samples: FloatArray, sensitivity: Int): FloatArray {
        if (samples.isEmpty()) return samples
        val normalizedSensitivity = fraction(sensitivity)
        if (normalizedSensitivity <= 0.0) return samples
        var sumSquares = 0.0
        var peak = 0.0
        samples.forEach { sample ->
            val value = sample.toDouble()
            sumSquares += value * value
            peak = max(peak, kotlin.math.abs(value))
        }
        val rms = sqrt(sumSquares / samples.size)
        if (rms <= 1e-6) return samples
        val targetRms = 0.025 + 0.025 * normalizedSensitivity
        val maxGain = 1.0 + 5.0 * normalizedSensitivity
        val peakLimitedGain = if (peak > 1e-6) 0.98 / peak else maxGain
        val gain = minOf(targetRms / rms, maxGain, peakLimitedGain)
        if (gain <= 1.02) return samples
        return FloatArray(samples.size) { index ->
            (samples[index] * gain).toFloat().coerceIn(-0.98f, 0.98f)
        }
    }

    private fun fraction(sensitivity: Int): Double {
        return sensitivity.coerceIn(0, 100) / 100.0
    }
}

internal object RecognitionWindowPolicy {
    fun shouldSubmit(
        durationMs: Int,
        voicedMs: Int,
        rms: Double,
        minRms: Double,
        minDurationMs: Int = 300,
        minVoicedMs: Int = 120,
        minVoicedRatio: Double = 0.10
    ): Boolean {
        if (durationMs < minDurationMs || rms < minRms) return false
        val voicedRatio = voicedMs.toDouble() / durationMs.coerceAtLeast(1)
        return voicedMs >= minVoicedMs && voicedRatio >= minVoicedRatio
    }
}

internal data class ExperimentalTargetSpeakerSelection(
    val audio: FloatArray?,
    val bestSimilarity: Float,
    val evaluatedWindows: Int,
    val insufficientAudio: Boolean
) {
    val targetDetected: Boolean
        get() = audio?.isNotEmpty() == true
}

internal interface TargetSpeakerFrontend {
    fun reset()
    fun isTargetActive(): Boolean
    fun minimumSamples(sampleRate: Int): Int
    fun process(
        audio: FloatArray,
        sampleRate: Int,
        threshold: Float,
        scoreWindow: (FloatArray) -> Float?
    ): ExperimentalTargetSpeakerSelection
}

/**
 * Lightweight speaker-conditioned time mask inspired by VoiceFilter-Lite.
 * It keeps the ASR model unchanged and can later be replaced by a neural mask frontend.
 */
internal class VoiceFilterInspiredTargetSpeakerFrontend(
    private val windowMs: Int = 1200,
    private val minimumWindowMs: Int = 600,
    private val hopMs: Int = 400,
    private val contextMs: Int = 140,
    private val fadeMs: Int = 20,
    private val releaseMargin: Float = 0.08f
) : TargetSpeakerFrontend {
    private val singleWindowHighConfidenceMargin = 0.22f
    private var targetActive = false

    @Synchronized
    override fun reset() {
        targetActive = false
    }

    @Synchronized
    override fun isTargetActive(): Boolean = targetActive

    override fun minimumSamples(sampleRate: Int): Int {
        return sampleRate.coerceAtLeast(1) * minimumWindowMs / 1000
    }

    @Synchronized
    override fun process(
        audio: FloatArray,
        sampleRate: Int,
        threshold: Float,
        scoreWindow: (FloatArray) -> Float?
    ): ExperimentalTargetSpeakerSelection {
        val normalizedRate = sampleRate.coerceAtLeast(1)
        if (audio.size < minimumSamples(normalizedRate)) {
            return ExperimentalTargetSpeakerSelection(
                audio = null,
                bestSimilarity = -1f,
                evaluatedWindows = 0,
                insufficientAudio = true
            )
        }

        val windowSamples = minOf(audio.size, normalizedRate * windowMs / 1000)
        val hopSamples = max(1, normalizedRate * hopMs / 1000)
        val starts = buildWindowStarts(audio.size, windowSamples, hopSamples)
        val acceptedRanges = mutableListOf<WeightedSampleRange>()
        var bestSimilarity = -1f
        var evaluatedWindows = 0
        var acceptedWindows = 0
        val enterThreshold = threshold.coerceIn(0.05f, 0.95f)
        val exitThreshold = (enterThreshold - releaseMargin).coerceAtLeast(0.05f)

        starts.forEach { start ->
            val end = minOf(audio.size, start + windowSamples)
            val score = scoreWindow(audio.copyOfRange(start, end)) ?: return@forEach
            evaluatedWindows++
            bestSimilarity = max(bestSimilarity, score)
            val accepted = score >= if (targetActive) exitThreshold else enterThreshold
            targetActive = accepted
            if (accepted) {
                acceptedWindows++
                val contextSamples = normalizedRate * contextMs / 1000
                acceptedRanges += WeightedSampleRange(
                    start = (start - contextSamples).coerceAtLeast(0),
                    endExclusive = (end + contextSamples).coerceAtMost(audio.size),
                    similarity = score
                )
            }
        }

        val hasSustainedEvidence = acceptedRanges.isNotEmpty() && (
            evaluatedWindows <= 1 ||
                acceptedWindows >= 2 ||
                bestSimilarity >= (enterThreshold + singleWindowHighConfidenceMargin).coerceAtMost(0.98f)
            )
        if (!hasSustainedEvidence) {
            targetActive = false
            return ExperimentalTargetSpeakerSelection(
                audio = null,
                bestSimilarity = bestSimilarity,
                evaluatedWindows = evaluatedWindows,
                insufficientAudio = false
            )
        }

        val mergedRanges = mergeRanges(acceptedRanges)
        val firstSample = mergedRanges.first().start
        val lastSample = mergedRanges.last().endExclusive
        val confidence = if (bestSimilarity < enterThreshold) {
            0f
        } else {
            ((bestSimilarity - enterThreshold) / (1f - enterThreshold).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
        }
        val residualGain = (0.16f - confidence * 0.12f).coerceIn(0.04f, 0.16f)
        val mask = FloatArray(audio.size)
        for (index in firstSample until lastSample) mask[index] = residualGain
        val fadeSamples = max(1, normalizedRate * fadeMs / 1000)
        mergedRanges.forEach { range ->
            for (index in range.start until range.endExclusive) {
                val fadeIn = ((index - range.start + 1).toFloat() / fadeSamples).coerceAtMost(1f)
                val fadeOut = ((range.endExclusive - index).toFloat() / fadeSamples).coerceAtMost(1f)
                mask[index] = max(mask[index], minOf(fadeIn, fadeOut))
            }
        }
        val filtered = FloatArray(lastSample - firstSample) { offset ->
            val sourceIndex = firstSample + offset
            audio[sourceIndex] * mask[sourceIndex]
        }
        return ExperimentalTargetSpeakerSelection(
            audio = filtered,
            bestSimilarity = bestSimilarity,
            evaluatedWindows = evaluatedWindows,
            insufficientAudio = false
        )
    }

    private fun buildWindowStarts(totalSamples: Int, windowSamples: Int, hopSamples: Int): List<Int> {
        if (totalSamples <= windowSamples) return listOf(0)
        val lastStart = totalSamples - windowSamples
        val starts = mutableListOf<Int>()
        var start = 0
        while (start < lastStart) {
            starts += start
            start += hopSamples
        }
        if (starts.lastOrNull() != lastStart) starts += lastStart
        return starts
    }

    private fun mergeRanges(ranges: List<WeightedSampleRange>): List<WeightedSampleRange> {
        val merged = mutableListOf<WeightedSampleRange>()
        ranges.sortedBy { it.start }.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous == null || range.start > previous.endExclusive) {
                merged += range
            } else {
                merged[merged.lastIndex] = previous.copy(
                    endExclusive = max(previous.endExclusive, range.endExclusive),
                    similarity = max(previous.similarity, range.similarity)
                )
            }
        }
        return merged
    }

    private data class WeightedSampleRange(
        val start: Int,
        val endExclusive: Int,
        val similarity: Float
    )
}
