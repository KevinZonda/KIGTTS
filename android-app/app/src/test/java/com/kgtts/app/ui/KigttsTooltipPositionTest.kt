package com.lhtstudio.kigtts.app.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class KigttsTooltipPositionTest {
    @Test
    fun `tooltip is centered above an anchor when space is available`() {
        val result = calculateKigttsTooltipPosition(
            anchorBounds = IntRect(left = 100, top = 100, right = 148, bottom = 148),
            windowSize = IntSize(400, 800),
            popupContentSize = IntSize(120, 40),
            gapPx = 8,
            edgePaddingPx = 8
        )

        assertEquals(64, result.x)
        assertEquals(52, result.y)
    }

    @Test
    fun `tooltip moves below an anchor near the top edge`() {
        val result = calculateKigttsTooltipPosition(
            anchorBounds = IntRect(left = 100, top = 4, right = 148, bottom = 52),
            windowSize = IntSize(400, 800),
            popupContentSize = IntSize(120, 40),
            gapPx = 8,
            edgePaddingPx = 8
        )

        assertEquals(60, result.y)
    }

    @Test
    fun `tooltip is clamped inside horizontal screen edges`() {
        val left = calculateKigttsTooltipPosition(
            anchorBounds = IntRect(left = 0, top = 100, right = 48, bottom = 148),
            windowSize = IntSize(240, 400),
            popupContentSize = IntSize(160, 40),
            gapPx = 8,
            edgePaddingPx = 8
        )
        val right = calculateKigttsTooltipPosition(
            anchorBounds = IntRect(left = 220, top = 100, right = 240, bottom = 148),
            windowSize = IntSize(240, 400),
            popupContentSize = IntSize(160, 40),
            gapPx = 8,
            edgePaddingPx = 8
        )

        assertEquals(8, left.x)
        assertEquals(72, right.x)
    }

    @Test
    fun `long press shows tooltip only when no business action exists`() {
        assertEquals(
            true,
            shouldShowKigttsTooltipOnLongPress(
                hasBusinessLongClick = false,
                tooltip = "打开设置"
            )
        )
        assertEquals(
            false,
            shouldShowKigttsTooltipOnLongPress(
                hasBusinessLongClick = true,
                tooltip = "打开设置"
            )
        )
        assertEquals(
            false,
            shouldShowKigttsTooltipOnLongPress(
                hasBusinessLongClick = false,
                tooltip = "  "
            )
        )
    }
}
