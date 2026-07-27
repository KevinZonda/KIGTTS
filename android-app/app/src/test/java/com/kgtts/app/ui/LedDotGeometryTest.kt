package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class LedDotGeometryTest {
    @Test
    fun `rows per line determine cell pitch`() {
        assertEquals(5, ledCellPitchPx(lineHeightPx = 120f, rowsPerLine = 24))
        assertEquals(2, ledCellPitchPx(lineHeightPx = 20f, rowsPerLine = 48))
    }

    @Test
    fun `dot size uses shape specific maximum diameter`() {
        val square = LedSubtitleSettings(
            dotShape = LedSubtitleSettings.DOT_SHAPE_SQUARE,
            dotSizeFraction = 1f
        )
        val circle = square.copy(dotShape = LedSubtitleSettings.DOT_SHAPE_CIRCLE)

        assertEquals(5f, ledDotHalfExtentPx(10, square), 0.001f)
        assertEquals(10f * sqrt(2f) / 2f, ledDotHalfExtentPx(10, circle), 0.001f)
    }
}
