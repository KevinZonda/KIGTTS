package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsColorNamesZhCnTest {
    @Test
    fun defaultPaletteColorsUseWindowsSampledNames() {
        assertEquals("深青色", WindowsColorNamesZhCn.displayName(0xFF038387.toInt()))
        assertEquals("浅绿色", WindowsColorNamesZhCn.displayName(0xFF7DE8EA.toInt()))
        assertEquals("蓝色", WindowsColorNamesZhCn.displayName(0xFF1E88E5.toInt()))
        assertEquals("浅蓝色", WindowsColorNamesZhCn.displayName(0xFF90CAF9.toInt()))
        assertEquals("玫瑰红色", WindowsColorNamesZhCn.displayName(0xFFFF9E9E.toInt()))
        assertEquals("冰蓝色", WindowsColorNamesZhCn.displayName(0xFFECEFF1.toInt()))
    }

    @Test
    fun alphaDoesNotAffectDisplayName() {
        assertEquals(
            WindowsColorNamesZhCn.displayName(0xFFB5523B.toInt()),
            WindowsColorNamesZhCn.displayName(0x00B5523B)
        )
    }

    @Test
    fun colorRemarkContainsUppercaseHexAndDisplayName() {
        assertEquals("#038387 · 深青色", formatColorHexAndNameZhCn(0x7F038387))
    }

    @Test
    fun sampledGridAlwaysProducesAColorSuffix() {
        for (red in 0..255 step 17) {
            for (green in 0..255 step 17) {
                for (blue in 0..255 step 17) {
                    val argb = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    assertTrue(WindowsColorNamesZhCn.displayName(argb).endsWith("色"))
                }
            }
        }
    }
}
