package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenuItem as M2DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import com.lhtstudio.kigtts.app.data.ListeningModeSettings
import com.lhtstudio.kigtts.app.overlay.ListeningCaptionItem
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ListeningAutoScrollTarget(
    val followingNewest: Boolean,
    val totalItems: Int,
    val lastVisibleIndex: Int,
    val remainingBottomPx: Int
)

@Composable
internal fun ListeningCaptionPanel(
    items: List<ListeningCaptionItem>,
    streamingText: String,
    settings: ListeningModeSettings,
    isLandscape: Boolean,
    onSettingsChange: ((ListeningModeSettings) -> ListeningModeSettings) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeChangeFinished: () -> Unit,
    onSwapPanels: () -> Unit,
    controlsVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val displayItems = remember(items, streamingText) {
        buildList {
            addAll(items)
            streamingText.trim().takeIf { it.isNotEmpty() }?.let { live ->
                add(ListeningCaptionItem(Long.MAX_VALUE, live))
            }
        }
    }
    var followingNewest by remember { mutableStateOf(true) }
    var automaticScrollInProgress by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var appliedRotated180 by remember { mutableStateOf(settings.rotated180) }
    val rotationFade = remember { Animatable(1f) }
    val fadeContainerColor = md2ElevatedCardContainerColor()
    val bottomSnapThresholdPx = with(LocalDensity.current) {
        settings.fontSizeSp.sp.toPx() * 0.6f
    }
    val topFadeAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(150),
        label = "listening_caption_top_fade"
    )
    val bottomFadeAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollForward) 1f else 0f,
        animationSpec = tween(150),
        label = "listening_caption_bottom_fade"
    )

    LaunchedEffect(settings.rotated180) {
        if (appliedRotated180 != settings.rotated180) {
            rotationFade.animateTo(0f, tween(90))
            appliedRotated180 = settings.rotated180
            rotationFade.animateTo(1f, tween(120))
        }
    }

    LaunchedEffect(listState, displayItems.size, bottomSnapThresholdPx) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.lastOrNull()
            val total = layoutInfo.totalItemsCount
            val remainingPx = if (last != null && last.index == total - 1) {
                (last.offset + last.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
            } else {
                Int.MAX_VALUE
            }
            Pair(
                Triple(
                    listState.isScrollInProgress,
                    total <= 1 || remainingPx <= bottomSnapThresholdPx,
                    remainingPx
                ),
                automaticScrollInProgress
            )
        }.distinctUntilChanged().collect { (scrollStateSnapshot, automaticScroll) ->
            val (scrolling, nearBottom, remainingPx) = scrollStateSnapshot
            if (scrolling && !nearBottom && !automaticScroll) followingNewest = false
            if (!scrolling && nearBottom && displayItems.isNotEmpty()) {
                followingNewest = true
                if (remainingPx in 1 until Int.MAX_VALUE) {
                    listState.animateScrollBy(remainingPx.toFloat())
                }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.lastOrNull()
            val total = layoutInfo.totalItemsCount
            ListeningAutoScrollTarget(
                followingNewest = followingNewest,
                totalItems = total,
                lastVisibleIndex = last?.index ?: -1,
                remainingBottomPx = if (last != null && last.index == total - 1) {
                    (last.offset + last.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
                } else {
                    Int.MAX_VALUE
                }
            )
        }.distinctUntilChanged().conflate().collect { target ->
            if (!target.followingNewest || target.totalItems == 0) return@collect
            automaticScrollInProgress = true
            try {
                val lastIndex = target.totalItems - 1
                if (target.lastVisibleIndex != lastIndex) {
                    listState.animateScrollToItem(lastIndex)
                }
                // Wait for streaming text to be remeasured before reading the final line height.
                withFrameNanos { }
                val layoutInfo = listState.layoutInfo
                val last = layoutInfo.visibleItemsInfo.lastOrNull()
                if (last?.index == lastIndex) {
                    val remainingPx =
                        (last.offset + last.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
                    if (remainingPx > 0) listState.animateScrollBy(remainingPx.toFloat())
                }
            } finally {
                automaticScrollInProgress = false
            }
        }
    }

    val captionArea: @Composable (Modifier) -> Unit = { areaModifier ->
        Box(
            modifier = areaModifier
                .quickSubtitlePinchZoom(
                    enabled = true,
                    fontSizeSp = settings.fontSizeSp,
                    minFontSizeSp = ListeningModeSettings.MIN_FONT_SIZE_SP,
                    maxFontSizeSp = ListeningModeSettings.MAX_FONT_SIZE_SP,
                    onFontSizeChange = onFontSizeChange,
                    onFontSizeChangeFinished = onFontSizeChangeFinished
                )
                .clickable(onClick = { previewVisible = true })
                .graphicsLayer {
                    alpha = rotationFade.value
                    rotationZ = if (appliedRotated180) 180f else 0f
                }
        ) {
            if (displayItems.isEmpty()) {
                Text(
                    text = "正在聆听周围的声音…",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                        val distanceFromNewest = displayItems.lastIndex - index
                        val targetAlpha = if (!followingNewest) {
                            1f
                        } else {
                            when (distanceFromNewest) {
                                0 -> 1f
                                1 -> 0.5f
                                else -> 0.25f
                            }
                        }
                        val alpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(180),
                            label = "listening_caption_history_alpha_$index"
                        )
                        AnimatedContent(
                            targetState = item.text,
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(tween(130)),
                                    initialContentExit = fadeOut(tween(100)),
                                    sizeTransform = null
                                )
                            },
                            label = "listening_caption_text"
                        ) { text ->
                            Text(
                                text = text,
                                color = MaterialTheme.colors.onSurface.copy(alpha = alpha),
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = (settings.fontSizeSp * 1.18f).sp,
                                fontWeight = if (index == displayItems.lastIndex) {
                                    FontWeight.Medium
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = topFadeAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(fadeContainerColor, fadeContainerColor.copy(alpha = 0f))
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = bottomFadeAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(fadeContainerColor.copy(alpha = 0f), fadeContainerColor)
                            )
                        )
                )
            }
        }
    }

    val returnToNewest = {
        followingNewest = true
        if (displayItems.isNotEmpty()) {
            scope.launch {
                automaticScrollInProgress = true
                try {
                    listState.animateScrollToItem(displayItems.lastIndex)
                    val layoutInfo = listState.layoutInfo
                    val last = layoutInfo.visibleItemsInfo.lastOrNull()
                    if (last?.index == displayItems.lastIndex) {
                        val remainingPx =
                            (last.offset + last.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
                        if (remainingPx > 0) listState.animateScrollBy(remainingPx.toFloat())
                    }
                } finally {
                    automaticScrollInProgress = false
                }
            }
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                captionArea(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(140)) + androidx.compose.animation.expandHorizontally(tween(180)),
                    exit = fadeOut(tween(110)) + androidx.compose.animation.shrinkHorizontally(tween(150))
                ) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        ListeningCaptionControls(
                            settings = settings,
                            vertical = true,
                            onSettingsChange = onSettingsChange,
                            onFontSizeChange = onFontSizeChange,
                            onFontSizeChangeFinished = onFontSizeChangeFinished,
                            onSwapPanels = onSwapPanels,
                            onReturnToNewest = returnToNewest,
                            modifier = Modifier
                                .width(40.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                captionArea(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(140)) + androidx.compose.animation.expandVertically(tween(180)),
                    exit = fadeOut(tween(110)) + androidx.compose.animation.shrinkVertically(tween(150))
                ) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        ListeningCaptionControls(
                            settings = settings,
                            vertical = false,
                            onSettingsChange = onSettingsChange,
                            onFontSizeChange = onFontSizeChange,
                            onFontSizeChangeFinished = onFontSizeChangeFinished,
                            onSwapPanels = onSwapPanels,
                            onReturnToNewest = returnToNewest,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (previewVisible) {
        ListeningCaptionPreviewDialog(
            items = displayItems,
            fontSizeSp = settings.fontSizeSp,
            onFontSizeChange = onFontSizeChange,
            onFontSizeChangeFinished = onFontSizeChangeFinished,
            onDismiss = { previewVisible = false }
        )
    }
}

@Composable
private fun ListeningCaptionControls(
    settings: ListeningModeSettings,
    vertical: Boolean,
    onSettingsChange: ((ListeningModeSettings) -> ListeningModeSettings) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeChangeFinished: () -> Unit,
    onSwapPanels: () -> Unit,
    onReturnToNewest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActionButtons by rememberSaveable { mutableStateOf(true) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var inputMenuExpanded by remember { mutableStateOf(false) }
    val actionScrollState = rememberScrollState()
    val toolbarBackground = md2ElevatedCardContainerColor(UiTokens.CardElevation)

    val actionButtons: @Composable () -> Unit = {
        Md2IconButton(
            icon = "swap_vert",
            contentDescription = if (settings.rotated180) {
                "恢复聆听字幕方向"
            } else {
                "倒置聆听字幕"
            },
            onClick = { onSettingsChange { it.copy(rotated180 = !it.rotated180) } }
        )
        Md2IconButton(
            icon = if (vertical) "swap_horizontal_circle" else "swap_vertical_circle",
            contentDescription = if (vertical) {
                "左右互换聆听字幕与大字幕"
            } else {
                "上下互换聆听字幕与大字幕"
            },
            onClick = onSwapPanels
        )
        Md2IconButton(
            icon = "vertical_align_bottom",
            contentDescription = "回到最新字幕",
            onClick = onReturnToNewest
        )
        Box {
            Md2IconButton(
                icon = "translate",
                contentDescription = "设置聆听语言",
                onClick = { languageMenuExpanded = true }
            )
            Md2AnimatedOptionMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false }
            ) {
                AsrRecognitionLanguage.entries.forEach { language ->
                    M2DropdownMenuItem(
                        onClick = {
                            languageMenuExpanded = false
                            onSettingsChange { it.copy(recognitionLanguage = language) }
                        }
                    ) {
                        MsIcon("translate", contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(AsrRecognitionLanguage.label(language))
                    }
                }
            }
        }
        Box {
            Md2IconButton(
                icon = "mic_gear",
                contentDescription = "设置聆听音频设备",
                onClick = { inputMenuExpanded = true }
            )
            Md2AnimatedOptionMenu(
                expanded = inputMenuExpanded,
                onDismissRequest = { inputMenuExpanded = false }
            ) {
                listeningInputOptions.forEach { (type, label) ->
                    M2DropdownMenuItem(
                        onClick = {
                            inputMenuExpanded = false
                            onSettingsChange { it.copy(preferredInputType = type) }
                        }
                    ) {
                        MsIcon("mic", contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        }
    }
    val toggleIcon = if (showActionButtons) {
        "search"
    } else if (vertical) {
        "more_vert"
    } else {
        "more_horiz"
    }
    val toggleDescription = if (showActionButtons) {
        "切换到聆听字幕字号"
    } else {
        "切换到聆听操作"
    }

    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = showActionButtons,
                    animationSpec = tween(180),
                    label = "listening_caption_controls_landscape"
                ) { showActions ->
                    if (showActions) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .animatedToolbarEdgeFade(
                                    scrollState = actionScrollState,
                                    vertical = true,
                                    backgroundColor = toolbarBackground
                                )
                                .verticalScroll(actionScrollState)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            actionButtons()
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MsIcon("search", contentDescription = "聆听字幕字号")
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Md2VerticalSlider(
                                    value = settings.fontSizeSp,
                                    onValueChange = onFontSizeChange,
                                    onValueChangeFinished = onFontSizeChangeFinished,
                                    valueRange = ListeningModeSettings.MIN_FONT_SIZE_SP..
                                        ListeningModeSettings.MAX_FONT_SIZE_SP,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(28.dp)
                                )
                            }
                        }
                    }
                }
            }
            Md2IconButton(
                icon = toggleIcon,
                contentDescription = toggleDescription,
                onClick = { showActionButtons = !showActionButtons }
            )
        }
    } else {
        Box(modifier = modifier.height(48.dp)) {
            Crossfade(
                targetState = showActionButtons,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(180),
                label = "listening_caption_controls_portrait"
            ) { showActions ->
                if (showActions) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 44.dp)
                                .offset(y = (-6).dp)
                                .animatedToolbarEdgeFade(
                                    scrollState = actionScrollState,
                                    vertical = false,
                                    backgroundColor = toolbarBackground
                                )
                                .horizontalScroll(actionScrollState),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            actionButtons()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 44.dp)
                                .offset(y = (-6).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MsIcon("search", contentDescription = "聆听字幕字号")
                            Slider(
                                value = settings.fontSizeSp,
                                onValueChange = onFontSizeChange,
                                onValueChangeFinished = onFontSizeChangeFinished,
                                valueRange = ListeningModeSettings.MIN_FONT_SIZE_SP..
                                    ListeningModeSettings.MAX_FONT_SIZE_SP,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            )
                        }
                    }
                }
            }
            Md2IconButton(
                icon = toggleIcon,
                contentDescription = toggleDescription,
                onClick = { showActionButtons = !showActionButtons },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = (-6).dp)
            )
        }
    }
}
