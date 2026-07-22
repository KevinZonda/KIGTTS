package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.QuickTextGestureBinding
import com.lhtstudio.kigtts.app.data.QuickTextGesturePoint
import com.lhtstudio.kigtts.app.data.QuickTextGestureSettings

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun QuickSubtitleGestureSurface(
    settings: QuickTextGestureSettings,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onGesture: (QuickTextGestureBinding) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gestureInputEnabled = settings.enabled && settings.activeBindings().isNotEmpty()
    val recognizer = remember(settings) { QuickTextGestureRecognizer(settings) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnGesture by rememberUpdatedState(onGesture)
    var activeTrace by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val traceColor = MaterialTheme.colorScheme.primary
    val traceWidth = with(LocalDensity.current) { 6.dp.toPx() }

    val interactionModifier = if (gestureInputEnabled) {
        Modifier
            .semantics {
                role = Role.Button
                onClick(label = "打开字幕预览") {
                    currentOnClick()
                    true
                }
                onLongClick(label = "复制字幕") {
                    currentOnLongClick()
                    true
                }
            }
            .pointerInput(recognizer) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sampledPoints = mutableListOf(down.position)
                    var moved = false
                    var released = false

                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) {
                            released = true
                            if (moved) {
                                sampledPoints += change.position
                                change.consume()
                                val binding = recognizer.recognize(
                                    points = sampledPoints.map { point ->
                                        QuickTextGesturePoint(point.x, point.y)
                                    },
                                    durationMs = change.uptimeMillis - down.uptimeMillis,
                                    surfaceWidth = size.width.toFloat(),
                                    surfaceHeight = size.height.toFloat()
                                )
                                if (binding != null) currentOnGesture(binding)
                            } else if (
                                change.uptimeMillis - down.uptimeMillis >=
                                viewConfiguration.longPressTimeoutMillis
                            ) {
                                currentOnLongClick()
                            } else {
                                currentOnClick()
                            }
                        } else {
                            sampledPoints += change.position
                            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            if (moved) {
                                activeTrace = sampledPoints.toList()
                                change.consume()
                            }
                        }
                    }
                    activeTrace = emptyList()
                }
            }
    } else {
        Modifier.combinedClickable(
            onClick = currentOnClick,
            onLongClick = currentOnLongClick
        )
    }

    Box(modifier = modifier.then(interactionModifier)) {
        content()
        if (activeTrace.size > 1) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                activeTrace.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = traceColor,
                        start = start,
                        end = end,
                        strokeWidth = traceWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
