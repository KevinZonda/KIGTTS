package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex

private val LedSubtitleEntryImageVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "LedSubtitle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(84f, 612f)
            verticalLineTo(348f)
            horizontalLineTo(150f)
            verticalLineTo(546f)
            horizontalLineTo(304f)
            verticalLineTo(612f)
            horizontalLineTo(84f)
            close()

            moveTo(370f, 612f)
            verticalLineTo(348f)
            horizontalLineTo(590f)
            verticalLineTo(414f)
            horizontalLineTo(436f)
            verticalLineTo(447f)
            horizontalLineTo(568f)
            verticalLineTo(513f)
            horizontalLineTo(436f)
            verticalLineTo(546f)
            horizontalLineTo(590f)
            verticalLineTo(612f)
            horizontalLineTo(370f)
            close()

            moveTo(656f, 612f)
            verticalLineTo(348f)
            horizontalLineTo(843f)
            lineTo(876f, 381f)
            verticalLineTo(579f)
            lineTo(843f, 612f)
            horizontalLineTo(656f)
            close()
            moveTo(722f, 546f)
            horizontalLineTo(810f)
            verticalLineTo(414f)
            horizontalLineTo(722f)
            verticalLineTo(546f)
            close()
        }
    }.build()
}

@Composable
internal fun LedSubtitleEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticOnClick = rememberKigttsHapticClick(onClick)
    IconButton(
        onClick = hapticOnClick,
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = LedSubtitleEntryImageVector,
            contentDescription = "LED 字幕",
            tint = LocalContentColor.current
        )
    }
}

@Composable
internal fun LedOverlayIconButton(
    icon: String,
    description: String,
    enabled: Boolean,
    alpha: Float,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        MsIcon(icon, contentDescription = description, tint = tint)
    }
}

@Composable
internal fun LedSubtitleGuideOverlay(
    visible: Boolean,
    quickSwipeEnabled: Boolean,
    adaptiveMultiLine: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(4f),
        enter = fadeIn(tween(170)),
        exit = fadeOut(tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.58f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f)
                        )
                    )
                    .padding(horizontal = 54.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    if (adaptiveMultiLine && quickSwipeEnabled) {
                        "快速左滑快捷文本 · 上滑输入文字"
                    } else if (adaptiveMultiLine) {
                        "上滑输入文字"
                    } else if (quickSwipeEnabled) {
                        "左右拖动字幕 · 快速左滑快捷文本 · 上滑输入文字"
                    } else {
                        "左右拖动字幕 · 上滑输入文字"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun LedQuickSubtitlePreviewDialog(
    text: String,
    contentColor: Color,
    backgroundColor: Color,
    viewModel: MainViewModel,
    state: UiState
) {
    Dialog(
        onDismissRequest = { viewModel.closeQuickSubtitlePreview() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable { viewModel.closeQuickSubtitlePreview() }
                .padding(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = backgroundColor,
                elevation = 12.dp
            ) {
                QuickSubtitleAdaptiveText(
                    text = AnnotatedString(text),
                    color = contentColor,
                    textAlign = if (viewModel.quickSubtitleCentered) TextAlign.Center else TextAlign.Start,
                    fontWeight = if (viewModel.quickSubtitleBold) FontWeight.Bold else FontWeight.Normal,
                    maxFontSizeSp = (viewModel.quickSubtitleFontSizeSp * 1.25f).coerceAtLeast(36f),
                    minFontSizeSp = 18f,
                    lineHeightMultiplier = 1.36f,
                    autoFitEnabled = state.quickSubtitleAutoFit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                    textRotationZ = if (viewModel.quickSubtitleRotated180) 180f else 0f
                )
            }
        }
    }
}
