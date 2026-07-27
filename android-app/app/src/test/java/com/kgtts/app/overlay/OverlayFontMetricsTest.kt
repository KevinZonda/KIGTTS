package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayFontMetricsTest {
    @Test
    fun compactTextUsesStableMaterialLineHeight() {
        assertEquals(
            20,
            resolveOverlayStableLineHeightPx(
                textSizePx = 13f,
                scaledDensity = 1f
            )
        )
    }

    @Test
    fun scaledDensityIsAppliedToStableLineHeight() {
        assertEquals(
            40,
            resolveOverlayStableLineHeightPx(
                textSizePx = 26f,
                scaledDensity = 2f
            )
        )
    }

    @Test
    fun largeTextUsesCompactDisplayMultiplier() {
        assertEquals(
            115,
            resolveOverlayStableLineHeightPx(
                textSizePx = 100f,
                scaledDensity = 1f
            )
        )
    }

    @Test
    fun explicitPreviewMultiplierOverridesTypographyBucket() {
        assertEquals(
            136,
            resolveOverlayStableLineHeightPx(
                textSizePx = 100f,
                scaledDensity = 1f,
                explicitMultiplier = 1.36f
            )
        )
    }

    @Test
    fun compactMiniQuickTextLineHeightFitsThreeLines() {
        assertEquals(
            19,
            resolveOverlayStableLineHeightPx(
                textSizePx = 16f,
                scaledDensity = 1f,
                explicitMultiplier = 1.15f
            )
        )
    }
}
