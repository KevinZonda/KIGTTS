package com.lhtstudio.kigtts.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingPaletteInstrumentedTest {
    @Test
    fun jsonRoundTripPreservesOrderAndThemePairs() {
        val original = DrawingPalette(
            listOf(
                DrawingPaletteEntry(7L, 0xFF112233.toInt(), 0xFF445566.toInt()),
                DrawingPaletteEntry(2L, 0xFF778899.toInt(), 0xFFAABBCC.toInt())
            )
        )

        assertEquals(original, decodeDrawingPalette(encodeDrawingPalette(original)))
    }
}
