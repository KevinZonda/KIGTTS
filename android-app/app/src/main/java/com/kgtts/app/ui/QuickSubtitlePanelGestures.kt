package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal enum class QuickSubtitlePanelGestureAction {
    OpenCandidates,
    OpenInput
}

internal fun resolveQuickSubtitlePanelGesture(
    primaryDelta: Float,
    reversed: Boolean
): QuickSubtitlePanelGestureAction {
    val towardCandidates = primaryDelta < 0f
    val effectiveTowardCandidates = if (reversed) !towardCandidates else towardCandidates
    return if (effectiveTowardCandidates) {
        QuickSubtitlePanelGestureAction.OpenCandidates
    } else {
        QuickSubtitlePanelGestureAction.OpenInput
    }
}

internal fun Modifier.quickSubtitlePanelGestures(
    enabled: Boolean,
    landscape: Boolean,
    reversed: Boolean,
    onOpenCandidates: () -> Unit,
    onOpenInput: () -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(landscape, reversed, onOpenCandidates, onOpenInput) {
        val triggerDistance = 44.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            var triggered = false
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!triggered) {
                    total += change.positionChange()
                    val primary = if (landscape) total.x else total.y
                    val secondary = if (landscape) total.y else total.x
                    if (
                        abs(primary) >= triggerDistance &&
                        abs(primary) > abs(secondary) * 1.2f
                    ) {
                        change.consume()
                        when (resolveQuickSubtitlePanelGesture(primary, reversed)) {
                            QuickSubtitlePanelGestureAction.OpenCandidates -> onOpenCandidates()
                            QuickSubtitlePanelGestureAction.OpenInput -> onOpenInput()
                        }
                        triggered = true
                    }
                } else {
                    change.consume()
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
