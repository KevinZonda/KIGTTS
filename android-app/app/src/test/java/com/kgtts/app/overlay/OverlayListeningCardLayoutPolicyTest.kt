package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayListeningCardLayoutPolicyTest {
    @Test
    fun `portrait listening card matches visible overlay width and spacing`() {
        val layout = resolve(vertical = true)

        assertEquals(110, layout.listeningLeftPx)
        assertEquals(340, layout.listeningWidthPx)
        assertEquals(110, layout.mainLeftPx + 10)
        assertEquals(12, layout.mainTopPx + 10 - layout.listeningTopPx - layout.listeningHeightPx)
    }

    @Test
    fun `landscape listening card matches visible overlay height`() {
        val layout = resolve(vertical = false)

        assertEquals(460, layout.listeningHeightPx)
        assertEquals(210, layout.listeningTopPx)
        assertEquals(210, layout.mainTopPx + 10)
    }

    @Test
    fun `vertical layout reserves host clock and unlock areas`() {
        val layout = resolve(
            vertical = true,
            verticalTopInsetPx = 90,
            verticalBottomInsetPx = 70
        )

        assertEquals(90, layout.listeningTopPx)
        assertEquals(262, layout.mainTopPx + 10)
        assertEquals(78, 800 - (layout.mainTopPx + 10 + 460))
    }

    private fun resolve(
        vertical: Boolean,
        verticalTopInsetPx: Int = 10,
        verticalBottomInsetPx: Int = 10
    ): OverlayListeningCardLayout = OverlayListeningCardLayoutPolicy.resolve(
        vertical = vertical,
        safeLeftPx = 0,
        safeTopPx = 0,
        safeRightPx = 800,
        safeBottomPx = 800,
        mainOuterWidthPx = 360,
        mainOuterHeightPx = 480,
        mainPaddingLeftPx = 10,
        mainPaddingTopPx = 10,
        mainPaddingRightPx = 10,
        mainPaddingBottomPx = 10,
        preferredMainLeftPx = 100,
        preferredMainTopPx = 200,
        requestedPortraitListeningHeightPx = 160,
        requestedLandscapeListeningWidthPx = 220,
        minimumListeningExtentPx = 96,
        gapPx = 12,
        edgeInsetPx = 12,
        verticalTopInsetPx = verticalTopInsetPx,
        verticalBottomInsetPx = verticalBottomInsetPx,
        landscapeCenterXPx = 400,
        listeningOnRight = false
    )
}
