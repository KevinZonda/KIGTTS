package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedSubtitleGesturesTest {
    @Test
    fun fastSwipeReleasedInsideWindowOpensQuickText() {
        assertTrue(
            shouldOpenLedQuickText(
                enabled = true,
                totalDragX = -80f,
                velocityX = -1_600f,
                releaseElapsedMillis = 260L,
                distanceThresholdPx = 42f,
                velocityThresholdPxPerSecond = 1_100f,
                releaseThresholdMillis = 420L
            )
        )
    }

    @Test
    fun heldDragDoesNotOpenEvenWithFastFinalVelocity() {
        assertFalse(
            shouldOpenLedQuickText(
                enabled = true,
                totalDragX = -120f,
                velocityX = -2_000f,
                releaseElapsedMillis = 900L,
                distanceThresholdPx = 42f,
                velocityThresholdPxPerSecond = 1_100f,
                releaseThresholdMillis = 420L
            )
        )
    }
}
