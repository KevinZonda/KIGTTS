package com.lhtstudio.kigtts.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCardRenderCacheTest {
    @Test
    fun qualityLimitsCoverQhdDisplays() {
        assertEquals(3840, QUICK_CARD_CROP_LONG_EDGE_PX)
        assertEquals(2160, QUICK_CARD_CROP_SHORT_EDGE_PX)
        assertTrue(QUICK_CARD_RENDER_LONG_EDGE_PX >= 2560)
    }

    @Test
    fun fourKCardIsNotSampledBackToFullHd() {
        assertEquals(
            1,
            quickCardImageSampleSize(
                QUICK_CARD_CROP_SHORT_EDGE_PX,
                QUICK_CARD_CROP_LONG_EDGE_PX,
                QUICK_CARD_RENDER_LONG_EDGE_PX
            )
        )
    }

    @Test
    fun oversizedSourceUsesLargestSafePowerOfTwoSample() {
        assertEquals(2, quickCardImageSampleSize(4320, 7680, QUICK_CARD_RENDER_LONG_EDGE_PX))
    }
}
