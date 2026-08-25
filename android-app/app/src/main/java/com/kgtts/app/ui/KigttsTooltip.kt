package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.max

internal val LocalKigttsTooltipLabelSink = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal fun calculateKigttsTooltipPosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    gapPx: Int,
    edgePaddingPx: Int
): IntOffset {
    val maxX = max(edgePaddingPx, windowSize.width - popupContentSize.width - edgePaddingPx)
    val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
    val x = centeredX.coerceIn(edgePaddingPx, maxX)
    val above = anchorBounds.top - popupContentSize.height - gapPx
    val maxY = max(edgePaddingPx, windowSize.height - popupContentSize.height - edgePaddingPx)
    val below = anchorBounds.bottom + gapPx
    val y = if (above >= edgePaddingPx) above else below.coerceAtMost(maxY)
    return IntOffset(x, y)
}

internal fun shouldShowKigttsTooltipOnLongPress(
    hasBusinessLongClick: Boolean,
    tooltip: String?
): Boolean = !hasBusinessLongClick && !tooltip.isNullOrBlank()

private class KigttsTooltipPositionProvider(
    private val gapPx: Int,
    private val edgePaddingPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = calculateKigttsTooltipPosition(
        anchorBounds = anchorBounds,
        windowSize = windowSize,
        popupContentSize = popupContentSize,
        gapPx = gapPx,
        edgePaddingPx = edgePaddingPx
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KigttsTooltipIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val performHaptic = rememberKigttsKeyHaptic()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    var inferredTooltip by remember { mutableStateOf<String?>(null) }
    val resolvedTooltip = tooltip?.takeIf(String::isNotBlank) ?: inferredTooltip
    val currentTooltip by rememberUpdatedState(resolvedTooltip)
    val tooltipLabelSink = remember {
        { label: String ->
            if (label.isNotBlank() && inferredTooltip != label) {
                inferredTooltip = label
            }
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    var hovering by remember { mutableStateOf(false) }
    var hoverTooltipVisible by remember { mutableStateOf(false) }
    var longPressTooltipVisible by remember { mutableStateOf(false) }
    var longPressRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> hovering = true
                is HoverInteraction.Exit -> hovering = false
            }
        }
    }
    LaunchedEffect(hovering, resolvedTooltip, enabled) {
        hoverTooltipVisible = false
        if (hovering && enabled && !resolvedTooltip.isNullOrBlank()) {
            delay(500)
            if (hovering) hoverTooltipVisible = true
        }
    }
    LaunchedEffect(longPressRequest) {
        if (longPressRequest > 0) {
            longPressTooltipVisible = true
            delay(1_600)
            longPressTooltipVisible = false
        }
    }

    val tooltipVisible = enabled && !resolvedTooltip.isNullOrBlank() &&
        (hoverTooltipVisible || longPressTooltipVisible)
    val visibilityState = remember { MutableTransitionState(false) }
    visibilityState.targetState = tooltipVisible
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        KigttsTooltipPositionProvider(
            gapPx = with(density) { 8.dp.roundToPx() },
            edgePaddingPx = with(density) { 8.dp.roundToPx() }
        )
    }
    val showsTooltipOnLongPress = shouldShowKigttsTooltipOnLongPress(
        hasBusinessLongClick = currentOnLongClick != null,
        tooltip = resolvedTooltip
    )
    val supportsLongClick = currentOnLongClick != null || showsTooltipOnLongPress

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    hoverTooltipVisible = false
                    longPressTooltipVisible = false
                    performHaptic()
                    currentOnClick()
                },
                onLongClickLabel = currentTooltip,
                onLongClick = if (supportsLongClick) {
                    {
                        performHaptic()
                        val longClick = currentOnLongClick
                        if (longClick != null) {
                            longClick()
                        } else if (showsTooltipOnLongPress) {
                            longPressRequest += 1
                        }
                    }
                } else {
                    null
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled,
            LocalKigttsTooltipLabelSink provides tooltipLabelSink,
            content = content
        )

        if (visibilityState.currentState || visibilityState.targetState) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                AnimatedVisibility(
                    visibleState = visibilityState,
                    enter = fadeIn(tween(90)) + scaleIn(
                        animationSpec = tween(90),
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin.Center
                    ),
                    exit = fadeOut(tween(70)) + scaleOut(
                        animationSpec = tween(70),
                        targetScale = 0.98f,
                        transformOrigin = TransformOrigin.Center
                    )
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 240.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.92f),
                        elevation = UiTokens.MenuElevation
                    ) {
                        Text(
                            text = resolvedTooltip.orEmpty(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.surface,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RegisterKigttsTooltipLabel(contentDescription: String?) {
    val sink = LocalKigttsTooltipLabelSink.current
    if (!contentDescription.isNullOrBlank() && sink != null) {
        SideEffect { sink(contentDescription) }
    }
}
