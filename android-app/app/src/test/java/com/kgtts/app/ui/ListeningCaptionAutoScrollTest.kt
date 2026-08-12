package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningCaptionAutoScrollTest {
    @Test
    fun `bottom edge restores following even while last item measurement is stale`() {
        assertTrue(
            isListeningCaptionNearBottom(
                totalItems = 12,
                lastVisibleIndex = 10,
                remainingBottomPx = Int.MAX_VALUE,
                canScrollForward = false,
                thresholdPx = 24f
            )
        )
    }

    @Test
    fun `near-bottom threshold restores following when newest item is visible`() {
        assertTrue(
            isListeningCaptionNearBottom(
                totalItems = 12,
                lastVisibleIndex = 11,
                remainingBottomPx = 18,
                canScrollForward = true,
                thresholdPx = 24f
            )
        )
    }

    @Test
    fun `history browsing remains detached from newest captions`() {
        assertFalse(
            isListeningCaptionNearBottom(
                totalItems = 12,
                lastVisibleIndex = 8,
                remainingBottomPx = Int.MAX_VALUE,
                canScrollForward = true,
                thresholdPx = 24f
            )
        )
    }

    @Test
    fun `programmatic scrolling never pauses following`() {
        assertFalse(
            shouldPauseListeningCaptionFollow(
                userDragging = false,
                nearBottom = false
            )
        )
    }

    @Test
    fun `dragging away from bottom pauses following`() {
        assertTrue(
            shouldPauseListeningCaptionFollow(
                userDragging = true,
                nearBottom = false
            )
        )
    }

    @Test
    fun `settling at bottom restores following`() {
        assertTrue(
            shouldRestoreListeningCaptionFollow(
                scrolling = false,
                nearBottom = true,
                totalItems = 12
            )
        )
    }
}
