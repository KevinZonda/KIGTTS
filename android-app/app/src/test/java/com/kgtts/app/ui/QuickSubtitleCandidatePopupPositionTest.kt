package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitleCandidatePopupPositionTest {
    private val anchor = QuickSubtitleCandidatePopupAnchor(
        triggerX = 1780,
        triggerY = 620,
        panelLeft = 1680,
        panelTop = 180,
        panelRight = 2380,
        panelBottom = 1380
    )

    @Test
    fun `tablet landscape aligns popup right edge to quick text panel`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = true,
            landscape = true,
            windowWidth = 2560,
            windowHeight = 1600,
            popupWidth = 1200,
            popupHeight = 720,
            edgeMargin = 40,
            anchor = anchor
        )

        assertEquals(anchor.panelRight, placement.left + 1200)
        assertEquals(anchor.triggerY, placement.top + 720 / 2)
    }

    @Test
    fun `tablet portrait aligns popup bottom edge and follows touch horizontally`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = true,
            landscape = false,
            windowWidth = 1600,
            windowHeight = 2560,
            popupWidth = 1000,
            popupHeight = 1100,
            edgeMargin = 40,
            anchor = anchor.copy(
                triggerX = 980,
                panelRight = 1500,
                panelBottom = 2200
            )
        )

        assertEquals(980, placement.left + 1000 / 2)
        assertEquals(2200, placement.top + 1100)
    }

    @Test
    fun `phone keeps the existing centered popup placement`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = false,
            landscape = true,
            windowWidth = 2400,
            windowHeight = 1080,
            popupWidth = 1600,
            popupHeight = 900,
            edgeMargin = 48,
            anchor = anchor
        )

        assertEquals(400, placement.left)
        assertEquals(90, placement.top)
    }

    @Test
    fun `tablet touch placement stays inside window margins`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = true,
            landscape = true,
            windowWidth = 1400,
            windowHeight = 1000,
            popupWidth = 1000,
            popupHeight = 700,
            edgeMargin = 30,
            anchor = anchor.copy(panelRight = 600, triggerY = 80)
        )

        assertEquals(30, placement.left)
        assertEquals(30, placement.top)
    }

    @Test
    fun `phone landscape centers popup inside asymmetric safe drawing bounds`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = false,
            landscape = true,
            windowWidth = 2400,
            windowHeight = 1080,
            popupWidth = 1600,
            popupHeight = 760,
            edgeMargin = 48,
            anchor = null,
            safeLeft = 96,
            safeTop = 24,
            safeRight = 48,
            safeBottom = 120,
            verticalEdgeMargin = 32
        )

        assertEquals(424, placement.left)
        assertEquals(112, placement.top)
        assertEquals(872, placement.top + 760)
    }

    @Test
    fun `tablet anchor is clamped above navigation safe area`() {
        val placement = QuickSubtitleCandidatePopupPosition.resolve(
            tablet = true,
            landscape = true,
            windowWidth = 2000,
            windowHeight = 1200,
            popupWidth = 1200,
            popupHeight = 800,
            edgeMargin = 40,
            anchor = anchor.copy(panelRight = 1900, triggerY = 1050),
            safeBottom = 120,
            verticalEdgeMargin = 24
        )

        assertEquals(256, placement.top)
        assertEquals(1056, placement.top + 800)
    }
}
