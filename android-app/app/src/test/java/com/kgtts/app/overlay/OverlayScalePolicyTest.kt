package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayScalePolicyTest {
    @Test
    fun `keeps original size when content fits safe area`() {
        val result = OverlayScalePolicy.resolve(
            requiredWidthPx = 360,
            requiredHeightPx = 480,
            safeWidthPx = 440,
            safeHeightPx = 560,
            horizontalMarginPx = 16,
            verticalMarginPx = 20
        )

        assertEquals(1f, result.scale)
        assertEquals(360, result.visualWidthPx)
        assertEquals(480, result.visualHeightPx)
    }

    @Test
    fun `uses the stricter axis while preserving aspect ratio`() {
        val result = OverlayScalePolicy.resolve(
            requiredWidthPx = 400,
            requiredHeightPx = 500,
            safeWidthPx = 360,
            safeHeightPx = 440,
            horizontalMarginPx = 20,
            verticalMarginPx = 20
        )

        assertEquals(0.8f, result.scale)
        assertEquals(320, result.visualWidthPx)
        assertEquals(400, result.visualHeightPx)
    }

    @Test
    fun `scales the complete landscape envelope on a near square narrow screen`() {
        val result = OverlayScalePolicy.resolve(
            requiredWidthPx = 540,
            requiredHeightPx = 364,
            safeWidthPx = 430,
            safeHeightPx = 430,
            horizontalMarginPx = 16,
            verticalMarginPx = 20
        )

        assertEquals(398f / 540f, result.scale, 0.0001f)
        assertEquals(398, result.visualWidthPx)
        assertEquals(268, result.visualHeightPx)
    }

    @Test
    fun `does not shrink below minimum scale`() {
        val result = OverlayScalePolicy.resolve(
            requiredWidthPx = 400,
            requiredHeightPx = 600,
            safeWidthPx = 200,
            safeHeightPx = 260,
            horizontalMarginPx = 16,
            verticalMarginPx = 20
        )

        assertEquals(OverlayScalePolicy.DEFAULT_MINIMUM_SCALE, result.scale)
        assertEquals(200, result.visualWidthPx)
        assertEquals(300, result.visualHeightPx)
    }

    @Test
    fun `positions and transforms grouped children inside safe bounds`() {
        val groupStart = OverlayScalePolicy.placeGroupStart(
            logicalStartPx = 20,
            logicalExtentPx = 400,
            visualExtentPx = 320,
            safeStartPx = 0,
            safeEndPx = 360,
            marginPx = 16
        )
        val childStart = OverlayScalePolicy.transformChildStart(
            logicalChildStartPx = 120,
            logicalGroupStartPx = 20,
            visualGroupStartPx = groupStart,
            scale = 0.8f
        )

        assertEquals(24, groupStart)
        assertEquals(104, childStart)
    }

    @Test
    fun `centers a scaled landscape group independently from its oversized logical bounds`() {
        val groupStart = OverlayScalePolicy.placeCenteredGroupStart(
            preferredCenterPx = 500,
            visualExtentPx = 600,
            safeStartPx = 100,
            safeEndPx = 1000,
            marginPx = 20
        )

        assertEquals(200, groupStart)
    }
}
