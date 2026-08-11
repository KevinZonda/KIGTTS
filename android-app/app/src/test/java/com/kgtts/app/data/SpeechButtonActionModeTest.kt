package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechButtonActionModeTest {
    @Test
    fun legacyFlagsMapToOneMode() {
        assertEquals(SpeechButtonActionMode.TOGGLE, SpeechButtonActionMode.fromLegacy(false, false))
        assertEquals(SpeechButtonActionMode.TOGGLE, SpeechButtonActionMode.fromLegacy(false, true))
        assertEquals(SpeechButtonActionMode.HOLD, SpeechButtonActionMode.fromLegacy(true, false))
        assertEquals(SpeechButtonActionMode.HOLD_CONFIRM, SpeechButtonActionMode.fromLegacy(true, true))
    }

    @Test
    fun modesDeriveCompatibleFlags() {
        assertFalse(SpeechButtonActionMode.usesPushToTalk(SpeechButtonActionMode.TOGGLE))
        assertTrue(SpeechButtonActionMode.usesPushToTalk(SpeechButtonActionMode.HOLD))
        assertFalse(SpeechButtonActionMode.usesConfirmation(SpeechButtonActionMode.HOLD))
        assertTrue(SpeechButtonActionMode.usesConfirmation(SpeechButtonActionMode.HOLD_CONFIRM))
    }
}
