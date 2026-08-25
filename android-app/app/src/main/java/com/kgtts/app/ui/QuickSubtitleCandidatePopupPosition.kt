package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

internal data class QuickSubtitleCandidatePopupAnchor(
    val triggerX: Int,
    val triggerY: Int,
    val panelLeft: Int,
    val panelTop: Int,
    val panelRight: Int,
    val panelBottom: Int
)

internal data class QuickSubtitleCandidatePopupPlacement(
    val left: Int,
    val top: Int
)

internal object QuickSubtitleCandidatePopupPosition {
    fun resolve(
        tablet: Boolean,
        landscape: Boolean,
        windowWidth: Int,
        windowHeight: Int,
        popupWidth: Int,
        popupHeight: Int,
        edgeMargin: Int,
        anchor: QuickSubtitleCandidatePopupAnchor?,
        safeLeft: Int = 0,
        safeTop: Int = 0,
        safeRight: Int = 0,
        safeBottom: Int = 0,
        verticalEdgeMargin: Int = edgeMargin
    ): QuickSubtitleCandidatePopupPlacement {
        val minimumLeft = safeLeft + edgeMargin
        val minimumTop = safeTop + verticalEdgeMargin
        val maximumRight = windowWidth - safeRight - edgeMargin
        val maximumBottom = windowHeight - safeBottom - verticalEdgeMargin
        if (!tablet || anchor == null) {
            return QuickSubtitleCandidatePopupPlacement(
                left = clampOrigin(
                    requested = minimumLeft + (maximumRight - minimumLeft - popupWidth) / 2,
                    extent = popupWidth,
                    minimum = minimumLeft,
                    maximumExclusive = maximumRight
                ),
                top = clampOrigin(
                    requested = minimumTop + (maximumBottom - minimumTop - popupHeight) / 2,
                    extent = popupHeight,
                    minimum = minimumTop,
                    maximumExclusive = maximumBottom
                )
            )
        }
        val requestedLeft = if (landscape) {
            anchor.panelRight - popupWidth
        } else {
            anchor.triggerX - popupWidth / 2
        }
        val requestedTop = if (landscape) {
            anchor.triggerY - popupHeight / 2
        } else {
            anchor.panelBottom - popupHeight
        }
        return QuickSubtitleCandidatePopupPlacement(
            left = clampOrigin(requestedLeft, popupWidth, minimumLeft, maximumRight),
            top = clampOrigin(requestedTop, popupHeight, minimumTop, maximumBottom)
        )
    }

    private fun clampOrigin(
        requested: Int,
        extent: Int,
        minimum: Int,
        maximumExclusive: Int
    ): Int {
        val maximum = (maximumExclusive - extent).coerceAtLeast(minimum)
        return requested.coerceIn(minimum, maximum)
    }
}

internal fun Modifier.trackQuickSubtitleCandidatePopupAnchor(
    onAnchorChanged: (QuickSubtitleCandidatePopupAnchor) -> Unit
): Modifier = composed {
    val currentOnAnchorChanged by rememberUpdatedState(onAnchorChanged)
    var panelBounds by remember { mutableStateOf(Rect.Zero) }

    this
        .onGloballyPositioned { panelBounds = it.boundsInWindow() }
        .pointerInput(Unit) {
            awaitEachGesture {
                var change = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                fun publish(position: Offset) {
                    if (panelBounds.width <= 0f || panelBounds.height <= 0f) return
                    val trigger = panelBounds.topLeft + position
                    currentOnAnchorChanged(
                        QuickSubtitleCandidatePopupAnchor(
                            triggerX = trigger.x.roundToInt(),
                            triggerY = trigger.y.roundToInt(),
                            panelLeft = panelBounds.left.roundToInt(),
                            panelTop = panelBounds.top.roundToInt(),
                            panelRight = panelBounds.right.roundToInt(),
                            panelBottom = panelBounds.bottom.roundToInt()
                        )
                    )
                }
                publish(change.position)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    change = event.changes.firstOrNull() ?: break
                    publish(change.position)
                } while (event.changes.any { it.pressed })
            }
        }
}

internal fun Modifier.placeCenterAtParentY(centerYPx: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(
            x = 0,
            y = centerYPx - placeable.height / 2
        )
    }
}
