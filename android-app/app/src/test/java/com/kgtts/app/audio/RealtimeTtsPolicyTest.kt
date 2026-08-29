package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeTtsPolicyTest {
    @Test
    fun normalRecognitionRequiresLoadedTts() {
        assertTrue(
            RealtimeTtsPolicy.requiresLoadedTts(
                ttsDisabled = false,
                listeningModeEnabled = false
            )
        )
    }

    @Test
    fun ttsDisabledAllowsRecognitionWithoutLoadedTts() {
        assertFalse(
            RealtimeTtsPolicy.requiresLoadedTts(
                ttsDisabled = true,
                listeningModeEnabled = false
            )
        )
    }

    @Test
    fun listeningModeAllowsRecognitionWithoutLoadedTts() {
        assertFalse(
            RealtimeTtsPolicy.requiresLoadedTts(
                ttsDisabled = false,
                listeningModeEnabled = true
            )
        )
    }
}
