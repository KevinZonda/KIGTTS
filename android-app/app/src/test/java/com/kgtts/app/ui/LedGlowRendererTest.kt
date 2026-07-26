package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class LedGlowRendererTest {
    @Test
    fun textGlowRadiusGrowsWithStrengthSizeAndDensity() {
        val baseline = ledTextGlowRadiusPx(
            textSizePx = 120f,
            densityScale = 2f,
            glowStrength = 0.4f
        )

        assertTrue(
            ledTextGlowRadiusPx(120f, 2f, 0.8f) > baseline
        )
        assertTrue(
            ledTextGlowRadiusPx(240f, 2f, 0.4f) > baseline
        )
        assertTrue(
            ledTextGlowRadiusPx(120f, 4f, 0.4f) > baseline
        )
    }

    @Test
    fun dotGlowRadiusGrowsWithPitchAndStrength() {
        val baseline = ledDotGlowRadiusPx(pitchPx = 12, glowStrength = 0.4f)

        assertTrue(ledDotGlowRadiusPx(12, 0.8f) > baseline)
        assertTrue(ledDotGlowRadiusPx(24, 0.4f) > baseline)
        assertTrue(ledDotGlowRadiusPx(0, -1f) >= 1f)
    }

    @Test
    fun glowAlphaIsVisibleAtDefaultStrengthAndGrowsMonotonically() {
        val defaultStrength = 0.42f

        assertTrue(ledOuterGlowAlpha(defaultStrength) >= 80)
        assertTrue(ledInnerGlowAlpha(defaultStrength) >= 130)
        assertTrue(ledOuterGlowAlpha(0.8f) > ledOuterGlowAlpha(defaultStrength))
        assertTrue(ledInnerGlowAlpha(0.8f) > ledInnerGlowAlpha(defaultStrength))
        assertTrue(ledOuterGlowAlpha(0f) == 0)
        assertTrue(ledInnerGlowAlpha(0f) == 0)
    }
}
