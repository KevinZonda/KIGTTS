package com.lhtstudio.kigtts.app.data

import com.lhtstudio.kigtts.app.audio.AudioRoutePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningModeSettingsTest {
    @Test
    fun defaultsUseBuiltInMicrophoneAndHoldMode() {
        val settings = ListeningModeSettings.fromJson(null)

        assertFalse(settings.enabled)
        assertEquals(AudioRoutePreference.INPUT_BUILTIN_MIC, settings.preferredInputType)
        assertEquals(SpeechButtonActionMode.HOLD, settings.preferredSpeechButtonMode)
        assertEquals(ListeningModeSettings.MIN_FONT_SIZE_SP, settings.fontSizeSp)
    }

    @Test
    fun jsonRoundTripPreservesLayoutAndAudioSettings() {
        val source = ListeningModeSettings(
            enabled = true,
            modePromptDismissed = true,
            preferredSpeechButtonMode = SpeechButtonActionMode.HOLD_CONFIRM,
            fontSizeSp = 72f,
            rotated180 = true,
            portraitPanelsSwapped = true,
            landscapePanelsSwapped = true,
            hideDuringTextInput = true,
            recognitionLanguage = AsrRecognitionLanguage.JAPANESE,
            preferredInputType = AudioRoutePreference.INPUT_USB,
            minVolumePercent = 12
        )

        assertEquals(source, ListeningModeSettings.fromJson(source.toJson()))
        assertTrue(ListeningModeSettings.fromJson(source.toJson()).hideDuringTextInput)
    }

    @Test
    fun invalidValuesAreNormalized() {
        val parsed = ListeningModeSettings.fromJson(
            """{"preferredSpeechButtonMode":0,"fontSizeSp":999,"preferredInputType":99}"""
        )

        assertEquals(SpeechButtonActionMode.HOLD, parsed.preferredSpeechButtonMode)
        assertEquals(ListeningModeSettings.MAX_FONT_SIZE_SP, parsed.fontSizeSp)
        assertEquals(AudioRoutePreference.INPUT_WIRED, parsed.preferredInputType)
    }
}
