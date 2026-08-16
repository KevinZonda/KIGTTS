package com.lhtstudio.kigtts.app.ui

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import kotlinx.coroutines.delay

private enum class LedSubtitlePanel { None, Input, QuickText, Settings }

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun LedSubtitleScreenContent(
    viewModel: MainViewModel,
    state: UiState,
    settings: LedSubtitleSettings,
    backgroundColor: Color,
    contentColor: Color,
    accentColor: Color,
    subtitleTypeface: Typeface,
    onBack: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val performKeyHaptic = rememberKigttsKeyHaptic()
    val marqueeMotionState = rememberLedMarqueeMotionState(settings)
    var activePanel by rememberSaveable { mutableStateOf(LedSubtitlePanel.None) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(true) }
    var controlsDimmed by remember { mutableStateOf(false) }
    var interactionSerial by remember { mutableIntStateOf(0) }
    var inputBarHeightPx by remember { mutableIntStateOf(0) }
    var inputFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = viewModel.quickSubtitleInputText,
                selection = TextRange(viewModel.quickSubtitleInputText.length)
            )
        )
    }
    var observedContentRevision by remember { mutableLongStateOf(viewModel.quickSubtitleContentRevision) }
    var inputPreviewBlockedRevision by remember { mutableLongStateOf(Long.MIN_VALUE) }

    fun registerInteraction(dismissGuide: Boolean = true) {
        interactionSerial += 1
        controlsDimmed = false
        if (dismissGuide) showGuide = false
    }

    fun closePanel() {
        activePanel = LedSubtitlePanel.None
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun openPanel(panel: LedSubtitlePanel) {
        if (locked) return
        registerInteraction()
        if (activePanel == panel) {
            closePanel()
        } else {
            if (panel != LedSubtitlePanel.Input) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            activePanel = panel
        }
    }

    LedSubtitleWindowSettingsEffect(settings)
    LedSubtitleOrientationLockEffect(locked)

    LaunchedEffect(interactionSerial, locked) {
        delay(3_000)
        controlsDimmed = true
    }
    LaunchedEffect(Unit) {
        delay(3_000)
        showGuide = false
    }
    LaunchedEffect(viewModel.quickSubtitleInputText) {
        if (viewModel.quickSubtitleInputText != inputFieldValue.text) {
            inputFieldValue = TextFieldValue(
                text = viewModel.quickSubtitleInputText,
                selection = TextRange(viewModel.quickSubtitleInputText.length)
            )
        }
    }
    LaunchedEffect(viewModel.quickSubtitleContentRevision) {
        val revision = viewModel.quickSubtitleContentRevision
        if (revision != observedContentRevision) {
            observedContentRevision = revision
            if (inputFieldValue.text.isNotEmpty()) inputPreviewBlockedRevision = revision
        }
    }
    LaunchedEffect(inputFieldValue.text) {
        if (inputFieldValue.text.isNotEmpty()) inputPreviewBlockedRevision = Long.MIN_VALUE
    }
    LaunchedEffect(activePanel) {
        if (activePanel != LedSubtitlePanel.Input) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    BackHandler {
        when {
            locked -> Unit
            activePanel != LedSubtitlePanel.None -> {
                registerInteraction()
                closePanel()
            }
            else -> onBack()
        }
    }

    val previewAllowed = inputFieldValue.text.isNotEmpty() &&
        inputPreviewBlockedRevision != viewModel.quickSubtitleContentRevision
    val moveInputPreviewCursor: (Int) -> Unit = { delta ->
        val currentIndex = inputFieldValue.selection.start.coerceIn(
            0,
            inputFieldValue.text.length
        )
        val targetIndex = resolveCursorIndexAfterSwipe(
            currentIndex = currentIndex,
            textLength = inputFieldValue.text.length,
            delta = delta
        )
        if (
            targetIndex != currentIndex ||
            inputFieldValue.selection.start != inputFieldValue.selection.end
        ) {
            inputFieldValue = inputFieldValue.copy(selection = TextRange(targetIndex))
        }
    }
    val editingInputPreviewActive = previewAllowed && activePanel == LedSubtitlePanel.Input
    val persistentInputPreviewActive = previewAllowed &&
        activePanel != LedSubtitlePanel.Input &&
        state.quickSubtitleKeepInputPreview
    val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val inputBarHeight = with(density) { inputBarHeightPx.toDp() }
    val floatingPreviewTopPadding =
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding() + 12.dp
    val controlAlpha by animateFloatAsState(
        targetValue = if (controlsDimmed) 0.38f else 1f,
        animationSpec = tween(240),
        label = "led_controls_alpha"
    )
    val gestureThresholdPx = with(density) { 54.dp.toPx() }
    val quickSwipeDistanceThresholdPx = with(density) { 42.dp.toPx() }
    val quickSwipeVelocityThresholdPxPerSecond = density.density * 1_100f
    val quickSwipeReleaseThresholdMillis = 420L
    val screenWidthDp = configuration.screenWidthDp.dp
    val sidePanelWidth = if (configuration.screenWidthDp > configuration.screenHeightDp) {
        (screenWidthDp * 0.40f).coerceIn(280.dp, 460.dp)
    } else {
        (screenWidthDp * 0.9f).coerceAtMost(460.dp)
    }
    val subtitleFontSizeMax = if (state.quickSubtitleAllowLargeFont) 800f else 96f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .quickSubtitlePinchZoom(
                    enabled = !locked,
                    fontSizeSp = viewModel.quickSubtitleFontSizeSp,
                    minFontSizeSp = 28f,
                    maxFontSizeSp = subtitleFontSizeMax,
                    onFontSizeChange = viewModel::updateQuickSubtitleFontSize,
                    onFontSizeChangeFinished = viewModel::commitQuickSubtitleFontSize
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!locked) {
                        registerInteraction()
                        if (activePanel != LedSubtitlePanel.None) closePanel()
                    }
                }
                .ledSubtitleDragGestures(
                    enabled = !locked && activePanel == LedSubtitlePanel.None,
                    motionState = marqueeMotionState,
                    verticalOpenThresholdPx = gestureThresholdPx,
                    quickSwipeEnabled = settings.quickSwipeOpensQuickText,
                    quickSwipeDistanceThresholdPx = quickSwipeDistanceThresholdPx,
                    quickSwipeVelocityThresholdPxPerSecond =
                        quickSwipeVelocityThresholdPxPerSecond,
                    quickSwipeReleaseThresholdMillis = quickSwipeReleaseThresholdMillis,
                    onInteraction = { registerInteraction() },
                    onOpenInput = { openPanel(LedSubtitlePanel.Input) },
                    onOpenQuickText = { openPanel(LedSubtitlePanel.QuickText) }
                )
        ) {
            LedSubtitleMainContent(
                subtitleText = viewModel.quickSubtitleCurrentText,
                pendingInputText = inputFieldValue.text,
                showPersistentInputPreview = persistentInputPreviewActive,
                settings = settings,
                motionState = marqueeMotionState,
                textColor = contentColor,
                textAlign = if (viewModel.quickSubtitleCentered) TextAlign.Center else TextAlign.Start,
                fontWeight = if (viewModel.quickSubtitleBold) FontWeight.Bold else FontWeight.Normal,
                typeface = subtitleTypeface,
                maxFontSizeSp = viewModel.quickSubtitleFontSizeSp,
                autoFitEnabled = state.quickSubtitleAutoFit,
                rotated180 = viewModel.quickSubtitleRotated180,
                modifier = Modifier.fillMaxSize()
            )
        }

        val noIndication = remember { MutableInteractionSource() }
        AnimatedVisibility(
            visible = activePanel != LedSubtitlePanel.None && !locked,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(interactionSource = noIndication, indication = null) {
                        registerInteraction()
                        closePanel()
                    }
            )
        }

        LedSubtitleFloatingInputPreview(
            visible = editingInputPreviewActive && !locked,
            text = inputFieldValue.text,
            cursorIndex = inputFieldValue.selection.start,
            bottomPadding = imeBottomInset + inputBarHeight + 8.dp,
            topPadding = floatingPreviewTopPadding,
            textAlign = if (viewModel.quickSubtitleCentered) TextAlign.Center else TextAlign.Start,
            fontWeight = if (viewModel.quickSubtitleBold) FontWeight.Bold else FontWeight.Normal,
            maxFontSizeSp = viewModel.quickSubtitleFontSizeSp,
            maxAllowedFontSizeSp = subtitleFontSizeMax,
            autoFitEnabled = state.quickSubtitleAutoFit,
            rotated180 = viewModel.quickSubtitleRotated180,
            onFontSizeChange = viewModel::updateQuickSubtitleFontSize,
            onFontSizeChangeFinished = viewModel::commitQuickSubtitleFontSize,
            onCursorDelta = moveInputPreviewCursor,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.5f)
        )

        AnimatedVisibility(
            visible = activePanel == LedSubtitlePanel.Input && !locked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                )
                .zIndex(3f),
            enter = fadeIn(tween(150)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(100)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        ) {
            LedSubtitleInputPanel(
                value = inputFieldValue,
                playOnSend = viewModel.quickSubtitlePlayOnSend,
                accentColor = accentColor,
                onContentHeightChanged = { inputBarHeightPx = it },
                onValueChange = { next ->
                    registerInteraction()
                    inputFieldValue = next
                    viewModel.updateQuickSubtitleInputText(next.text)
                },
                onTogglePlayOnSend = {
                    registerInteraction()
                    viewModel.updateQuickSubtitlePlayOnSend(!viewModel.quickSubtitlePlayOnSend)
                },
                onSubmit = {
                    if (inputFieldValue.text.trim().isNotEmpty()) {
                        registerInteraction()
                        viewModel.submitQuickSubtitleInput(
                            playVoice = viewModel.quickSubtitlePlayOnSend && state.voiceDir != null
                        )
                        inputFieldValue = TextFieldValue("")
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = activePanel == LedSubtitlePanel.QuickText && !locked,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .zIndex(3f),
            enter = fadeIn(tween(150)) + slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(100)) + slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        ) {
            LedSubtitleQuickTextPanel(
                viewModel = viewModel,
                state = state,
                accentColor = accentColor,
                panelWidth = sidePanelWidth,
                onInteraction = { registerInteraction() }
            )
        }

        AnimatedVisibility(
            visible = activePanel == LedSubtitlePanel.Settings && !locked,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .zIndex(3f),
            enter = fadeIn(tween(150)) + slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(100)) + slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        ) {
            LedSubtitleSettingsPanel(
                settings = settings,
                accentColor = accentColor,
                panelWidth = sidePanelWidth,
                onSettingsChange = {
                    registerInteraction()
                    viewModel.updateLedSubtitleSettings(it)
                },
                onReset = {
                    registerInteraction()
                    viewModel.resetLedSubtitleSettings()
                },
                onClose = {
                    registerInteraction()
                    closePanel()
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .zIndex(1f)
        ) {
            AnimatedVisibility(
                visible = !locked,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Box(Modifier.fillMaxSize()) {
                    LedOverlayIconButton(
                        icon = "arrow_back",
                        description = "返回便捷字幕",
                        enabled = !locked,
                        alpha = controlAlpha,
                        tint = contentColor,
                        onClick = {
                            registerInteraction()
                            onBack()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                    LedOverlayIconButton(
                        icon = "settings",
                        description = "LED 设置",
                        enabled = !locked,
                        alpha = controlAlpha,
                        tint = contentColor,
                        onClick = { openPanel(LedSubtitlePanel.Settings) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        LedOverlayIconButton(
                            icon = "subtitles",
                            description = "快捷文本",
                            enabled = !locked,
                            alpha = controlAlpha,
                            tint = contentColor,
                            onClick = { openPanel(LedSubtitlePanel.QuickText) }
                        )
                        LedOverlayIconButton(
                            icon = "keyboard",
                            description = "输入文本",
                            enabled = !locked,
                            alpha = controlAlpha,
                            tint = contentColor,
                            onClick = { openPanel(LedSubtitlePanel.Input) }
                        )
                    }
                }
            }
            LedOverlayIconButton(
                icon = if (locked) "lock_open" else "lock",
                description = if (locked) "解锁 LED 屏幕" else "锁定 LED 屏幕",
                enabled = true,
                alpha = controlAlpha,
                tint = contentColor,
                onClick = {
                    performKeyHaptic()
                    if (locked) {
                        locked = false
                        registerInteraction()
                    } else {
                        closePanel()
                        viewModel.closeQuickSubtitlePreview()
                        showGuide = false
                        locked = true
                        interactionSerial += 1
                        controlsDimmed = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }

        LedSubtitleGuideOverlay(
            visible = showGuide && !locked && activePanel == LedSubtitlePanel.None,
            quickSwipeEnabled = settings.quickSwipeOpensQuickText,
            adaptiveMultiLine = settings.adaptiveMultiLine,
            onDismiss = {
                registerInteraction()
                showGuide = false
            }
        )

        if (viewModel.quickSubtitlePreviewVisible && !locked) {
            LedQuickSubtitlePreviewDialog(
                text = viewModel.quickSubtitleCurrentText,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                viewModel = viewModel,
                state = state
            )
        }
    }
}
