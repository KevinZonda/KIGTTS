package com.lhtstudio.kigtts.app.overlay

import com.lhtstudio.kigtts.app.data.AppFontWeightAxis
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayFontWeightsTest {
    @Test
    fun shiftsVariableFontWeightsFromPreferredValue() {
        val weights = resolveVariableOverlayFontWeights(
            axis = AppFontWeightAxis(min = 100, default = 400, max = 700),
            sourceDefaultWeight = 400,
            preferredWeight = 600
        )

        assertEquals(600, weights.regular)
        assertEquals(700, weights.bold)
    }

    @Test
    fun snapsStaticFontWeightsToRealFiles() {
        val weights = resolveStaticOverlayFontWeights(
            availableWeights = listOf(100, 200, 300, 400, 450, 500, 600, 700),
            sourceDefaultWeight = 400,
            preferredWeight = 450
        )

        assertEquals(450, weights.regular)
        assertEquals(700, weights.bold)
    }

    @Test
    fun preservesOffsetForLighterStaticPreference() {
        val weights = resolveStaticOverlayFontWeights(
            availableWeights = listOf(100, 300, 400, 600, 700),
            sourceDefaultWeight = 400,
            preferredWeight = 300
        )

        assertEquals(300, weights.regular)
        assertEquals(600, weights.bold)
    }
}
