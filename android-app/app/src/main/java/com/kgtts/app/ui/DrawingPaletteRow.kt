package com.lhtstudio.kigtts.app.ui

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.DrawingPaletteEntry
import com.lhtstudio.kigtts.app.data.WindowsColorNamesZhCn

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun DrawingPaletteRow(
    entry: DrawingPaletteEntry,
    dragged: Boolean,
    onEditLight: () -> Unit,
    onEditDark: () -> Unit,
    onDelete: () -> Unit,
    onStartDrag: () -> Unit
) {
    val cardElevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else UiTokens.CardElevation,
        animationSpec = tween(
            durationMillis = if (dragged) 120 else 160,
            easing = FastOutSlowInEasing
        ),
        label = "drawing_palette_row_elevation"
    )
    val overlay by animateColorAsState(
        targetValue = if (dragged) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(120),
        label = "drawing_palette_row_overlay"
    )
    Box(modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = cardElevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(overlay)
                    .graphicsLayer { alpha = if (dragged) 0.98f else 1f }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaletteColorButton("亮色", "light_mode", Color(entry.lightColorArgb), onEditLight)
                PaletteColorButton("暗色", "dark_mode", Color(entry.darkColorArgb), onEditDark)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${WindowsColorNamesZhCn.displayName(entry.lightColorArgb)} · " +
                            WindowsColorNamesZhCn.displayName(entry.darkColorArgb),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${entry.lightColorArgb.toHexColor()} · ${entry.darkColorArgb.toHexColor()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Md2IconButton(
                    icon = "drag_indicator",
                    contentDescription = "拖动排序",
                    onClick = {},
                    modifier = Modifier.pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                onStartDrag()
                                true
                            }
                            MotionEvent.ACTION_MOVE,
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> true
                            else -> false
                        }
                    }
                )
                Md2IconButton("delete", "删除颜色", onDelete)
            }
        }
    }
}

@Composable
private fun PaletteColorButton(label: String, icon: String, color: Color, onClick: () -> Unit) {
    val iconColor = if (color.luminance() > 0.52f) Color(0xFF111417) else Color.White
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "编辑${label}主题颜色" }
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color,
        contentColor = iconColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            MsIcon(icon, contentDescription = null, tint = iconColor)
        }
    }
}

private fun Int.toHexColor(): String = "#%06X".format(this and 0x00FFFFFF)
