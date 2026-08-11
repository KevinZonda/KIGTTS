package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningEndpointPolicyTest {
    @Test
    fun finalizesListeningAfterTrailingSilence() {
        assertTrue(
            ListeningEndpointPolicy.shouldFinalizeAfterSilence(
                listeningEnabled = true,
                speechSeen = true,
                trailingSilenceMs = 450,
                requiredSilenceMs = 450
            )
        )
    }

    @Test
    fun doesNotFinalizeBeforeSpeechOrSilenceThreshold() {
        assertFalse(
            ListeningEndpointPolicy.shouldFinalizeAfterSilence(
                listeningEnabled = true,
                speechSeen = false,
                trailingSilenceMs = 900,
                requiredSilenceMs = 450
            )
        )
        assertFalse(
            ListeningEndpointPolicy.shouldFinalizeAfterSilence(
                listeningEnabled = true,
                speechSeen = true,
                trailingSilenceMs = 449,
                requiredSilenceMs = 450
            )
        )
    }

    @Test
    fun forcesBoundaryAtContinuousSpeechLimit() {
        assertTrue(
            ListeningEndpointPolicy.shouldForceBoundary(
                listeningEnabled = true,
                speechDetected = true,
                windowSamples = 85_120,
                sampleRate = 16_000,
                maxSpeechDurationMs = 5_000,
                preRollSamples = 5_120
            )
        )
    }

    @Test
    fun waitsUntilSpeechAndPreRollLimitIsReached() {
        assertFalse(
            ListeningEndpointPolicy.shouldForceBoundary(
                listeningEnabled = true,
                speechDetected = true,
                windowSamples = 85_119,
                sampleRate = 16_000,
                maxSpeechDurationMs = 5_000,
                preRollSamples = 5_120
            )
        )
    }

    @Test
    fun doesNotForceNormalRecognitionOrSilence() {
        assertFalse(
            ListeningEndpointPolicy.shouldForceBoundary(
                listeningEnabled = false,
                speechDetected = true,
                windowSamples = 96_000,
                sampleRate = 16_000,
                maxSpeechDurationMs = 5_000,
                preRollSamples = 0
            )
        )
        assertFalse(
            ListeningEndpointPolicy.shouldForceBoundary(
                listeningEnabled = true,
                speechDetected = false,
                windowSamples = 96_000,
                sampleRate = 16_000,
                maxSpeechDurationMs = 5_000,
                preRollSamples = 0
            )
        )
    }
}
