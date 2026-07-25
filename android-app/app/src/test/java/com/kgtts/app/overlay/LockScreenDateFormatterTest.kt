package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockScreenDateFormatterTest {
    @Test
    fun `lunar date uses familiar Chinese names`() {
        assertEquals("农历正月初一", LockScreenDateFormatter.lunarLabel(1, 1, false))
        assertEquals("农历闰六月十三", LockScreenDateFormatter.lunarLabel(6, 13, true))
        assertEquals("农历腊月三十", LockScreenDateFormatter.lunarLabel(12, 30, false))
    }

    @Test
    fun `invalid lunar fields are omitted`() {
        assertNull(LockScreenDateFormatter.lunarLabel(0, 1, false))
        assertNull(LockScreenDateFormatter.lunarLabel(1, 31, false))
    }
}
