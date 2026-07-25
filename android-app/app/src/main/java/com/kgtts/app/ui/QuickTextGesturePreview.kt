package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.QuickTextGestureTemplate
import kotlin.math.hypot

@Composable
internal fun QuickTextGesturePreview(
    template: QuickTextGestureTemplate,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gesture_preview")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1450,
                delayMillis = 350,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "gesture_stroke_progress"
    )
    val progress = if (animated) animatedProgress else 1f
    val lineColor = MaterialTheme.colorScheme.accentText
    val startColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Canvas(
        modifier = modifier
            .aspectRatio(1.2f)
            .background(backgroundColor, RoundedCornerShape(4.dp))
    ) {
        if (template.points.size < 2) return@Canvas
        val inset = size.minDimension * 0.12f
        val drawableWidth = (size.width - inset * 2f).coerceAtLeast(1f)
        val drawableHeight = (size.height - inset * 2f).coerceAtLeast(1f)
        val points = template.points.map { point ->
            Offset(
                x = inset + point.x * drawableWidth,
                y = inset + point.y * drawableHeight
            )
        }
        val lengths = points.zipWithNext().map { (start, end) ->
            hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
        }
        val totalLength = lengths.sum().coerceAtLeast(1f)
        var remainingLength = totalLength * progress.coerceIn(0f, 1f)
        var tip = points.first()

        drawCircle(
            color = startColor.copy(alpha = 0.55f),
            radius = 5.dp.toPx(),
            center = points.first(),
            style = Stroke(width = 2.dp.toPx())
        )
        points.zipWithNext().forEachIndexed { index, (start, end) ->
            if (remainingLength <= 0f) return@forEachIndexed
            val segmentLength = lengths[index]
            val segmentProgress = (remainingLength / segmentLength).coerceIn(0f, 1f)
            val partialEnd = Offset(
                x = start.x + (end.x - start.x) * segmentProgress,
                y = start.y + (end.y - start.y) * segmentProgress
            )
            drawLine(
                color = lineColor,
                start = start,
                end = partialEnd,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            tip = partialEnd
            remainingLength -= segmentLength
        }
        if (animated && progress < 0.995f) {
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = tip)
        }
    }
}
