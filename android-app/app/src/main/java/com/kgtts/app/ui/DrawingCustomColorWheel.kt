package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val DrawingColorWheelColors = listOf(
    Color(0xFFF44336),
    Color(0xFFFF9800),
    Color(0xFFFFEB3B),
    Color(0xFF4CAF50),
    Color(0xFF00BCD4),
    Color(0xFF2196F3),
    Color(0xFF9C27B0)
)

@Composable
internal fun DrawingCustomColorWheel(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    KigttsIconButton(
        onClick = onClick,
        tooltip = "自定义颜色",
        modifier = Modifier
            .size(22.dp)
            .semantics { contentDescription = "自定义颜色" }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, borderColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.5.dp)
            ) {
                val strokeWidth = size.minDimension * 0.14f
                val arcInset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val segment = 360f / DrawingColorWheelColors.size
                val overlap = 0.75f
                DrawingColorWheelColors.forEachIndexed { index, segmentColor ->
                    drawArc(
                        color = segmentColor,
                        startAngle = -90f + segment * index - overlap / 2f,
                        sweepAngle = segment + overlap,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.21f
                )
            }
        }
    }
}
