package com.lhtstudio.kigtts.app.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingCustomColorWheelTest {
    @Test
    fun wheelUsesSevenDistinctMd2Colors() {
        assertEquals(7, DrawingColorWheelColors.size)
        assertEquals(7, DrawingColorWheelColors.map { it.toArgb() }.distinct().size)
        assertEquals(0xFFF44336.toInt(), DrawingColorWheelColors.first().toArgb())
        assertEquals(0xFF9C27B0.toInt(), DrawingColorWheelColors.last().toArgb())
    }
}
