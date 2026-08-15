package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayQuickPanelGestureFrameTest {
    @Test
    fun `vertical layout maps up to candidates and down to input`() {
        assertEquals(
            OverlayQuickPanelGestureAction.OpenCandidates,
            resolveOverlayQuickPanelGesture(-48f, horizontalLayout = false, reversed = false)
        )
        assertEquals(
            OverlayQuickPanelGestureAction.OpenInput,
            resolveOverlayQuickPanelGesture(48f, horizontalLayout = false, reversed = false)
        )
    }

    @Test
    fun `horizontal layout maps right to candidates and left to input`() {
        assertEquals(
            OverlayQuickPanelGestureAction.OpenCandidates,
            resolveOverlayQuickPanelGesture(48f, horizontalLayout = true, reversed = false)
        )
        assertEquals(
            OverlayQuickPanelGestureAction.OpenInput,
            resolveOverlayQuickPanelGesture(-48f, horizontalLayout = true, reversed = false)
        )
    }

    @Test
    fun `reversed setting swaps both actions`() {
        assertEquals(
            OverlayQuickPanelGestureAction.OpenInput,
            resolveOverlayQuickPanelGesture(-48f, horizontalLayout = false, reversed = true)
        )
        assertEquals(
            OverlayQuickPanelGestureAction.OpenCandidates,
            resolveOverlayQuickPanelGesture(-48f, horizontalLayout = true, reversed = true)
        )
    }
}
