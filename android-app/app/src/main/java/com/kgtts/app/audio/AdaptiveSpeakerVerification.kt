package com.lhtstudio.kigtts.app.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

internal enum class PrimarySpeakerDecision {
    ACCEPT,
    CONFIRM,
    REJECT
}

internal object AdaptiveSpeakerVerificationPolicy {
    private const val PRIMARY_ACCEPT_MARGIN = 0.12f
    private const val PRIMARY_REJECT_MARGIN = 0.12f
    private const val PROFILE_MARGIN = 0.10f
    private const val SESSION_REUSE_MS = 3000L
    private const val SESSION_SHORT_UTTERANCE_MS = 1100

    fun calibratedThreshold(
        profiles: List<FloatArray>,
        defaultThreshold: Float,
        minimum: Float,
        maximum: Float,
        maxRelaxation: Float = 1f
    ): Float {
        val adaptiveMinimum = max(
            minimum,
            defaultThreshold - maxRelaxation.coerceAtLeast(0f)
        )
        if (profiles.size < 2) return defaultThreshold.coerceIn(adaptiveMinimum, maximum)
        var lowestPair = 1f
        var pairs = 0
        for (first in 0 until profiles.lastIndex) {
            for (second in first + 1 until profiles.size) {
                if (profiles[first].isEmpty() || profiles[second].isEmpty()) continue
                lowestPair = minOf(
                    lowestPair,
                    SpeakerVerifier.cosineSimilarity(profiles[first], profiles[second])
                )
                pairs++
            }
        }
        if (pairs == 0) return defaultThreshold.coerceIn(adaptiveMinimum, maximum)
        // Positive enrollment samples can show how far the same speaker may drift,
        // but they cannot justify raising the rejection threshold without impostor data.
        // Raising it here made verification overfit the prompted enrollment phrases.
        val enrollmentUpperBound = lowestPair - PROFILE_MARGIN
        return minOf(defaultThreshold, enrollmentUpperBound).coerceIn(adaptiveMinimum, maximum)
    }

    fun primaryDecision(
        similarity: Float,
        threshold: Float,
        confirmationAvailable: Boolean
    ): PrimarySpeakerDecision {
        val normalizedThreshold = threshold.coerceIn(0.05f, 0.95f)
        if (similarity >= (normalizedThreshold + PRIMARY_ACCEPT_MARGIN).coerceAtMost(0.92f)) {
            return PrimarySpeakerDecision.ACCEPT
        }
        if (similarity < (normalizedThreshold - PRIMARY_REJECT_MARGIN).coerceAtLeast(0.10f)) {
            return PrimarySpeakerDecision.REJECT
        }
        if (!confirmationAvailable) {
            return if (similarity >= normalizedThreshold) {
                PrimarySpeakerDecision.ACCEPT
            } else {
                PrimarySpeakerDecision.REJECT
            }
        }
        return PrimarySpeakerDecision.CONFIRM
    }

    fun candidateThreshold(threshold: Float): Float {
        return (threshold - PRIMARY_REJECT_MARGIN).coerceIn(0.10f, 0.90f)
    }

    fun confirmationAccepted(
        primarySimilarity: Float,
        primaryThreshold: Float,
        confirmationSimilarity: Float,
        confirmationThreshold: Float
    ): Boolean {
        return primarySimilarity >= (primaryThreshold - PRIMARY_REJECT_MARGIN).coerceAtLeast(0.10f) &&
            confirmationSimilarity >= confirmationThreshold
    }

    fun canReuseVerifiedSession(
        sessionPassed: Boolean,
        lastVerifiedAtMs: Long,
        nowMs: Long,
        utteranceDurationMs: Int
    ): Boolean {
        if (!sessionPassed || lastVerifiedAtMs <= 0L) return false
        val age = nowMs - lastVerifiedAtMs
        return age in 0..SESSION_REUSE_MS && utteranceDurationMs <= SESSION_SHORT_UTTERANCE_MS
    }
}

