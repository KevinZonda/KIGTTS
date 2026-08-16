package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenLayoutPolicyTest {
    @Test
    fun `compact clock aligns to the visible phone overlay card`() {
        assertEquals(
            CompactClockFrame(leftPx = 78, widthPx = 924),
            LockScreenLayoutPolicy.compactClockFrame(
                mode = LockScreenLayoutMode.PhonePortrait,
                screenWidthPx = 1080,
                density = 3f,
                sideMarginPx = 48,
                overlayHorizontalPaddingPx = 30
            )
        )
    }

    @Test
    fun `compact clock aligns to the visible large square overlay card`() {
        assertEquals(
            CompactClockFrame(leftPx = 629, widthPx = 950),
            LockScreenLayoutPolicy.compactClockFrame(
                mode = LockScreenLayoutMode.LargeSquare,
                screenWidthPx = 2208,
                density = 2.5f,
                sideMarginPx = 40,
                overlayHorizontalPaddingPx = 25
            )
        )
    }

    @Test
    fun `clock only hides for phone portrait mini pages`() {
        assertTrue(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhonePortrait, true))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhonePortrait, false))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.PhoneLandscape, true))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.TabletPortrait, true))
        assertFalse(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.TabletLandscape, true))
        assertTrue(LockScreenLayoutPolicy.hideClock(LockScreenLayoutMode.LargeSquare, true))
    }

    @Test
    fun `phone portrait listening launcher only uses compact clock when normal clock does not fit`() {
        assertTrue(
            LockScreenLayoutPolicy.useCompactClock(
                mode = LockScreenLayoutMode.PhonePortrait,
                miniOverlayVisible = false,
                listeningOverlayVisible = true,
                listeningTopClearancePx = 150,
                normalClockRequiredHeightPx = 180
            )
        )
        assertFalse(
            LockScreenLayoutPolicy.useCompactClock(
                mode = LockScreenLayoutMode.PhonePortrait,
                miniOverlayVisible = false,
                listeningOverlayVisible = true,
                listeningTopClearancePx = 260,
                normalClockRequiredHeightPx = 180
            )
        )
        assertFalse(
            LockScreenLayoutPolicy.useCompactClock(
                mode = LockScreenLayoutMode.PhonePortrait,
                miniOverlayVisible = true,
                listeningOverlayVisible = true,
                listeningTopClearancePx = 80,
                normalClockRequiredHeightPx = 180
            )
        )
    }

    @Test
    fun `large phone listening group moves down while compact phones keep their offsets`() {
        assertEquals(
            0f,
            LockScreenLayoutPolicy.phonePortraitListeningGroupOffsetDp(
                largePhone = true,
                launcherVisible = true
            )
        )
        assertEquals(
            48f,
            LockScreenLayoutPolicy.phonePortraitListeningGroupOffsetDp(
                largePhone = true,
                launcherVisible = false
            )
        )
        assertEquals(
            -32f,
            LockScreenLayoutPolicy.phonePortraitListeningGroupOffsetDp(
                largePhone = false,
                launcherVisible = true
            )
        )
        assertEquals(
            -56f,
            LockScreenLayoutPolicy.phonePortraitListeningGroupOffsetDp(
                largePhone = false,
                launcherVisible = false
            )
        )
    }

    @Test
    fun `large phone launcher group centers between clock and unlock reserves when space allows`() {
        assertEquals(
            750,
            LockScreenLayoutPolicy.centeredPortraitLauncherGroupTopPx(
                currentTopPx = 260,
                groupHeightPx = 1700,
                safeTopPx = 0,
                safeBottomPx = 3200,
                topReservePx = 200,
                bottomReservePx = 200,
                minimumTopPx = 40,
                maximumTopPx = 1460
            )
        )
        assertEquals(
            260,
            LockScreenLayoutPolicy.centeredPortraitLauncherGroupTopPx(
                currentTopPx = 260,
                groupHeightPx = 2800,
                safeTopPx = 0,
                safeBottomPx = 3200,
                topReservePx = 300,
                bottomReservePx = 300,
                minimumTopPx = 40,
                maximumTopPx = 360
            )
        )
    }

    @Test
    fun `large square only compacts clock when normal stack does not fit`() {
        assertTrue(
            LockScreenLayoutPolicy.useCompactClock(
                mode = LockScreenLayoutMode.LargeSquare,
                miniOverlayVisible = false,
                listeningOverlayVisible = true,
                listeningTopClearancePx = 150,
                normalClockRequiredHeightPx = 180
            )
        )
        assertFalse(
            LockScreenLayoutPolicy.useCompactClock(
                mode = LockScreenLayoutMode.LargeSquare,
                miniOverlayVisible = false,
                listeningOverlayVisible = true,
                listeningTopClearancePx = 220,
                normalClockRequiredHeightPx = 180
            )
        )
    }

    @Test
    fun `large unfolded square is distinct from a small square cover screen`() {
        assertEquals(
            LockScreenLayoutMode.LargeSquare,
            LockScreenLayoutPolicy.mode(2208, 1840, 2.5f)
        )
        assertEquals(
            LockScreenLayoutMode.PhonePortrait,
            LockScreenLayoutPolicy.mode(720, 748, 3f)
        )
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
    fun `large phone portrait stays below tablet threshold`() {
        assertTrue(LockScreenLayoutPolicy.isLargePhonePortrait(1440, 3200, 3.775f))
        assertTrue(LockScreenLayoutPolicy.isLargePhonePortrait(1440, 3200, 2.6125f))
        assertFalse(LockScreenLayoutPolicy.isLargePhonePortrait(1440, 3200, 4.5f))
        assertFalse(LockScreenLayoutPolicy.isLargePhonePortrait(1600, 2560, 2f))
        assertFalse(LockScreenLayoutPolicy.isLargePhonePortrait(3200, 1440, 2.6125f))
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
    fun `tablet portrait centers while landscape uses the right side`() {
        val portraitMode = LockScreenLayoutPolicy.mode(1600, 2560, 2f)
        val landscapeMode = LockScreenLayoutPolicy.mode(2560, 1600, 2f)

        assertEquals(LockScreenLayoutMode.TabletPortrait, portraitMode)
        assertEquals(LockScreenLayoutMode.TabletLandscape, landscapeMode)
        assertEquals(
            400,
            LockScreenLayoutPolicy.overlayLeftPx(portraitMode, 1600, 800, 48)
        )
        assertEquals(
            1520,
            LockScreenLayoutPolicy.overlayLeftPx(landscapeMode, 2560, 800, 48)
        )
    }

    @Test
    fun `tablet landscape centers overlay in the right half`() {
        val screenWidth = 2560
        val contentWidth = 800
        val left = LockScreenLayoutPolicy.overlayLeftPx(
            LockScreenLayoutMode.TabletLandscape,
            screenWidth,
            contentWidth,
            48
        )

        assertEquals(screenWidth * 3 / 4, left + contentWidth / 2)
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
        val width = LockScreenLayoutPolicy.overlayWidthPx(mode, 2400, 3f, 48)
        val left = LockScreenLayoutPolicy.overlayLeftPx(mode, 2400, width, 48)

        assertEquals(1620, width)
        assertEquals(732, left)
        assertEquals(
            612,
            LockScreenLayoutPolicy.hostColumnWidthPx(left, 0, 48, 72, 600)
        )
    }

    @Test
    fun `phone landscape listening reserves a clock column without affecting tablets`() {
        val safeLeft = 0
        val safeRight = 3200
        val density = 4.5f
        val listeningLeft = LockScreenLayoutPolicy.phoneLandscapeListeningSafeLeftPx(
            safeLeftPx = safeLeft,
            safeRightPx = safeRight,
            density = density
        )

        assertEquals(640, listeningLeft)
        assertTrue(
            LockScreenLayoutPolicy.phoneLandscapeInfoScale(
                columnWidthPx = listeningLeft - 72 - 108,
                density = density
            ) < 1f
        )
    }

    @Test
    fun `phone landscape overlay receives a bounded upward bias`() {
        assertEquals(455, LockScreenLayoutPolicy.phoneLandscapeOverlayTopPx(500, 4.5f))
        assertEquals(54, LockScreenLayoutPolicy.phoneLandscapeOverlayTopPx(20, 4.5f))
    }

    @Test
    fun `lock screen keeps design width separate from visual safe width`() {
        val density = 4.5f
        val mode = LockScreenLayoutMode.PhonePortrait

        assertEquals(1620, LockScreenLayoutPolicy.overlayDesignWidthPx(mode, density))
        assertEquals(
            1296,
            LockScreenLayoutPolicy.overlayWidthPx(
                mode = mode,
                screenWidthPx = 1440,
                density = density,
                sideMarginPx = 72
            )
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
