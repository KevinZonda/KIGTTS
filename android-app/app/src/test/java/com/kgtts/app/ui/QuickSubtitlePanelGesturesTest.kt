package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitlePanelGesturesTest {
    @Test
    fun normalDirectionMapsNegativeDeltaToCandidates() {
        assertEquals(
            QuickSubtitlePanelGestureAction.OpenCandidates,
            resolveQuickSubtitlePanelGesture(primaryDelta = -48f, reversed = false)
        )
        assertEquals(
            QuickSubtitlePanelGestureAction.OpenInput,
            resolveQuickSubtitlePanelGesture(primaryDelta = 48f, reversed = false)
        )
    }

    @Test
    fun reversedDirectionSwapsActions() {
        assertEquals(
            QuickSubtitlePanelGestureAction.OpenInput,
            resolveQuickSubtitlePanelGesture(primaryDelta = -48f, reversed = true)
        )
        assertEquals(
            QuickSubtitlePanelGestureAction.OpenCandidates,
            resolveQuickSubtitlePanelGesture(primaryDelta = 48f, reversed = true)
        )
    }
}
