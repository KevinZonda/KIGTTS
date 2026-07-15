package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedSubtitleSettingsTest {
    @Test
    fun normalizedClampsNumericAndEnumValues() {
        val value = LedSubtitleSettings(
            ledColorArgb = 0x00112233,
            backgroundColorArgb = 0x00445566,
            dotShape = 9,
            dotDensity = 3f,
            glowStrength = -1f,
            displayHeightFraction = 2f,
            scrollSpeedDpPerSecond = 1f,
            scrollDirection = 8,
            loopGapDp = 1f,
            shortTextAlignment = 7,
            screenBrightness = 0f
        ).normalized()

        assertEquals(0xFF112233.toInt(), value.ledColorArgb)
        assertEquals(0xFF445566.toInt(), value.backgroundColorArgb)
        assertEquals(LedSubtitleSettings.DOT_SHAPE_SQUARE, value.dotShape)
        assertEquals(1f, value.dotDensity)
        assertEquals(0f, value.glowStrength)
        assertEquals(0.92f, value.displayHeightFraction)
        assertEquals(24f, value.scrollSpeedDpPerSecond)
        assertEquals(LedSubtitleSettings.SCROLL_LEFT_TO_RIGHT, value.scrollDirection)
        assertEquals(24f, value.loopGapDp)
        assertEquals(LedSubtitleSettings.ALIGN_END, value.shortTextAlignment)
        assertEquals(0.1f, value.screenBrightness)
    }

    @Test
    fun defaultsAreReadyForImmediateRendering() {
        val value = LedSubtitleSettings().normalized()

        assertEquals(LedSubtitleSettings(), value)
        assertEquals(0xFFFFFFFF.toInt(), value.ledColorArgb)
        assertTrue(value.dotMatrixEnabled)
        assertTrue(value.quickSwipeOpensQuickText)
        assertTrue(value.keepScreenOn)
        assertFalse(value.followSystemBrightness)
        assertEquals(1f, value.screenBrightness)
        assertTrue(value.glowEnabled)
        assertFalse(value.ledColorArgb == value.backgroundColorArgb)
    }

    @Test
    fun normalizedPreservesNormalFontMode() {
        val original = LedSubtitleSettings(
            dotMatrixEnabled = false,
            ledColorArgb = 0xFFEC407A.toInt()
        )

        val normalized = original.normalized()

        assertEquals(original, normalized)
        assertFalse(normalized.dotMatrixEnabled)
    }
}
