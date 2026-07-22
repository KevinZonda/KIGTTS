package com.lhtstudio.kigtts.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KigttsTextToolbarStateTest {
    @Test
    fun firstImmediateHideAfterShowIsTreatedAsTransientRelayout() {
        var now = 1_000L
        val state = KigttsTextToolbarState(uptimeMillis = { now })
        state.show(Rect.Zero, null, null, null, {})

        now += 40L
        state.hide()

        assertTrue(state.visible)
        state.hide()
        assertFalse(state.visible)
    }

    @Test
    fun hideAfterGracePeriodDismissesNormally() {
        var now = 1_000L
        val state = KigttsTextToolbarState(uptimeMillis = { now })
        state.show(Rect.Zero, null, null, null, {})

        now += 300L
        state.hide()

        assertFalse(state.visible)
    }

    @Test
    fun explicitDismissIsNeverDebounced() {
        val state = KigttsTextToolbarState(uptimeMillis = { 1_000L })
        state.show(Rect.Zero, null, null, null, {})

        state.dismissImmediately()

        assertFalse(state.visible)
    }

    @Test
    fun popupTracksHostWhenImeMovesItUp() {
        val state = KigttsTextToolbarState(uptimeMillis = { 1_000L })
        state.updateHostOrigin(IntOffset(40, 300))
        state.show(Rect(100f, 500f, 200f, 540f), null, null, null, {})
        val provider = KigttsTextToolbarPositionProvider(
            anchorRect = IntRect(100, 500, 200, 540),
            anchorOriginAtShow = state.anchorOriginAtShow,
            focusedFieldBoundsAtShow = null,
            focusedFieldBounds = null,
            marginPx = 8
        )

        val position = provider.calculatePosition(
            anchorBounds = IntRect(40, 100, 400, 700),
            windowSize = IntSize(500, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(80, 40)
        )

        assertEquals(IntOffset(110, 252), position)
    }

    @Test
    fun popupTracksScrolledFieldAndStaysAnchoredToSelection() {
        val token = Any()
        val state = KigttsTextToolbarState(uptimeMillis = { 1_000L })
        state.updateHostOrigin(IntOffset(40, 100))
        state.focusField(token, Rect(80f, 450f, 420f, 550f))
        state.show(Rect(100f, 500f, 200f, 540f), null, null, null, {})
        state.updateFocusedFieldBounds(token, Rect(80f, 250f, 420f, 350f))
        val provider = KigttsTextToolbarPositionProvider(
            anchorRect = IntRect(100, 500, 200, 540),
            anchorOriginAtShow = state.anchorOriginAtShow,
            focusedFieldBoundsAtShow = state.focusedFieldBoundsAtShow,
            focusedFieldBounds = state.focusedFieldBounds,
            marginPx = 8
        )

        val position = provider.calculatePosition(
            anchorBounds = IntRect(40, 100, 400, 700),
            windowSize = IntSize(500, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(80, 40)
        )

        assertEquals(IntOffset(110, 252), position)
    }

    @Test
    fun regularPagePopupKeepsNormalSelectionMargin() {
        val provider = KigttsTextToolbarPositionProvider(
            anchorRect = IntRect(100, 300, 200, 340),
            anchorOriginAtShow = IntOffset.Zero,
            focusedFieldBoundsAtShow = null,
            focusedFieldBounds = null,
            marginPx = 8
        )

        val position = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 500, 800),
            windowSize = IntSize(500, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(80, 40)
        )

        assertEquals(IntOffset(110, 252), position)
    }
}
