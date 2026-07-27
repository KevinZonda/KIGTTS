package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val LedPanelBackground = Color(0xF21A1B1E)
private val LedPanelContent = Color(0xFFF5F5F5)
private val LedPanelSecondary = Color(0xFFB8BBC2)
private val LedPanelOutline = Color(0xFF5D616A)

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun LedSubtitleInputPanel(
    value: TextFieldValue,
    playOnSend: Boolean,
    accentColor: Color,
    onContentHeightChanged: (Int) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onTogglePlayOnSend: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(90)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = LedPanelBackground,
        contentColor = LedPanelContent,
        elevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .onSizeChanged { onContentHeightChanged(it.height) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LedPanelIconButton(
                icon = "arrow_back",
                description = "光标左移",
                onClick = {
                    val current = value.selection.start.coerceIn(0, value.text.length)
                    onValueChange(value.copy(selection = TextRange((current - 1).coerceAtLeast(0))))
                }
            )
            LedPanelIconButton(
                icon = "arrow_forward",
                description = "光标右移",
                onClick = {
                    val current = value.selection.end.coerceIn(0, value.text.length)
                    onValueChange(value.copy(selection = TextRange((current + 1).coerceAtMost(value.text.length))))
                }
            )
            LedPanelIconButton(
                icon = if (playOnSend) "volume_up" else "volume_off",
                description = if (playOnSend) "发送时播放语音：开" else "发送时播放语音：关",
                onClick = onTogglePlayOnSend
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .kigttsTextToolbarAnchor(),
                singleLine = true,
                placeholder = { Text("请输入文本", color = LedPanelSecondary) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }, onDone = { onSubmit() }),
                trailingIcon = {
                    if (value.text.isNotEmpty()) {
                        LedPanelIconButton(
                            icon = "close",
                            description = "清空输入",
                            onClick = { onValueChange(TextFieldValue("")) }
                        )
                    }
                },
                shape = RoundedCornerShape(UiTokens.Radius),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = LedPanelContent,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = LedPanelOutline,
                    cursorColor = accentColor,
                    trailingIconColor = LedPanelContent
                )
            )
            LedPanelIconButton(
                icon = "send",
                description = "发送到朗读队列",
                enabled = value.text.trim().isNotEmpty(),
                onClick = onSubmit
            )
        }
    }
}

@Composable
internal fun LedSubtitleQuickTextPanel(
    viewModel: MainViewModel,
    state: UiState,
    accentColor: Color,
    panelWidth: Dp,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = viewModel.quickSubtitleGroups
    val selectedIndex = viewModel.currentQuickSubtitleGroupIndex()
        .coerceIn(0, groups.lastIndex.coerceAtLeast(0))
    val hasVoice = state.voiceDir != null
    val groupRailState = rememberLazyListState()
    LaunchedEffect(selectedIndex, groups.size) {
        if (groups.isNotEmpty()) groupRailState.animateScrollToItem(selectedIndex)
    }

    Surface(
        modifier = modifier
            .width(panelWidth)
            .widthIn(min = 220.dp, max = 460.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = UiTokens.Radius, bottomStart = UiTokens.Radius),
        color = LedPanelBackground,
        contentColor = LedPanelContent,
        elevation = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LedPanelIconButton(
                    icon = "chevron_left",
                    description = "上一分组",
                    onClick = {
                        if (groups.isNotEmpty()) {
                            onInteraction()
                            viewModel.selectQuickSubtitleGroup(
                                if (selectedIndex > 0) selectedIndex - 1 else groups.lastIndex
                            )
                        }
                    }
                )
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = { ledGroupSwitchTransform() },
                    modifier = Modifier.weight(1f),
                    label = "led_quick_text_group_title"
                ) { index ->
                    Text(
                        text = groups.getOrNull(index)?.title?.ifBlank { "未命名分组" }.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LedPanelIconButton(
                    icon = "chevron_right",
                    description = "下一分组",
                    onClick = {
                        if (groups.isNotEmpty()) {
                            onInteraction()
                            viewModel.selectQuickSubtitleGroup(
                                if (selectedIndex < groups.lastIndex) selectedIndex + 1 else 0
                            )
                        }
                    }
                )
            }
            Divider(color = LedPanelOutline.copy(alpha = 0.72f))
            Row(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = { ledGroupSwitchTransform() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "led_quick_text_group_content"
                ) { groupIndex ->
                    val group = groups.getOrNull(groupIndex)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(group?.items.orEmpty()) { itemIndex, text ->
                            val hapticSubmit = rememberKigttsHapticClick {
                                onInteraction()
                                viewModel.submitQuickSubtitlePreset(
                                    text = text,
                                    hasVoice = hasVoice,
                                    interruptCurrent = state.quickSubtitleInterruptQueue
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clickable(onClick = hapticSubmit)
                                    .quickSubtitleItemColorMarker(
                                        group?.itemColorArgb(itemIndex),
                                        QuickSubtitleItemColorEdge.Left
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Divider(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                color = LedPanelOutline.copy(alpha = 0.4f)
                            )
                        }
                        item {
                            val hapticAdd = rememberKigttsHapticClick {
                                onInteraction()
                                viewModel.addQuickSubtitleItem(
                                    groupIndex = groupIndex,
                                    value = viewModel.quickSubtitleCurrentText
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable(onClick = hapticAdd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                MsIcon("add", contentDescription = "添加当前文本", tint = LedPanelSecondary)
                            }
                        }
                    }
                }
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = LedPanelOutline.copy(alpha = 0.72f)
                )
                LazyColumn(
                    state = groupRailState,
                    modifier = Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                ) {
                    itemsIndexed(groups) { index, group ->
                        val hapticSelectGroup = rememberKigttsHapticClick {
                            onInteraction()
                            viewModel.selectQuickSubtitleGroup(index)
                        }
                        val selected = index == selectedIndex
                        val selectionBackground by animateColorAsState(
                            targetValue = if (selected) {
                                accentColor.copy(alpha = 0.22f)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(180),
                            label = "led_group_selection_background"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) accentColor else LedPanelSecondary,
                            animationSpec = tween(180),
                            label = "led_group_selection_icon"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(selectionBackground)
                                .clickable(onClick = hapticSelectGroup),
                            contentAlignment = Alignment.Center
                        ) {
                            MsIcon(
                                group.icon,
                                contentDescription = group.title.ifBlank { "未命名分组" },
                                tint = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<Int>.ledGroupSwitchTransform(): ContentTransform {
    val direction = if (targetState >= initialState) 1 else -1
    return ContentTransform(
        targetContentEnter = fadeIn(animationSpec = tween(160)) +
            slideInHorizontally(
                initialOffsetX = { width -> direction * width / 9 },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
        initialContentExit = fadeOut(animationSpec = tween(120)) +
            slideOutHorizontally(
                targetOffsetX = { width -> -direction * width / 11 },
                animationSpec = tween(170, easing = FastOutSlowInEasing)
            ),
        sizeTransform = null
    )
}

@Composable
private fun LedPanelIconButton(
    icon: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    KigttsIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp)
    ) {
        MsIcon(
            icon,
            contentDescription = description,
            tint = if (enabled) LedPanelContent else LedPanelSecondary.copy(alpha = 0.38f)
        )
    }
}
