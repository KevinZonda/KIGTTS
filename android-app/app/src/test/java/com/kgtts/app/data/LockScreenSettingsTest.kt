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
        assertFalse(settings.showBatteryStatus)
        assertEquals(LockScreenBatteryStyle.Compact, settings.batteryStyle)
        assertFalse(settings.batteryOnlyWhenChargingOrLow)
        assertEquals(30, settings.lowBatteryThreshold)
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
            scrimOpacity = -2f,
            lowBatteryThreshold = 999
        ).normalized()

        assertEquals(30f, decoded.wallpaperBlurRadius)
        assertEquals(0f, decoded.scrimOpacity)
        assertEquals(100, decoded.lowBatteryThreshold)
    }

    @Test
    fun `battery status respects charging and strict low threshold policy`() {
        val settings = LockScreenSettings(
            showBatteryStatus = true,
            batteryOnlyWhenChargingOrLow = true,
            lowBatteryThreshold = 30
        )

        assertTrue(
            settings.shouldShowBatteryStatus(
                LockScreenBatteryStatus(percentage = 29, isCharging = false, isFull = false)
            )
        )
        assertFalse(
            settings.shouldShowBatteryStatus(
                LockScreenBatteryStatus(percentage = 30, isCharging = false, isFull = false)
            )
        )
        assertTrue(
            settings.shouldShowBatteryStatus(
                LockScreenBatteryStatus(percentage = 80, isCharging = true, isFull = false)
            )
        )
    }

    @Test
    fun `battery styles format concise and detailed labels`() {
        val charging = LockScreenBatteryStatus(
            percentage = 76,
            isCharging = true,
            isFull = false
        )
        val idle = charging.copy(isCharging = false)

        assertEquals(
            "76% · 正在充电",
            LockScreenSettings(
                showBatteryStatus = true,
                batteryStyle = LockScreenBatteryStyle.Compact
            ).formatBatteryStatus(charging)
        )
        assertEquals(
            "电量 76% · 未充电",
            LockScreenSettings(
                showBatteryStatus = true,
                batteryStyle = LockScreenBatteryStyle.Detailed
            ).formatBatteryStatus(idle)
        )
    }

}
