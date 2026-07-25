package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitleItemColorCodecTest {
    @Test
    fun alignedColorsPadMissingEntriesWithoutShiftingExistingColors() {
        val colors = listOf<Int?>(0xFFFF0000.toInt(), null, 0xFF00FF00.toInt())

        assertEquals(
            listOf(0xFFFF0000.toInt(), null, 0xFF00FF00.toInt(), null),
            colors.alignedQuickSubtitleItemColors(4)
        )
    }

    @Test
    fun alignedColorsDropEntriesBeyondTextCount() {
        val colors = listOf<Int?>(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())

        assertEquals(
            listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
            colors.alignedQuickSubtitleItemColors(2)
        )
    }

    @Test
    fun compactColorsRemoveOnlyTrailingEmptyValues() {
        val colors = listOf<Int?>(0xFFFF0000.toInt(), null, 0xFF0000FF.toInt(), null, null)

        assertEquals(
            listOf(0xFFFF0000.toInt(), null, 0xFF0000FF.toInt()),
            colors.compactQuickSubtitleItemColors()
        )
    }
}
