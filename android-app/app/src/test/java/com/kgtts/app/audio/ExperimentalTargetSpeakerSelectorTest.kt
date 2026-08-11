package com.lhtstudio.kigtts.app.audio

import com.lhtstudio.kigtts.app.data.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalTargetSpeakerSelectorTest {
    @Test
    fun experimentalModeKeepsOnlyTargetRegion() {
        val frontend = VoiceFilterInspiredTargetSpeakerFrontend()
        val audio = FloatArray(48_000) { index -> if (index < 24_000) 0.2f else 0.8f }
        val scores = ArrayDeque(listOf(0.2f, 0.2f, 0.3f, 0.7f, 0.72f, 0.75f))

        val selection = frontend.process(audio, sampleRate = 16_000, threshold = 0.5f) {
            scores.removeFirst()
        }

        assertTrue(selection.targetDetected)
        assertEquals(0.75f, selection.bestSimilarity, 0.001f)
        assertEquals(6, selection.evaluatedWindows)
        assertNotNull(selection.audio)
        assertTrue(selection.audio!!.size < audio.size)
    }

    @Test
    fun nonTargetAudioIsRejected() {
        val frontend = VoiceFilterInspiredTargetSpeakerFrontend()
        val selection = frontend.process(
            audio = FloatArray(32_000) { 0.2f },
            sampleRate = 16_000,
            threshold = 0.5f
        ) { 0.28f }

        assertFalse(selection.targetDetected)
        assertNull(selection.audio)
        assertEquals(0.28f, selection.bestSimilarity, 0.001f)
    }

    @Test
    fun isolatedSpeakerScoreSpikeIsRejectedAcrossMultipleWindows() {
        val frontend = VoiceFilterInspiredTargetSpeakerFrontend()
        val scores = ArrayDeque(listOf(0.22f, 0.67f, 0.24f, 0.21f, 0.18f, 0.20f))

        val selection = frontend.process(
            audio = FloatArray(48_000) { 0.2f },
            sampleRate = 16_000,
            threshold = 0.5f
        ) { scores.removeFirst() }

        assertFalse(selection.targetDetected)
        assertFalse(frontend.isTargetActive())
    }

    @Test
    fun activeSpeakerUsesLowerReleaseThreshold() {
        val frontend = VoiceFilterInspiredTargetSpeakerFrontend()
        val audio = FloatArray(19_200) { 0.5f }

        assertTrue(frontend.process(audio, 16_000, 0.5f) { 0.55f }.targetDetected)
        assertTrue(frontend.process(audio, 16_000, 0.5f) { 0.45f }.targetDetected)
        assertFalse(frontend.process(audio, 16_000, 0.5f) { 0.30f }.targetDetected)
    }

    @Test
    fun shortAudioWaitsForMoreSamples() {
        val frontend = VoiceFilterInspiredTargetSpeakerFrontend()
        val selection = frontend.process(
            audio = FloatArray(8_000),
            sampleRate = 16_000,
            threshold = 0.5f
        ) { error("Short audio must not be scored") }

        assertTrue(selection.insufficientAudio)
        assertEquals(0, selection.evaluatedWindows)
    }

    @Test
    fun unknownRecognitionModeFallsBackToLegacy() {
        assertEquals(
            UserPrefs.RECOGNITION_MODULE_MODE_LEGACY,
            UserPrefs.normalizeRecognitionModuleMode(99)
        )
        assertEquals(
            UserPrefs.RECOGNITION_MODULE_MODE_EXPERIMENTAL,
            UserPrefs.normalizeRecognitionModuleMode(
                UserPrefs.RECOGNITION_MODULE_MODE_EXPERIMENTAL
            )
        )
    }

    @Test
    fun sensitivityLowersOnlyExperimentalEnergyThresholds() {
        val low = ExperimentalRecognitionSensitivityPolicy.minSegmentRms(0.02, 0)
        val standard = ExperimentalRecognitionSensitivityPolicy.minSegmentRms(0.02, 50)
        val high = ExperimentalRecognitionSensitivityPolicy.minSegmentRms(0.02, 100)

        assertTrue(low > standard)
        assertTrue(standard > high)
        assertTrue(
            ExperimentalRecognitionSensitivityPolicy.classicVadThreshold(0.03, 100) < 0.03
        )
        assertTrue(
            ExperimentalRecognitionSensitivityPolicy.endpointSilenceThreshold(0.015, 100) >= 0.012
        )
    }

    @Test
    fun recognitionWindowFlushKeepsSpokenTailAndRejectsNoise() {
        assertTrue(
            RecognitionWindowPolicy.shouldSubmit(
                durationMs = 900,
                voicedMs = 420,
                rms = 0.018,
                minRms = 0.008
            )
        )
        assertFalse(
            RecognitionWindowPolicy.shouldSubmit(
                durationMs = 900,
                voicedMs = 40,
                rms = 0.018,
                minRms = 0.008
            )
        )
    }

    @Test
    fun sensitivityAppliesLimitedGainToQuietSpeech() {
        val quiet = FloatArray(16_000) { 0.004f }

        assertSame(quiet, ExperimentalRecognitionSensitivityPolicy.applyInputGain(quiet, 0))
        val boosted = ExperimentalRecognitionSensitivityPolicy.applyInputGain(quiet, 100)
        assertTrue(boosted.maxOf { kotlin.math.abs(it) } > 0.004f)
        assertTrue(boosted.maxOf { kotlin.math.abs(it) } <= 0.98f)
    }

    @Test
    fun neuralBackendRequiresBothResourcesAndNeuralProfile() {
        assertFalse(
            ExperimentalTargetSpeakerBackend.shouldUseNeural(
                ExperimentalTargetSpeakerBackend.AUTO,
                resourcesInstalled = false,
                neuralProfileReady = true
            )
        )
        assertFalse(
            ExperimentalTargetSpeakerBackend.shouldUseNeural(
                ExperimentalTargetSpeakerBackend.NEURAL,
                resourcesInstalled = true,
                neuralProfileReady = false
            )
        )
        assertTrue(
            ExperimentalTargetSpeakerBackend.shouldUseNeural(
                ExperimentalTargetSpeakerBackend.AUTO,
                resourcesInstalled = true,
                neuralProfileReady = true
            )
        )
        assertFalse(
            ExperimentalTargetSpeakerBackend.shouldUseNeural(
                ExperimentalTargetSpeakerBackend.LIGHTWEIGHT,
                resourcesInstalled = true,
                neuralProfileReady = true
            )
        )
    }

    @Test
    fun speakerProfilePayloadKeepsOptionalConfirmationAndNeuralEmbeddings() {
        val camp = floatArrayOf(0.1f, 0.2f, 0.3f)
        val confirmation = floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f)
        val neural = FloatArray(192) { index -> index / 192f }
        val payload = UserPrefs.serializeSpeakerVerifyProfiles(
            listOf(
                UserPrefs.SpeakerVerifyProfile(
                    id = "sample-1",
                    name = "样本 1",
                    vector = camp,
                    confirmationVector = confirmation,
                    neuralVector = neural
                )
            )
        )

        val restored = UserPrefs.parseSpeakerVerifyProfiles(payload).single()

        assertArrayEquals(camp, restored.vector, 0f)
        assertArrayEquals(confirmation, requireNotNull(restored.confirmationVector), 0f)
        assertArrayEquals(neural, requireNotNull(restored.neuralVector), 0f)
    }

    @Test
    fun neuralQualityGuardRejectsIdentityLossAndCollapsedAudio() {
        assertTrue(
            NeuralSeparationQualityPolicy.accepts(
                candidateSimilarity = 0.58f,
                baselineSimilarity = 0.62f,
                verificationThreshold = 0.5f,
                energyRatio = 0.7
            )
        )
        assertFalse(
            NeuralSeparationQualityPolicy.accepts(
                candidateSimilarity = 0.4f,
                baselineSimilarity = 0.62f,
                verificationThreshold = 0.5f,
                energyRatio = 0.7
            )
        )
        assertFalse(
            NeuralSeparationQualityPolicy.accepts(
                candidateSimilarity = 0.7f,
                baselineSimilarity = 0.62f,
                verificationThreshold = 0.5f,
                energyRatio = 0.01
            )
        )
    }

    @Test
    fun automaticNeuralSeparationRunsOnlyForAmbiguousSpeakerScores() {
        assertTrue(
            NeuralSeparationActivationPolicy.shouldRun(
                ExperimentalTargetSpeakerBackend.AUTO,
                baselineSimilarity = 0.54f,
                verificationThreshold = 0.5f
            )
        )
        assertFalse(
            NeuralSeparationActivationPolicy.shouldRun(
                ExperimentalTargetSpeakerBackend.AUTO,
                baselineSimilarity = 0.64f,
                verificationThreshold = 0.5f
            )
        )
        assertTrue(
            NeuralSeparationActivationPolicy.shouldRun(
                ExperimentalTargetSpeakerBackend.NEURAL,
                baselineSimilarity = 0.9f,
                verificationThreshold = 0.5f
            )
        )
    }

    @Test
    fun autoNeuralBackendRejectsNonRealtimeSeparator() {
        assertFalse(NeuralSeparationPerformancePolicy.shouldDisableAuto(0.84f))
        assertTrue(NeuralSeparationPerformancePolicy.shouldDisableAuto(0.86f))
        assertTrue(
            NeuralSeparationPerformancePolicy.shouldDisableAuto(Float.POSITIVE_INFINITY)
        )
        assertTrue(NeuralSeparationPerformancePolicy.shouldDisableAuto(Float.NaN))
    }
}
