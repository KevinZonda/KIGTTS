package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenSettingsTest {
    @Test
    fun `defaults preserve the current lock screen appearance`() {
        val settings = LockScreenSettings()

        assertEquals("", settings.wallpaperPath)
        assertEquals(0f, settings.wallpaperBlurRadius)
        assertEquals(0xFF000000.toInt(), settings.scrimColorArgb)
        assertEquals(0.28f, settings.scrimOpacity)
        assertEquals(LockScreenScrimStyle.EdgeGradient, settings.scrimStyle)
        assertFalse(settings.timeAndDateAlignedStart)
        assertFalse(settings.useSystemFont)
        assertFalse(settings.useSeparateClockFont)
        assertFalse(settings.showLunarDate)
    }

    @Test
    fun `wallpaper and clock settings normalize before persistence`() {
        val normalized = LockScreenSettings(
            wallpaperPath = "/data/user/0/test/files/lock_screen/wallpaper",
            wallpaperBlurRadius = 99f,
            scrimColorArgb = 0x00F44336,
            scrimOpacity = -1f,
            scrimStyle = LockScreenScrimStyle.Full,
            timeAndDateAlignedStart = true,
            useSystemFont = true,
            useSeparateClockFont = true,
            clockFontId = "source-han-sans-cn",
            clockFontWeight = 650,
            showLunarDate = true
        ).normalized()

        assertEquals(30f, normalized.wallpaperBlurRadius)
        assertEquals(0xFFF44336.toInt(), normalized.scrimColorArgb)
        assertEquals(0f, normalized.scrimOpacity)
        assertEquals(LockScreenScrimStyle.Full, normalized.scrimStyle)
        assertEquals("source-han-sans-cn", normalized.clockFontId)
        assertEquals(650, normalized.clockFontWeight)
    }

    @Test
    fun `invalid payload falls back safely`() {
        val settings = decodeLockScreenSettings("not-json")

        assertEquals(LockScreenSettings(), settings)
    }

    @Test
    fun `out of range wallpaper effects are clamped`() {
        val decoded = LockScreenSettings(
            wallpaperBlurRadius = 99f,
            scrimOpacity = -2f
        ).normalized()

        assertEquals(30f, decoded.wallpaperBlurRadius)
        assertEquals(0f, decoded.scrimOpacity)
    }
}
