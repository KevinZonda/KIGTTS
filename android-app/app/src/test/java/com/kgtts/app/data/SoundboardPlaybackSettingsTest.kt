package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SoundboardPlaybackSettingsTest {
    @Test
    fun `new playback interrupts current soundboard audio by default`() {
        assertTrue(UserPrefs.AppSettings().soundboardInterruptOnNewPlayback)
    }

    @Test
    fun `quick text soundboard linkage defaults to enabled`() {
        val settings = UserPrefs.AppSettings()

        assertTrue(settings.allowQuickTextTriggerSoundboard)
        assertTrue(settings.soundboardSuppressTtsOnKeyword)
    }
}
