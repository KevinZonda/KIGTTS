package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenLayoutPolicyTest {
    @Test
    fun `clock only hides for phone portrait mini pages`() {
        assertTrue(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhonePortrait, true))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhonePortrait, false))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhoneLandscape, true))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.Tablet, true))
    }

    @Test
    fun `phone portrait keeps overlay centered`() {
        val mode = LockScreenLayoutPolicy.mode(1080, 2400, 3f)

        assertEquals(LockScreenLayoutMode.PhonePortrait, mode)
        assertEquals(
            120,
            LockScreenLayoutPolicy.overlayLeftPx(mode, 1080, 840, 48)
        )
    }

    @Test
    fun `phone landscape moves overlay to the right`() {
        val mode = LockScreenLayoutPolicy.mode(2400, 1080, 3f)

        assertEquals(LockScreenLayoutMode.PhoneLandscape, mode)
        assertEquals(
            732,
            LockScreenLayoutPolicy.overlayLeftPx(mode, 2400, 1620, 48)
        )
    }

    @Test
    fun `tablet uses the right side in either orientation`() {
        val portraitMode = LockScreenLayoutPolicy.mode(1600, 2560, 2f)
        val landscapeMode = LockScreenLayoutPolicy.mode(2560, 1600, 2f)

        assertEquals(LockScreenLayoutMode.Tablet, portraitMode)
        assertEquals(LockScreenLayoutMode.Tablet, landscapeMode)
        assertEquals(
            752,
            LockScreenLayoutPolicy.overlayLeftPx(portraitMode, 1600, 800, 48)
        )
    }

    @Test
    fun `right aligned overlay respects the side margin when nearly full width`() {
        assertEquals(
            48,
            LockScreenLayoutPolicy.overlayLeftPx(
                LockScreenLayoutMode.PhoneLandscape,
                1080,
                1020,
                48
            )
        )
    }

    @Test
    fun `phone landscape host column stops before the actual overlay`() {
        val mode = LockScreenLayoutPolicy.mode(2400, 1080, 3f)
        val width = LockScreenLayoutPolicy.overlayWidthPx(mode, 2400, 1080, 3f, 48)
        val left = LockScreenLayoutPolicy.overlayLeftPx(mode, 2400, width, 48)

        assertEquals(1620, width)
        assertEquals(732, left)
        assertEquals(
            612,
            LockScreenLayoutPolicy.hostColumnWidthPx(left, 0, 48, 72, 600)
        )
    }

    @Test
    fun `portrait overlay uses measured height without losing its preferred offset`() {
        assertEquals(
            660,
            LockScreenLayoutPolicy.portraitOverlayTopPx(
                screenHeightPx = 3200,
                contentHeightPx = 1850,
                preferredTopPx = 660,
                bottomReservePx = 288,
                marginPx = 60
            )
        )
        assertEquals(
            512,
            LockScreenLayoutPolicy.portraitOverlayTopPx(
                screenHeightPx = 3200,
                contentHeightPx = 2400,
                preferredTopPx = 660,
                bottomReservePx = 288,
                marginPx = 60
            )
        )
    }

    @Test
    fun `portrait mini overlay centers without reserving the clock area`() {
        assertEquals(
            515,
            LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = 2400,
                contentHeightPx = 1250,
                verticalBiasPx = 60,
                marginPx = 60
            )
        )
        assertEquals(
            60,
            LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = 2400,
                contentHeightPx = 2350,
                verticalBiasPx = 60,
                marginPx = 60
            )
        )
    }
}
