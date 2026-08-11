package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AdaptiveSpeakerVerificationTest {
    @Test
    fun primaryDecisionUsesFastAcceptAndRejectMargins() {
        assertEquals(
            PrimarySpeakerDecision.ACCEPT,
            AdaptiveSpeakerVerificationPolicy.primaryDecision(0.64f, 0.5f, true)
        )
        assertEquals(
            PrimarySpeakerDecision.CONFIRM,
            AdaptiveSpeakerVerificationPolicy.primaryDecision(0.53f, 0.5f, true)
        )
        assertEquals(
            PrimarySpeakerDecision.REJECT,
            AdaptiveSpeakerVerificationPolicy.primaryDecision(0.36f, 0.5f, true)
        )
    }

    @Test
    fun missingConfirmationModelFallsBackToPrimaryThreshold() {
        assertEquals(
            PrimarySpeakerDecision.ACCEPT,
            AdaptiveSpeakerVerificationPolicy.primaryDecision(0.51f, 0.5f, false)
        )
        assertEquals(
            PrimarySpeakerDecision.REJECT,
            AdaptiveSpeakerVerificationPolicy.primaryDecision(0.49f, 0.5f, false)
        )
    }

    @Test
    fun enrollmentProfilesNeverRaiseThresholdWithoutImpostorSamples() {
        val profiles = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0.9f, 0.1f, 0f),
            floatArrayOf(0.86f, 0.14f, 0f)
        )

        val threshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = profiles,
            defaultThreshold = 0.5f,
            minimum = 0.38f,
            maximum = 0.68f
        )

        assertEquals(0.5f, threshold, 0.0001f)
    }

    @Test
    fun variedEnrollmentProfilesCanLowerThresholdForNaturalVoiceDrift() {
        val profiles = listOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0.5f, 0.8660254f)
        )

        val threshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = profiles,
            defaultThreshold = 0.5f,
            minimum = 0.38f,
            maximum = 0.68f
        )

        assertEquals(0.4f, threshold, 0.0001f)
    }

    @Test
    fun userToleranceKeepsAdaptiveRelaxationNearSelectedLevel() {
        val profiles = listOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0.5f, 0.8660254f)
        )

        val strict = SpeakerVerificationTolerance.STRICT
        val threshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = profiles,
            defaultThreshold = strict.primaryThreshold,
            minimum = 0.38f,
            maximum = 0.68f,
            maxRelaxation = strict.adaptiveRelaxation
        )

        assertEquals(0.59f, threshold, 0.0001f)
    }

    @Test
    fun smartToleranceCanAdaptAcrossTheFullSupportedRange() {
        val profiles = listOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0.5f, 0.8660254f)
        )

        val smart = SpeakerVerificationTolerance.SMART
        val threshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = profiles,
            defaultThreshold = smart.primaryThreshold,
            minimum = 0.38f,
            maximum = 0.68f,
            maxRelaxation = smart.adaptiveRelaxation
        )

        assertEquals(0.4f, threshold, 0.0001f)
    }

    @Test
    fun storedThresholdMapsToNearestToleranceLevel() {
        assertEquals(
            SpeakerVerificationTolerance.LENIENT,
            SpeakerVerificationTolerance.fromThreshold(0.39f)
        )
        assertEquals(
            SpeakerVerificationTolerance.BALANCED,
            SpeakerVerificationTolerance.fromThreshold(0.52f)
        )
        assertEquals(
            SpeakerVerificationTolerance.STRICT,
            SpeakerVerificationTolerance.fromThreshold(0.64f)
        )
        assertEquals(
            SpeakerVerificationTolerance.SMART,
            SpeakerVerificationTolerance.fromIndex(0)
        )
    }

    @Test
    fun confirmationCannotRescueHardPrimaryMismatch() {
        assertTrue(
            AdaptiveSpeakerVerificationPolicy.confirmationAccepted(
                primarySimilarity = 0.44f,
                primaryThreshold = 0.5f,
                confirmationSimilarity = 0.67f,
                confirmationThreshold = 0.6f
            )
        )
        assertFalse(
            AdaptiveSpeakerVerificationPolicy.confirmationAccepted(
                primarySimilarity = 0.37f,
                primaryThreshold = 0.5f,
                confirmationSimilarity = 0.9f,
                confirmationThreshold = 0.6f
            )
        )
    }

    @Test
    fun verifiedSessionReuseIsShortAndTimeBound() {
        assertTrue(
            AdaptiveSpeakerVerificationPolicy.canReuseVerifiedSession(
                sessionPassed = true,
                lastVerifiedAtMs = 1_000L,
                nowMs = 3_800L,
                utteranceDurationMs = 900
            )
        )
        assertFalse(
            AdaptiveSpeakerVerificationPolicy.canReuseVerifiedSession(
                sessionPassed = true,
                lastVerifiedAtMs = 1_000L,
                nowMs = 4_100L,
                utteranceDurationMs = 900
            )
        )
        assertFalse(
            AdaptiveSpeakerVerificationPolicy.canReuseVerifiedSession(
                sessionPassed = true,
                lastVerifiedAtMs = 1_000L,
                nowMs = 2_000L,
                utteranceDurationMs = 1_200
            )
        )
    }

    @Test
    fun enrollmentQualityTrimsSilenceAroundNaturalSpeech() {
        val sampleRate = 16_000
        val audio = FloatArray(sampleRate * 3)
        for (index in sampleRate / 2 until sampleRate * 5 / 2) {
            audio[index] = (0.18 * sin(2.0 * PI * 220.0 * index / sampleRate)).toFloat()
        }

        val assessment = SpeakerEnrollmentQualityPolicy.assess(audio, sampleRate)

        assertTrue(assessment.accepted)
        assertNotNull(assessment.audio)
        assertTrue(requireNotNull(assessment.audio).size < audio.size)
        assertTrue(assessment.activeRatio > 0.5f)
    }

    @Test
    fun enrollmentQualityRejectsSilenceAndClipping() {
        val silence = SpeakerEnrollmentQualityPolicy.assess(FloatArray(48_000), 16_000)
        val clipping = SpeakerEnrollmentQualityPolicy.assess(FloatArray(48_000) { 1f }, 16_000)

        assertFalse(silence.accepted)
        assertNull(silence.audio)
        assertFalse(clipping.accepted)
        assertNull(clipping.audio)
    }
}
