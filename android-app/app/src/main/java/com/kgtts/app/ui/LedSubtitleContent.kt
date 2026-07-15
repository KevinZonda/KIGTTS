package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings

@Composable
internal fun LedSubtitleMainContent(
    subtitleText: String,
    pendingInputText: String,
    showPersistentInputPreview: Boolean,
    settings: LedSubtitleSettings,
    motionState: LedMarqueeMotionState,
    textColor: Color,
    textAlign: TextAlign,
    fontWeight: FontWeight,
    maxFontSizeSp: Float,
    autoFitEnabled: Boolean,
    rotated180: Boolean,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = showPersistentInputPreview,
        animationSpec = tween(180),
        modifier = modifier,
        label = "led_persistent_input_preview_switch"
    ) { preview ->
        if (preview) {
            QuickSubtitleAdaptiveText(
                text = AnnotatedString(pendingInputText),
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                maxFontSizeSp = maxFontSizeSp,
                minFontSizeSp = 14f,
                lineHeightMultiplier = 1.15f,
                autoFitEnabled = autoFitEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center,
                textRotationZ = if (rotated180) 180f else 0f
            )
        } else {
            LedSubtitleDisplay(
                text = subtitleText,
                settings = settings,
                motionState = motionState,
                bold = fontWeight == FontWeight.Bold,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun LedSubtitleFloatingInputPreview(
    visible: Boolean,
    text: String,
    cursorIndex: Int,
    bottomPadding: Dp,
    topPadding: Dp,
    textAlign: TextAlign,
    fontWeight: FontWeight,
    maxFontSizeSp: Float,
    autoFitEnabled: Boolean,
    rotated180: Boolean,
    modifier: Modifier = Modifier
) {
    QuickSubtitleFloatingInputPreviewOverlay(
        preview = if (visible) {
            QuickSubtitleFloatingInputPreviewState(
                text = AnnotatedString(text),
                cursorIndex = cursorIndex.coerceIn(0, text.length),
                bottomPadding = bottomPadding
            )
        } else {
            null
        },
        textAlign = textAlign,
        fontWeight = fontWeight,
        maxFontSizeSp = maxFontSizeSp,
        autoFitEnabled = autoFitEnabled,
        rotated180 = rotated180,
        startPadding = 64.dp,
        endPadding = 64.dp,
        topPadding = topPadding,
        modifier = modifier
    )
}
