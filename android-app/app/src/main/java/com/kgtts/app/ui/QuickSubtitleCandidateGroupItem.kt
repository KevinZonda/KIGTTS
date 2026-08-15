package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
internal fun QuickSubtitleCandidateGroupItem(
    group: QuickSubtitleGroup,
    selected: Boolean,
    vertical: Boolean,
    showLabel: Boolean,
    dragged: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(UiTokens.Radius)
    val elevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else 0.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_group_drag_elevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (dragged) 1.02f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_group_drag_scale"
    )
    val cardColor = md2CardContainerColor()
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        .compositeOver(cardColor)
    Box(
        modifier = Modifier
            .then(if (vertical) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .height(46.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
    ) {
        Card(
            modifier = Modifier
                .then(if (vertical) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .height(44.dp)
                .scale(scale),
            shape = shape,
            backgroundColor = when {
                dragged -> cardColor
                selected -> selectedColor
                else -> Color.Transparent
            },
            elevation = elevation
        ) {
            Row(
                modifier = Modifier
                    .then(if (vertical) Modifier.fillMaxWidth() else Modifier.widthIn(min = 52.dp))
                    .fillMaxHeight()
                    .clip(shape)
                    .clickable(onClick = onClick)
                    .padding(horizontal = if (showLabel) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showLabel) Arrangement.Start else Arrangement.Center
            ) {
                val title = group.title.ifBlank { "未命名分组" }
                if (vertical && showLabel) Spacer(Modifier.width(2.dp))
                MsIcon(group.icon, contentDescription = title)
                if (showLabel) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (vertical) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }
        QuickSubtitleCandidateGroupActionMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            onEdit = onEdit
        )
    }
}

@Composable
private fun QuickSubtitleCandidateGroupActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit
) {
    val performKeyHaptic = rememberKigttsKeyHaptic()
    var rendered by remember { mutableStateOf(expanded) }
    val positionProvider = rememberQuickSubtitleGroupTopEndPopupPositionProvider(2.dp)
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_group_menu_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.92f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_group_menu_scale"
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
                .width(52.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(4.dp),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.MenuElevation
        ) {
            IconButton(
                onClick = {
                    performKeyHaptic()
                    onEdit()
                },
                modifier = Modifier.size(52.dp)
            ) {
                MsIcon("edit", contentDescription = "编辑分组")
            }
        }
    }
}

@Composable
private fun rememberQuickSubtitleGroupTopEndPopupPositionProvider(
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
