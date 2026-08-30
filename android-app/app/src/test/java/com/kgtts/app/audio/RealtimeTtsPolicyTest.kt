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

    @Test
    fun ttsDisabledAlwaysSuppressesAsrAutoSpeak() {
        assertTrue(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = true,
                pushToTalkMode = false,
                pushToTalkConfirmInput = false
            )
        )
    }

    @Test
    fun confirmedPushToTalkSuppressesAsrAutoSpeak() {
        assertTrue(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = false,
                pushToTalkMode = true,
                pushToTalkConfirmInput = true
            )
        )
    }

    @Test
    fun normalRecognitionKeepsAsrAutoSpeakEnabled() {
        assertFalse(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = false,
                pushToTalkMode = false,
                pushToTalkConfirmInput = false
            )
        )
    }

    @Test
    fun pushToTalkWithoutConfirmationKeepsAsrAutoSpeakEnabled() {
        assertFalse(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = false,
                pushToTalkMode = true,
                pushToTalkConfirmInput = false
            )
        )
    }

    @Test
    fun unavailableTtsKeepsAsrAutoSpeakSuppressed() {
        assertTrue(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = false,
                pushToTalkMode = false,
                pushToTalkConfirmInput = false,
                ttsReady = false
            )
        )
    }

    @Test
    fun readyTtsAllowsNormalAsrAutoSpeak() {
        assertFalse(
            RealtimeTtsPolicy.shouldSuppressAsrAutoSpeak(
                ttsDisabled = false,
                pushToTalkMode = false,
                pushToTalkConfirmInput = false,
                ttsReady = true
            )
        )
    }
}
