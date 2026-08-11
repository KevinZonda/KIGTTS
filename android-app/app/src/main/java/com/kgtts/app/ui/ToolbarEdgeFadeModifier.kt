package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.animatedToolbarEdgeFade(
    scrollState: ScrollState,
    vertical: Boolean,
    backgroundColor: Color = md2ElevatedCardContainerColor(UiTokens.CardElevation)
): Modifier {
    val startAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(140),
        label = "toolbar_start_fade"
    )
    val endAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(140),
        label = "toolbar_end_fade"
    )
    return drawWithContent {
        drawContent()
        val edgeSize = 22.dp.toPx().coerceAtMost(if (vertical) size.height else size.width)
        if (edgeSize <= 0f) return@drawWithContent

        if (startAlpha > 0f) {
            val opaque = backgroundColor.copy(alpha = backgroundColor.alpha * startAlpha)
            val transparent = backgroundColor.copy(alpha = 0f)
            if (vertical) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(opaque, transparent),
                        startY = 0f,
                        endY = edgeSize
                    ),
                    size = Size(size.width, edgeSize)
                )
            } else {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(opaque, transparent),
                        startX = 0f,
                        endX = edgeSize
                    ),
                    size = Size(edgeSize, size.height)
                )
            }
        }
        if (endAlpha > 0f) {
            val opaque = backgroundColor.copy(alpha = backgroundColor.alpha * endAlpha)
            val transparent = backgroundColor.copy(alpha = 0f)
            if (vertical) {
                val top = size.height - edgeSize
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(transparent, opaque),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, edgeSize)
                )
            } else {
                val left = size.width - edgeSize
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(transparent, opaque),
                        startX = left,
                        endX = size.width
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(edgeSize, size.height)
                )
            }
        }
    }
}