internal data class SpeakerEnrollmentAssessment(
    val audio: FloatArray?,
    val message: String?,
    val activeRatio: Float,
    val estimatedSnrDb: Float,
    val clippingRatio: Float
) {
    val accepted: Boolean
        get() = audio?.isNotEmpty() == true && message == null
}

internal object SpeakerEnrollmentQualityPolicy {
    private const val FRAME_MS = 20
    private const val MIN_ACTIVE_MS = 1200
    private const val MIN_ACTIVE_RATIO = 0.22f
    private const val MAX_CLIPPING_RATIO = 0.015f
    private const val MIN_RMS = 0.006

    fun assess(audio: FloatArray, sampleRate: Int): SpeakerEnrollmentAssessment {
        if (audio.isEmpty() || sampleRate <= 0) return rejected("录音时长不足")
        val frameSamples = max(1, sampleRate * FRAME_MS / 1000)
        val frameRms = mutableListOf<Double>()
        var clippingSamples = 0
        var offset = 0
        while (offset < audio.size) {
            val end = minOf(audio.size, offset + frameSamples)
            var sum = 0.0
            for (index in offset until end) {
                val value = audio[index].toDouble()
                sum += value * value
                if (kotlin.math.abs(value) >= 0.98) clippingSamples++
            }
            frameRms += sqrt(sum / (end - offset).coerceAtLeast(1))
            offset = end
        }
        val sortedRms = frameRms.sorted()
        val noiseFloor = sortedRms[(sortedRms.lastIndex * 0.2).toInt().coerceAtLeast(0)]
        val activeThreshold = max(MIN_RMS, noiseFloor * 2.2)
        val activeFrames = frameRms.indices.filter { frameRms[it] >= activeThreshold }
        val activeRatio = activeFrames.size.toFloat() / frameRms.size.coerceAtLeast(1)
        val clippingRatio = clippingSamples.toFloat() / audio.size.coerceAtLeast(1)
        if (clippingRatio > MAX_CLIPPING_RATIO) {
            return rejected("录音声音过大，请稍微远离麦克风", activeRatio, 0f, clippingRatio)
        }
        if (activeFrames.size * FRAME_MS < MIN_ACTIVE_MS || activeRatio < MIN_ACTIVE_RATIO) {
            return rejected("有效语音不足，请自然、连续地朗读完整句子", activeRatio, 0f, clippingRatio)
        }
        val activeEnergy = activeFrames.map { frameRms[it] * frameRms[it] }.average()
        val inactiveFrames = frameRms.indices.filterNot(activeFrames::contains)
        val noiseEnergy = inactiveFrames
            .map { frameRms[it] * frameRms[it] }
            .average()
            .takeIf { it.isFinite() && it > 1e-12 }
            ?: (noiseFloor * noiseFloor).coerceAtLeast(1e-12)
        val snrDb = (10.0 * log10(activeEnergy.coerceAtLeast(1e-12) / noiseEnergy)).toFloat()
        val firstFrame = (activeFrames.first() - 1).coerceAtLeast(0)
        val lastFrame = (activeFrames.last() + 1).coerceAtMost(frameRms.lastIndex)
        val start = firstFrame * frameSamples
        val end = minOf(audio.size, (lastFrame + 1) * frameSamples)
        return SpeakerEnrollmentAssessment(
            audio = audio.copyOfRange(start, end),
            message = null,
            activeRatio = activeRatio,
            estimatedSnrDb = snrDb,
            clippingRatio = clippingRatio
        )
    }

    private fun rejected(
        message: String,
        activeRatio: Float = 0f,
        snrDb: Float = 0f,
        clippingRatio: Float = 0f
    ) = SpeakerEnrollmentAssessment(
        audio = null,
        message = message,
        activeRatio = activeRatio,
        estimatedSnrDb = snrDb,
        clippingRatio = clippingRatio
    )
}
