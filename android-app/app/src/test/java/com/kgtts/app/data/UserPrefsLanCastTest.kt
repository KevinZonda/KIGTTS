package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPrefsLanCastTest {
    @Test
    fun `cast background reminder is enabled by default`() {
        assertFalse(UserPrefs.AppSettings().lanCastBackgroundReminderDismissed)
    }

    @Test
    fun `cast display settings default to normal adaptive text`() {
        val settings = UserPrefs.AppSettings().lanCastDisplaySettings

        assertFalse(settings.dotMatrixEnabled)
        assertTrue(settings.adaptiveMultiLine)
    }

    @Test
    fun `missing cast display settings use cast defaults`() {
        val settings = decodeLedSubtitleSettings(
            raw = null,
            defaults = defaultLanCastDisplaySettings()
        )

        assertFalse(settings.dotMatrixEnabled)
        assertTrue(settings.adaptiveMultiLine)
    }
}
