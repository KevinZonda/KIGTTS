package com.lhtstudio.kigtts.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal enum class QuickSubtitleGuideAnchor {
    QuickText,
    QuickTextGroupSwitcher,
    SubtitleDisplay,
    DisplayActions,
    BottomBar,
    RecognitionFab,
    ActionBold,
    ActionAlignment,
    ActionRotate,
    ActionLed,
    ActionClear,
    ActionHistory,
    ActionFontSize,
    BottomCursorLeft,
    BottomCursorRight,
    BottomPlayOnSend,
    BottomToggleQuickText,
    BottomReplay,
    BottomMore,
    BottomSend,
    TopBarMenu,
    TopBarStatus,
    TopBarEdit,
    TopBarFullscreen
}

internal data class QuickSubtitleGuideStep(
    val title: String,
    val anchors: Set<QuickSubtitleGuideAnchor>,
    val messages: List<String>,
    val callouts: List<QuickSubtitleGuideCallout> = emptyList()
)

internal val LocalQuickSubtitleGuideAnchorRecorder = staticCompositionLocalOf<
    (QuickSubtitleGuideAnchor, Rect) -> Unit
> { { _, _ -> } }

internal fun quickSubtitleGuideSteps(
    compactControls: Boolean,
    panelGesturesEnabled: Boolean = true,
    panelGesturesReversed: Boolean = false,
    isLandscape: Boolean = false
): List<QuickSubtitleGuideStep> = listOf(
    QuickSubtitleGuideStep(
        title = if (compactControls) "紧凑快捷文本" else "快捷文本与分组",
        anchors = buildSet {
            add(QuickSubtitleGuideAnchor.QuickText)
            if (compactControls) add(QuickSubtitleGuideAnchor.QuickTextGroupSwitcher)
        },
        messages = buildList {
            add("点按快捷文本会立即更新大字幕；长按任意条目可打开完整候选列表。")
            if (panelGesturesEnabled) {
                val candidateDirection = when {
                    isLandscape && panelGesturesReversed -> "右滑"
                    isLandscape -> "左滑"
                    panelGesturesReversed -> "下滑"
                    else -> "上滑"
                }
                val inputDirection = when {
                    isLandscape && panelGesturesReversed -> "左滑"
                    isLandscape -> "右滑"
                    panelGesturesReversed -> "上滑"
                    else -> "下滑"
                }
                add("$candidateDirection 可打开候选列表，$inputDirection 可直接输入文本。")
            }
            if (compactControls) {
                add("竖屏可在右侧分组选择器上下滑动切组；横屏使用底部分组选择器左右滑动。")
                add("编辑快捷文本的入口位于页面顶栏。")
            } else {
                add("使用分组栏切换内容，右侧编辑按钮可管理分组和快捷文本。")
            }
        },
        callouts = buildList {
            val quickTextLabel = if (!panelGesturesEnabled) {
                "长按打开候选列表"
            } else {
                val candidateDirection = when {
                    isLandscape && panelGesturesReversed -> "右滑"
                    isLandscape -> "左滑"
                    panelGesturesReversed -> "下滑"
                    else -> "上滑"
                }
                val inputDirection = when {
                    isLandscape && panelGesturesReversed -> "左滑"
                    isLandscape -> "右滑"
                    panelGesturesReversed -> "上滑"
                    else -> "下滑"
                }
                "长按或${candidateDirection}打开候选\n${inputDirection}打开文本输入"
            }
            add(
                QuickSubtitleGuideCallout(
                    QuickSubtitleGuideAnchor.QuickText,
                    quickTextLabel
                )
            )
            if (compactControls) {
                add(
                    QuickSubtitleGuideCallout(
                        QuickSubtitleGuideAnchor.QuickTextGroupSwitcher,
                        if (isLandscape) "左右滑动切换分组" else "上下滑动切换分组"
                    )
                )
            }
        }
    ),
    QuickSubtitleGuideStep(
        title = "大字幕与快捷操作",
        anchors = setOf(
            QuickSubtitleGuideAnchor.SubtitleDisplay,
            QuickSubtitleGuideAnchor.DisplayActions,
            QuickSubtitleGuideAnchor.TopBarMenu,
            QuickSubtitleGuideAnchor.TopBarStatus,
            QuickSubtitleGuideAnchor.TopBarEdit,
            QuickSubtitleGuideAnchor.TopBarFullscreen
        ),
        messages = listOf("点按大字幕进入全屏预览，长按大字幕可复制当前内容。"),
        callouts = listOf(
            QuickSubtitleGuideCallout(
                QuickSubtitleGuideAnchor.SubtitleDisplay,
                "大字幕（点按进入预览，长按复制文本）"
            ),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionBold, "粗体"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionAlignment, "对齐"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionRotate, "倒置"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionLed, "LED"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionClear, "清屏"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionHistory, "历史"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.ActionFontSize, "调整字体大小"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.TopBarMenu, "菜单"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.TopBarStatus, "音频设置菜单"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.TopBarEdit, "编辑"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.TopBarFullscreen, "全屏")
        )
    ),
    QuickSubtitleGuideStep(
        title = "输入、朗读与语音识别",
        anchors = setOf(
            QuickSubtitleGuideAnchor.BottomBar,
            QuickSubtitleGuideAnchor.RecognitionFab
        ),
        messages = listOf(
            "输入文本后使用发送按钮更新字幕。",
            "麦克风按钮用于语音识别。",
            "可以从设置中再次进入使用引导。"
        ),
        callouts = listOf(
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomCursorLeft, "左移"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomCursorRight, "右移"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomPlayOnSend, "朗读开关"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomToggleQuickText, "折叠"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomReplay, "重读"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomMore, "更多"),
            QuickSubtitleGuideCallout(QuickSubtitleGuideAnchor.BottomSend, "发送"),
            QuickSubtitleGuideCallout(
                QuickSubtitleGuideAnchor.RecognitionFab,
                "语音识别\n（需要安装资源包）"
            )
        )
    )
)

