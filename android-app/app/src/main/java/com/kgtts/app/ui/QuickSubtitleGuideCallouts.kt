package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal data class QuickSubtitleGuideCallout(
    val anchor: QuickSubtitleGuideAnchor,
    val label: String
)

@Composable
internal fun QuickSubtitleGuideCallouts(
    callouts: List<QuickSubtitleGuideCallout>,
    anchorBounds: Map<QuickSubtitleGuideAnchor, Rect>,
    overlayBounds: Rect,
    screenSize: IntSize,
    initialDelayMillis: Long,
    modifier: Modifier = Modifier
) {
    if (overlayBounds == Rect.Zero || screenSize.width <= 0 || screenSize.height <= 0) return
    val actionBounds = anchorBounds[QuickSubtitleGuideAnchor.DisplayActions]
    val verticalActions = actionBounds != null && actionBounds.height > actionBounds.width
    var visibleCalloutCount by remember(callouts) { mutableIntStateOf(0) }
    LaunchedEffect(callouts, initialDelayMillis) {
        visibleCalloutCount = 0
        delay(initialDelayMillis)
        callouts.forEachIndexed { index, _ ->
            visibleCalloutCount = index + 1
            if (index < callouts.lastIndex) delay(GUIDE_CALLOUT_STAGGER_MS)
        }
    }
    Box(modifier = modifier) {
        callouts.forEachIndexed { index, callout ->
            val windowTarget = anchorBounds[callout.anchor] ?: return@forEachIndexed
            val target = Rect(
                left = windowTarget.left - overlayBounds.left,
                top = windowTarget.top - overlayBounds.top,
                right = windowTarget.right - overlayBounds.left,
                bottom = windowTarget.bottom - overlayBounds.top
            )
            val placement = calloutPlacement(callout.anchor, verticalActions)
            GuideArrowBubble(
                label = callout.label,
                target = target,
                placement = placement,
                horizontalOffset = calloutHorizontalOffset(callout.anchor),
                screenSize = screenSize,
                visible = index < visibleCalloutCount
            )
        }
    }
}

@Composable
private fun GuideArrowBubble(
    label: String,
    target: Rect,
    placement: GuideCalloutPlacement,
    horizontalOffset: Dp,
    screenSize: IntSize,
    visible: Boolean
) {
    var measuredSize by remember(label, placement) { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val margin = with(density) { 4.dp.toPx() }
    val gap = with(density) { 3.dp.toPx() }
    val insideGap = with(density) { 12.dp.toPx() }
    val horizontalOffsetPx = with(density) { horizontalOffset.toPx() }
    val labelLines = label.lines()
    val longestLineLength = labelLines.maxOfOrNull { it.length } ?: label.length
    val estimatedWidth = with(density) { (longestLineLength * 12 + 16).dp.toPx() }
    val estimatedHeight = with(density) { (if (labelLines.size > 1) 46.dp else 31.dp).toPx() }
    val bubbleWidth = measuredSize.width.takeIf { it > 0 }?.toFloat() ?: estimatedWidth
    val bubbleHeight = measuredSize.height.takeIf { it > 0 }?.toFloat() ?: estimatedHeight
    val rawOffset = when (placement) {
        GuideCalloutPlacement.Above -> Offset(
            x = target.center.x - bubbleWidth / 2f + horizontalOffsetPx,
            y = target.top - bubbleHeight - gap
        )

        GuideCalloutPlacement.Below -> Offset(
            x = target.center.x - bubbleWidth / 2f + horizontalOffsetPx,
            y = target.bottom + gap
        )

        GuideCalloutPlacement.Left -> Offset(
            x = target.left - bubbleWidth - gap + horizontalOffsetPx,
            y = target.center.y - bubbleHeight / 2f
        )

        GuideCalloutPlacement.InsideBottom -> Offset(
            x = target.center.x - bubbleWidth / 2f + horizontalOffsetPx,
            y = target.bottom - bubbleHeight - insideGap
        )

        GuideCalloutPlacement.InsideTop -> Offset(
            x = target.center.x - bubbleWidth / 2f + horizontalOffsetPx,
            y = target.top + insideGap
        )
    }
    val x = rawOffset.x.coerceIn(margin, (screenSize.width - bubbleWidth - margin).coerceAtLeast(margin))
    val y = rawOffset.y.coerceIn(margin, (screenSize.height - bubbleHeight - margin).coerceAtLeast(margin))
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .onSizeChanged { measuredSize = it }
            .then(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }),
        enter = fadeIn(animationSpec = tween(GUIDE_CALLOUT_FADE_DURATION_MS)) +
            scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(
                    durationMillis = GUIDE_CALLOUT_SCALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            ) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = GUIDE_CALLOUT_SCALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { height -> height / 3 }
            )
    ) {
        GuideBubbleContent(label = label, placement = placement)
    }
}

