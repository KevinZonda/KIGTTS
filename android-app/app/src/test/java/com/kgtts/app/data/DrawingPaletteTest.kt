package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DrawingPaletteTest {
    @Test
    fun defaultsKeepSixPairsAndRemoveFormerPurplePair() {
        val entries = DrawingPalette().entries

        assertEquals(6, entries.size)
        assertFalse(entries.any { it.lightColorArgb == 0xFF5E35B1.toInt() })
        assertFalse(entries.any { it.darkColorArgb == 0xFFD1C4E9.toInt() })
    }

    @Test
    fun normalizedPreservesOrderAndBothThemeColors() {
        val original = DrawingPalette(
            listOf(
                DrawingPaletteEntry(8L, 0xFF112233.toInt(), 0xFF445566.toInt()),
                DrawingPaletteEntry(3L, 0xFF778899.toInt(), 0xFFAABBCC.toInt())
            )
        )

        assertEquals(original, original.normalized())
    }

    @Test
    fun duplicateIdsAreReplacedAndColorsBecomeOpaque() {
        val decoded = DrawingPalette(
            listOf(
                DrawingPaletteEntry(2L, 0x00112233, 0x00445566),
                DrawingPaletteEntry(2L, 0x00778899, 0x00AABBCC)
            )
        ).normalized()

        assertNotEquals(decoded.entries[0].id, decoded.entries[1].id)
        assertEquals(0xFF112233.toInt(), decoded.entries[0].lightColorArgb)
        assertEquals(0xFFAABBCC.toInt(), decoded.entries[1].darkColorArgb)
    }

    @Test
    fun nextIdUsesFirstAvailablePositiveSlot() {
        val entries = listOf(
            DrawingPaletteEntry(1L, 0, 0),
            DrawingPaletteEntry(3L, 0, 0)
        )

        assertEquals(2L, nextDrawingPaletteEntryId(entries))
    }

    @Test
    fun defaultDrawingColorUsesFirstPairAndFallsBackToThemeColor() {
        val palette = DrawingPalette(
            listOf(DrawingPaletteEntry(7L, 0xFF112233.toInt(), 0xFFCCDDEE.toInt()))
        )

        assertEquals(0xFF112233.toInt(), resolveDefaultDrawingColorArgb(palette, false, 0))
        assertEquals(0xFFCCDDEE.toInt(), resolveDefaultDrawingColorArgb(palette, true, 0))
        assertEquals(
            0xFF445566.toInt(),
            resolveDefaultDrawingColorArgb(DrawingPalette(emptyList()), false, 0x00445566)
        )
    }
}
