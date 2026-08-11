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

    @Test
    fun previewCursorSwipeMovesByCharacterCount() {
        assertEquals(
            7,
            resolveCursorIndexAfterSwipe(currentIndex = 4, textLength = 12, delta = 3)
        )
        assertEquals(
            2,
            resolveCursorIndexAfterSwipe(currentIndex = 4, textLength = 12, delta = -2)
        )
    }

    @Test
    fun previewCursorSwipeClampsToTextBounds() {
        assertEquals(
            0,
            resolveCursorIndexAfterSwipe(currentIndex = 2, textLength = 12, delta = -20)
        )
        assertEquals(
            12,
            resolveCursorIndexAfterSwipe(currentIndex = 8, textLength = 12, delta = 20)
        )
    }

    @Test
    fun pinchZoomAdjustsAndClampsFontSize() {
        assertEquals(
            72f,
            resolvePinchAdjustedFontSize(
                currentFontSizeSp = 48f,
                zoomFactor = 1.5f,
                minFontSizeSp = 28f,
                maxFontSizeSp = 96f
            ),
            0.001f
        )
        assertEquals(
            96f,
            resolvePinchAdjustedFontSize(
                currentFontSizeSp = 80f,
                zoomFactor = 2f,
                minFontSizeSp = 28f,
                maxFontSizeSp = 96f
            ),
            0.001f
        )
        assertEquals(
            28f,
            resolvePinchAdjustedFontSize(
                currentFontSizeSp = 40f,
                zoomFactor = 0.1f,
                minFontSizeSp = 28f,
                maxFontSizeSp = 96f
            ),
            0.001f
        )
    }

    @Test
    fun pinchZoomIgnoresInvalidScaleFactor() {
        assertEquals(
            56f,
            resolvePinchAdjustedFontSize(
                currentFontSizeSp = 56f,
                zoomFactor = Float.NaN,
                minFontSizeSp = 28f,
                maxFontSizeSp = 96f
            ),
            0.001f
        )
    }
}
