package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
internal fun QuickSubtitleCandidateItem(
    text: String,
    colorArgb: Int?,
    grid: Boolean,
    dragged: Boolean,
    menuExpanded: Boolean,
    canDelete: Boolean,
    canMoveToGroup: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(UiTokens.Radius)
    val cardElevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else 0.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_drag_elevation"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (dragged) 1.02f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_drag_scale"
    )
    val baseColor = md2CardContainerColor()
    val gridColor = if (currentAppDarkTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(baseColor)
    } else {
        baseColor
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (grid) 84.dp else 65.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (grid) Modifier.padding(4.dp) else Modifier)
                .height(if (grid) 76.dp else 64.dp)
                .scale(cardScale),
            shape = shape,
            backgroundColor = if (grid) gridColor else baseColor,
            elevation = cardElevation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(
                        if (grid) {
                            Modifier.border(
                                width = 1.dp,
                                color = colorArgb?.let(::Color)
                                    ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                shape = shape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .quickSubtitleItemColorMarker(
                        colorArgb = colorArgb,
                        edge = if (grid) QuickSubtitleItemColorEdge.Bottom else QuickSubtitleItemColorEdge.Left,
                        crossAxisInset = if (grid) 0.dp else 6.dp
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = text,
                    maxLines = if (grid) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        QuickSubtitleCandidateActionMenu(
            expanded = menuExpanded,
            canDelete = canDelete,
            canMoveToGroup = canMoveToGroup,
            onDismissRequest = onDismissMenu,
            onEdit = onEdit,
            onMove = onMove,
            onDelete = onDelete
        )
        if (showDivider) {
            Divider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun QuickSubtitleCandidateActionMenu(
    expanded: Boolean,
    canDelete: Boolean,
    canMoveToGroup: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val performKeyHaptic = rememberKigttsKeyHaptic()
    var rendered by remember { mutableStateOf(expanded) }
    val positionProvider = rememberCandidateTopEndPopupPositionProvider(verticalMargin = 2.dp)
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_action_menu_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.92f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_action_menu_scale"
    )
    LaunchedEffect(expanded) {
        if (expanded) rendered = true else if (rendered) {
            delay(150L)
            rendered = false
        }
    }
    if (!rendered) return
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .padding(6.dp)
                .width(156.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(4.dp),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.MenuElevation
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        performKeyHaptic()
                        onEdit()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    MsIcon("edit", contentDescription = "编辑快捷文本")
                }
                IconButton(
                    onClick = {
                        performKeyHaptic()
                        onMove()
                    },
                    enabled = canMoveToGroup,
                    modifier = Modifier.size(52.dp)
                ) {
                    MsIcon("drive_file_move", contentDescription = "移动到其它分组")
                }
                IconButton(
                    onClick = {
                        performKeyHaptic()
                        onDelete()
                    },
                    enabled = canDelete,
                    modifier = Modifier.size(52.dp)
                ) {
                    MsIcon("delete", contentDescription = "删除快捷文本")
                }
            }
        }
    }
}

@Composable
private fun rememberCandidateTopEndPopupPositionProvider(
    verticalMargin: Dp
): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, verticalMargin) {
        object : PopupPositionProvider {
            private val verticalMarginPx = with(density) { verticalMargin.roundToPx() }

            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val preferredX = when (layoutDirection) {
                    LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
                    LayoutDirection.Rtl -> anchorBounds.left
                }
                val x = preferredX.coerceIn(
                    minimumValue = 0,
                    maximumValue = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                )
                val aboveY = anchorBounds.top - verticalMarginPx - popupContentSize.height
                val insideTopY = anchorBounds.top + verticalMarginPx
                val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                return IntOffset(
                    x = x,
                    y = if (aboveY >= 0) aboveY else insideTopY.coerceIn(0, maxY)
                )
            }
        }
    }
}
