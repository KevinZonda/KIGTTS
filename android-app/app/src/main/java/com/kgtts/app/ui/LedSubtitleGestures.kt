package com.lhtstudio.kigtts.app.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

private enum class LedDragAxis { Undetermined, Horizontal, Vertical }

internal fun Modifier.ledSubtitleDragGestures(
    enabled: Boolean,
    motionState: LedMarqueeMotionState,
    verticalOpenThresholdPx: Float,
    quickSwipeEnabled: Boolean,
    quickSwipeDistanceThresholdPx: Float,
    quickSwipeVelocityThresholdPxPerSecond: Float,
    onInteraction: () -> Unit,
    onOpenInput: () -> Unit,
    onOpenQuickText: () -> Unit
): Modifier = pointerInput(
    enabled,
    motionState,
    verticalOpenThresholdPx,
    quickSwipeEnabled,
    quickSwipeDistanceThresholdPx,
    quickSwipeVelocityThresholdPxPerSecond
) {
    if (!enabled) return@pointerInput
    var totalDrag = Offset.Zero
    var dragAxis = LedDragAxis.Undetermined
    var velocityTracker = VelocityTracker()

    detectDragGestures(
        onDragStart = { startPosition ->
            totalDrag = Offset.Zero
            dragAxis = LedDragAxis.Undetermined
            velocityTracker = VelocityTracker().apply {
                addPosition(SystemClock.uptimeMillis(), startPosition)
            }
            onInteraction()
        },
        onDragCancel = {
            motionState.cancelHorizontalDrag()
            totalDrag = Offset.Zero
            dragAxis = LedDragAxis.Undetermined
        },
        onDragEnd = {
            val velocityX = velocityTracker.calculateVelocity().x
            when (dragAxis) {
                LedDragAxis.Horizontal -> {
                    motionState.endHorizontalDrag(velocityX)
                    if (
                        quickSwipeEnabled &&
                        totalDrag.x <= -quickSwipeDistanceThresholdPx &&
                        velocityX <= -quickSwipeVelocityThresholdPxPerSecond
                    ) {
                        onOpenQuickText()
                    }
                }
                LedDragAxis.Vertical -> {
                    motionState.cancelHorizontalDrag()
                    if (totalDrag.y <= -verticalOpenThresholdPx) onOpenInput()
                }
                LedDragAxis.Undetermined -> motionState.cancelHorizontalDrag()
            }
            totalDrag = Offset.Zero
            dragAxis = LedDragAxis.Undetermined
        }
    ) { change, dragAmount ->
        velocityTracker.addPosition(change.uptimeMillis, change.position)
        totalDrag += dragAmount
        if (dragAxis == LedDragAxis.Undetermined) {
            dragAxis = when {
                abs(totalDrag.x) > abs(totalDrag.y) * AXIS_LOCK_BIAS -> LedDragAxis.Horizontal
                abs(totalDrag.y) > abs(totalDrag.x) * AXIS_LOCK_BIAS -> LedDragAxis.Vertical
                else -> LedDragAxis.Undetermined
            }
            if (dragAxis == LedDragAxis.Horizontal) {
                motionState.beginHorizontalDrag()
                motionState.dragBy(totalDrag.x)
            }
        } else if (dragAxis == LedDragAxis.Horizontal) {
            motionState.dragBy(dragAmount.x)
        }
        change.consume()
    }
}

private const val AXIS_LOCK_BIAS = 1.08f