@Composable
private fun GuideBubbleContent(label: String, placement: GuideCalloutPlacement) {
    val arrowExtent = 6.dp
    val contentPadding = when (placement) {
        GuideCalloutPlacement.Above,
        GuideCalloutPlacement.InsideBottom,
        GuideCalloutPlacement.InsideTop -> Modifier.padding(
            start = 5.dp,
            top = 4.dp,
            end = 5.dp,
            bottom = 4.dp + arrowExtent
        )

        GuideCalloutPlacement.Below -> Modifier.padding(
            start = 5.dp,
            top = 4.dp + arrowExtent,
            end = 5.dp,
            bottom = 4.dp
        )

        GuideCalloutPlacement.Left -> Modifier.padding(
            start = 5.dp,
            top = 4.dp,
            end = 5.dp + arrowExtent,
            bottom = 4.dp
        )
    }
    Surface(
        shape = remember(placement) { GuideBubbleShape(placement) },
        color = MaterialTheme.colorScheme.surface,
        elevation = 6.dp
    ) {
        Text(
            text = label,
            modifier = contentPadding,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.5.sp,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

private class GuideBubbleShape(
    private val placement: GuideCalloutPlacement
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline.Generic {
        val arrowWidth = with(density) { 10.dp.toPx() }.coerceAtMost(
            if (placement == GuideCalloutPlacement.Left) size.height else size.width
        )
        val arrowExtent = with(density) { 6.dp.toPx() }
        val cornerRadius = with(density) { 4.dp.toPx() }
        val body = when (placement) {
            GuideCalloutPlacement.Above,
            GuideCalloutPlacement.InsideBottom,
            GuideCalloutPlacement.InsideTop -> Rect(0f, 0f, size.width, size.height - arrowExtent)
            GuideCalloutPlacement.Below -> Rect(0f, arrowExtent, size.width, size.height)
            GuideCalloutPlacement.Left -> Rect(0f, 0f, size.width - arrowExtent, size.height)
        }
        val path = Path().apply {
            addRoundRect(RoundRect(body, cornerRadius, cornerRadius))
            when (placement) {
                GuideCalloutPlacement.Above,
                GuideCalloutPlacement.InsideBottom,
                GuideCalloutPlacement.InsideTop -> {
                    moveTo(size.width / 2f - arrowWidth / 2f, body.bottom)
                    lineTo(size.width / 2f + arrowWidth / 2f, body.bottom)
                    lineTo(size.width / 2f, size.height)
                }

                GuideCalloutPlacement.Below -> {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width / 2f + arrowWidth / 2f, body.top)
                    lineTo(size.width / 2f - arrowWidth / 2f, body.top)
                }

                GuideCalloutPlacement.Left -> {
                    moveTo(body.right, size.height / 2f - arrowWidth / 2f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(body.right, size.height / 2f + arrowWidth / 2f)
                }
            }
            close()
        }
        return Outline.Generic(path)
    }
}

private fun calloutPlacement(
    anchor: QuickSubtitleGuideAnchor,
    verticalActions: Boolean
): GuideCalloutPlacement = when (anchor) {
    QuickSubtitleGuideAnchor.QuickText -> GuideCalloutPlacement.InsideTop
    QuickSubtitleGuideAnchor.SubtitleDisplay -> GuideCalloutPlacement.InsideBottom

    QuickSubtitleGuideAnchor.ActionBold,
    QuickSubtitleGuideAnchor.ActionAlignment,
    QuickSubtitleGuideAnchor.ActionRotate,
    QuickSubtitleGuideAnchor.ActionLed,
    QuickSubtitleGuideAnchor.ActionClear,
    QuickSubtitleGuideAnchor.ActionHistory,
    QuickSubtitleGuideAnchor.ActionFontSize -> {
        if (verticalActions) GuideCalloutPlacement.Left else GuideCalloutPlacement.Below
    }

    QuickSubtitleGuideAnchor.TopBarMenu,
    QuickSubtitleGuideAnchor.TopBarStatus,
    QuickSubtitleGuideAnchor.TopBarEdit,
    QuickSubtitleGuideAnchor.TopBarFullscreen -> GuideCalloutPlacement.Below

    QuickSubtitleGuideAnchor.BottomSend,
    QuickSubtitleGuideAnchor.RecognitionFab -> GuideCalloutPlacement.Left
    else -> GuideCalloutPlacement.Above
}

private fun calloutHorizontalOffset(anchor: QuickSubtitleGuideAnchor): Dp = when (anchor) {
    QuickSubtitleGuideAnchor.BottomCursorRight -> (-3).dp
    QuickSubtitleGuideAnchor.BottomPlayOnSend -> (-2).dp
    QuickSubtitleGuideAnchor.BottomToggleQuickText -> 3.dp

    else -> 0.dp
}

private enum class GuideCalloutPlacement {
    Above,
    Below,
    Left,
    InsideBottom,
    InsideTop
}

private const val GUIDE_CALLOUT_STAGGER_MS = 45L
private const val GUIDE_CALLOUT_FADE_DURATION_MS = 150
private const val GUIDE_CALLOUT_SCALE_DURATION_MS = 180