internal fun Modifier.quickSubtitleGuideAnchor(
    anchor: QuickSubtitleGuideAnchor,
    onBoundsChanged: (QuickSubtitleGuideAnchor, Rect) -> Unit
): Modifier = onGloballyPositioned { coordinates ->
    onBoundsChanged(anchor, coordinates.boundsInWindow())
}

internal fun shouldPresentQuickSubtitleGuide(
    firstRunCompleted: Boolean,
    replayRequestId: Int
): Boolean = !firstRunCompleted || replayRequestId != 0

@Composable
internal fun QuickSubtitleFirstRunGuideCoordinator(
    visible: Boolean,
    compactControls: Boolean,
    panelGesturesEnabled: Boolean,
    panelGesturesReversed: Boolean,
    isLandscape: Boolean,
    replayRequestId: Int,
    anchorBounds: Map<QuickSubtitleGuideAnchor, Rect>,
    onSelectCompactControls: (Boolean) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by rememberSaveable { mutableIntStateOf(GUIDE_PHASE_MODE) }
    var postponedForVisit by rememberSaveable { mutableStateOf(false) }
    var selectedCompact by rememberSaveable { mutableStateOf(compactControls) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(visible, compactControls) {
        if (visible && phase == GUIDE_PHASE_MODE) selectedCompact = compactControls
    }
    LaunchedEffect(replayRequestId) {
        if (replayRequestId != 0) {
            postponedForVisit = false
            stepIndex = 0
            phase = GUIDE_PHASE_OVERLAY
        }
    }
    if (!visible || postponedForVisit) return

    if (phase == GUIDE_PHASE_MODE && replayRequestId == 0) {
        QuickSubtitleModeSelectionDialog(
            selectedCompact = selectedCompact,
            onSelectedCompactChange = { selectedCompact = it },
            onPostpone = { postponedForVisit = true },
            onStartGuide = {
                onSelectCompactControls(selectedCompact)
                stepIndex = 0
                phase = GUIDE_PHASE_OVERLAY
            }
        )
        return
    }

    val steps = remember(
        compactControls,
        panelGesturesEnabled,
        panelGesturesReversed,
        isLandscape
    ) {
        quickSubtitleGuideSteps(
            compactControls = compactControls,
            panelGesturesEnabled = panelGesturesEnabled,
            panelGesturesReversed = panelGesturesReversed,
            isLandscape = isLandscape
        )
    }
    val currentIndex = stepIndex.coerceIn(0, steps.lastIndex)
    BackHandler { postponedForVisit = true }
    QuickSubtitleGuideOverlay(
        step = steps[currentIndex],
        stepIndex = currentIndex,
        stepCount = steps.size,
        anchorBounds = anchorBounds,
        onPrevious = if (currentIndex > 0) ({ stepIndex = currentIndex - 1 }) else null,
        onNext = {
            if (currentIndex == steps.lastIndex) onComplete() else stepIndex = currentIndex + 1
        },
        onSkip = onComplete,
        modifier = modifier
    )
}

@Composable
private fun QuickSubtitleGuideOverlay(
    step: QuickSubtitleGuideStep,
    stepIndex: Int,
    stepCount: Int,
    anchorBounds: Map<QuickSubtitleGuideAnchor, Rect>,
    onPrevious: (() -> Unit)?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val primary = MaterialTheme.colorScheme.primary
        var overlayBounds by remember { mutableStateOf(Rect.Zero) }
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val paddingPx = with(density) { 7.dp.toPx() }
        val cornerPx = with(density) { 8.dp.toPx() }
        val localHoles = step.anchors.mapIndexedNotNull { sequenceIndex, anchor ->
            anchorBounds[anchor]?.let { bounds ->
                sequenceIndex to Rect(
                    left = (bounds.left - overlayBounds.left - paddingPx).coerceAtLeast(0f),
                    top = (bounds.top - overlayBounds.top - paddingPx).coerceAtLeast(0f),
                    right = (bounds.right - overlayBounds.left + paddingPx).coerceAtMost(overlayBounds.width),
                    bottom = (bounds.bottom - overlayBounds.top + paddingPx).coerceAtMost(overlayBounds.height)
                )
            }
        }.filter { (_, rect) -> rect.width > 0f && rect.height > 0f }
        val union = localHoles.map { it.second }.reduceOrNull { acc, rect ->
            Rect(
                left = minOf(acc.left, rect.left),
                top = minOf(acc.top, rect.top),
                right = maxOf(acc.right, rect.right),
                bottom = maxOf(acc.bottom, rect.bottom)
            )
        }
        var visibleHighlightCount by remember(step) { mutableIntStateOf(0) }
        LaunchedEffect(step) {
            visibleHighlightCount = 0
            delay(GUIDE_HIGHLIGHT_INITIAL_DELAY_MS)
            step.anchors.forEachIndexed { index, _ ->
                visibleHighlightCount = index + 1
                if (index < step.anchors.size - 1) delay(GUIDE_HIGHLIGHT_STAGGER_MS)
            }
        }
        val animatedHoles = localHoles.map { (sequenceIndex, hole) ->
            val progress by animateFloatAsState(
                targetValue = if (sequenceIndex < visibleHighlightCount) 1f else 0f,
                animationSpec = tween(
                    durationMillis = GUIDE_HIGHLIGHT_DURATION_MS,
                    easing = FastOutSlowInEasing
                ),
                label = "quick_subtitle_guide_highlight_$sequenceIndex"
            )
            hole to progress
        }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val cardWidth = minOf(420.dp, maxWidth - 32.dp)
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = cardSize.height.takeIf { it > 0 }?.toFloat() ?: with(density) { 210.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }
        val isDisplayGuideStep = QuickSubtitleGuideAnchor.SubtitleDisplay in step.anchors
        val calloutReservePx = if (QuickSubtitleGuideAnchor.BottomBar in step.anchors) {
            with(density) { 36.dp.toPx() }
        } else {
            0f
        }
        val cardHorizontalAdjustment = if (isDisplayGuideStep) {
            with(density) { (-24).dp.toPx() }
        } else {
            0f
        }
        val cardX = ((maxWidthPx - cardWidthPx) / 2f + cardHorizontalAdjustment).coerceIn(
            marginPx,
            (maxWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        )
        val baseCardY = when {
            union == null -> (maxHeightPx - cardHeightPx) / 2f
            union.bottom + gapPx + cardHeightPx <= maxHeightPx - marginPx -> union.bottom + gapPx
            union.top - gapPx - calloutReservePx - cardHeightPx >= marginPx ->
                union.top - gapPx - calloutReservePx - cardHeightPx
            else -> (maxHeightPx - cardHeightPx) / 2f
        }
        val cardVerticalAdjustment = if (isDisplayGuideStep) {
            with(density) { (-28).dp.toPx() }
        } else {
            0f
        }
        val cardY = (baseCardY + cardVerticalAdjustment).coerceIn(
            marginPx,
            (maxHeightPx - cardHeightPx - marginPx).coerceAtLeast(marginPx)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.99f }
                .onGloballyPositioned { overlayBounds = it.boundsInWindow() }
        ) {
            drawRect(Color.Black.copy(alpha = 0.74f))
            animatedHoles.forEach { (hole, progress) ->
                if (progress <= 0f) return@forEach
                val scale = 0.9f + 0.1f * progress
                val animatedWidth = hole.width * scale
                val animatedHeight = hole.height * scale
                val animatedHole = Rect(
                    left = hole.center.x - animatedWidth / 2f,
                    top = hole.center.y - animatedHeight / 2f,
                    right = hole.center.x + animatedWidth / 2f,
                    bottom = hole.center.y + animatedHeight / 2f
                )
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(animatedHole.left, animatedHole.top),
                    size = androidx.compose.ui.geometry.Size(animatedHole.width, animatedHole.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    alpha = progress,
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = primary.copy(alpha = progress),
                    topLeft = Offset(animatedHole.left, animatedHole.top),
                    size = androidx.compose.ui.geometry.Size(animatedHole.width, animatedHole.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = with(density) { 2.dp.toPx() })
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        Crossfade(
            targetState = step,
            animationSpec = tween(160),
            modifier = Modifier
                .width(cardWidth)
                .onGloballyPositioned { cardSize = it.size }
                .offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) },
            label = "quick_subtitle_guide_step"
        ) { animatedStep ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UiTokens.Radius),
                color = MaterialTheme.colorScheme.surface,
                elevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = animatedStep.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.h6
                        )
                        Text(
                            text = "${stepIndex + 1} / $stepCount",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.caption
                        )
                    }
                    animatedStep.messages.forEachIndexed { index, message ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .background(primary, CircleShape)
                            )
                            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body2)
                        }
                        if (index != animatedStep.messages.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) { Text("跳过") }
                        if (onPrevious != null) {
                            TextButton(onClick = onPrevious) { Text("上一步") }
                        }
                        TextButton(onClick = onNext) {
                            Text(if (stepIndex == stepCount - 1) "完成" else "下一步")
                        }
                    }
                }
            }
        }
        QuickSubtitleGuideCallouts(
            callouts = step.callouts,
            anchorBounds = anchorBounds,
            overlayBounds = overlayBounds,
            screenSize = IntSize(maxWidthPx.roundToInt(), maxHeightPx.roundToInt()),
            initialDelayMillis = GUIDE_HIGHLIGHT_INITIAL_DELAY_MS +
                (step.anchors.size - 1).coerceAtLeast(0) * GUIDE_HIGHLIGHT_STAGGER_MS +
                GUIDE_CALLOUT_AFTER_HIGHLIGHT_DELAY_MS,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val GUIDE_PHASE_MODE = 0
private const val GUIDE_PHASE_OVERLAY = 1
private const val GUIDE_HIGHLIGHT_INITIAL_DELAY_MS = 40L
private const val GUIDE_HIGHLIGHT_STAGGER_MS = 55L
private const val GUIDE_HIGHLIGHT_DURATION_MS = 180
private const val GUIDE_CALLOUT_AFTER_HIGHLIGHT_DELAY_MS = 80L
