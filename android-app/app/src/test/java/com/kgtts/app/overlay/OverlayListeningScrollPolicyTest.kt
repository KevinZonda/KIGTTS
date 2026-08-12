package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayListeningScrollPolicyTest {
    @Test
    fun `maximum scroll includes viewport padding`() {
        assertEquals(
            440,
            OverlayListeningScrollPolicy.maximumScrollY(
                childHeightPx = 900,
                viewportHeightPx = 500,
                paddingTopPx = 20,
                paddingBottomPx = 20
            )
        )
    }

    @Test
    fun `bottom edge restores automatic following`() {
        assertTrue(
            OverlayListeningScrollPolicy.isNearBottom(
                scrollYPx = 400,
                maximumScrollYPx = 440,
                thresholdPx = 24,
                canScrollForward = false
            )
        )
    }

    @Test
    fun `history browsing disables automatic following`() {
        assertFalse(
            OverlayListeningScrollPolicy.isNearBottom(
                scrollYPx = 180,
                maximumScrollYPx = 440,
                thresholdPx = 24,
                canScrollForward = true
            )
        )
    }
}
