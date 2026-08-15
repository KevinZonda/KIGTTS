package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSubtitleLargeFontDefaultsTest {
    @Test
    fun `large subtitle font defaults to enabled on tablets`() {
        assertTrue(resolveQuickSubtitleAllowLargeFont(stored = null, smallestScreenWidthDp = 600))
    }

    @Test
    fun `large subtitle font defaults to disabled on phones`() {
        assertFalse(resolveQuickSubtitleAllowLargeFont(stored = null, smallestScreenWidthDp = 599))
    }

    @Test
    fun `stored large subtitle font preference wins on every device`() {
        assertFalse(resolveQuickSubtitleAllowLargeFont(stored = false, smallestScreenWidthDp = 720))
        assertTrue(resolveQuickSubtitleAllowLargeFont(stored = true, smallestScreenWidthDp = 360))
    }

    @Test
    fun `full group names default to enabled on tablets`() {
        assertTrue(resolveForceFullWidthTabs(stored = null, smallestScreenWidthDp = 600))
        assertFalse(resolveForceFullWidthTabs(stored = null, smallestScreenWidthDp = 599))
    }

    @Test
    fun `stored full group name preference wins on every device`() {
        assertFalse(resolveForceFullWidthTabs(stored = false, smallestScreenWidthDp = 720))
        assertTrue(resolveForceFullWidthTabs(stored = true, smallestScreenWidthDp = 360))
    }
}
