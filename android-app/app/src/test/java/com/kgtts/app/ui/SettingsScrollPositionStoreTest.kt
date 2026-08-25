package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsScrollPositionStoreTest {
    @Test
    fun `positions are stored independently for every settings page`() {
        val store = SettingsScrollPositionStore()

        store.update("category:Audio", 320)
        store.update("detail:microphone", 740)

        assertEquals(320, store.positionFor("category:Audio"))
        assertEquals(740, store.positionFor("detail:microphone"))
        assertEquals(0, store.positionFor("category:System"))
    }

    @Test
    fun `main categories and detail pages use stable distinct keys`() {
        val audioKey = settingsScrollPositionKey(null, SettingsCategory.Audio)
        val systemKey = settingsScrollPositionKey(null, SettingsCategory.System)
        val microphoneKey = settingsScrollPositionKey(
            SettingsDetailPage.Microphone,
            SettingsCategory.Recognition
        )

        assertEquals("category:Audio", audioKey)
        assertEquals("detail:microphone", microphoneKey)
        assertNotEquals(audioKey, systemKey)
        assertNotEquals(systemKey, microphoneKey)
    }
}
