package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundboardTriggerPolicyTest {
    @Test
    fun recognizedSpeechSuppressesTtsWhenMatchedAndEnabled() {
        assertTrue(
            shouldSuppressTtsForSoundboardTrigger(
                fromQuickText = false,
                keywordTriggerEnabled = true,
                allowQuickTextTrigger = false,
                suppressTtsOnKeyword = true,
                hasTriggerMatch = true
            )
        )
    }

    @Test
    fun quickTextSuppressesTtsWhenQuickTextTriggerIsAllowed() {
        assertTrue(
            shouldSuppressTtsForSoundboardTrigger(
                fromQuickText = true,
                keywordTriggerEnabled = true,
                allowQuickTextTrigger = true,
                suppressTtsOnKeyword = true,
                hasTriggerMatch = true
            )
        )
    }

    @Test
    fun quickTextDoesNotSuppressWhenQuickTextTriggerIsDisabled() {
        assertFalse(
            shouldSuppressTtsForSoundboardTrigger(
                fromQuickText = true,
                keywordTriggerEnabled = true,
                allowQuickTextTrigger = false,
                suppressTtsOnKeyword = true,
                hasTriggerMatch = true
            )
        )
    }

    @Test
    fun unmatchedOrDisabledTriggerDoesNotSuppressTts() {
        assertFalse(
            shouldSuppressTtsForSoundboardTrigger(
                fromQuickText = false,
                keywordTriggerEnabled = true,
                allowQuickTextTrigger = true,
                suppressTtsOnKeyword = true,
                hasTriggerMatch = false
            )
        )
        assertFalse(
            shouldSuppressTtsForSoundboardTrigger(
                fromQuickText = false,
                keywordTriggerEnabled = false,
                allowQuickTextTrigger = true,
                suppressTtsOnKeyword = true,
                hasTriggerMatch = true
            )
        )
    }
}
