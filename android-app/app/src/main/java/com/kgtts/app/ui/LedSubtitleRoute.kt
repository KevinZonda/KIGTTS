package com.lhtstudio.kigtts.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import com.lhtstudio.kigtts.app.overlay.OverlayTypefaceLoader
import com.lhtstudio.kigtts.app.overlay.OverlayTypefaceRequest
import com.lhtstudio.kigtts.app.overlay.OverlayTypefaces

@Composable
internal fun LedSubtitleScreen(
    viewModel: MainViewModel,
    state: UiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = state.ledSubtitleSettings
    val appTypefaces by produceState<OverlayTypefaces?>(
        initialValue = null,
        context,
        state.appFontId,
        state.appFontWeight,
        state.appFontFamilySource
    ) {
        value = runCatching {
            OverlayTypefaceLoader.load(
                context = context,
                request = OverlayTypefaceRequest(
                    useSystemFont = false,
                    appFontId = state.appFontId,
                    preferredWeight = state.appFontWeight
                )
            )
        }.getOrNull()
    }
    val subtitleTypeface = if (viewModel.quickSubtitleBold) {
        appTypefaces?.bold ?: Typeface.DEFAULT_BOLD
    } else {
        appTypefaces?.regular ?: Typeface.DEFAULT
    }
    val backgroundColor by animateColorAsState(
        targetValue = Color(settings.backgroundColorArgb),
        animationSpec = tween(180),
        label = "led_background"
    )
    val accentColor = MaterialTheme.colorScheme.accentText
    val contentColor = if (backgroundColor.luminance() > 0.48f) Color.Black else Color.White
    LedSubtitleScreenContent(
        viewModel = viewModel,
        state = state,
        settings = settings,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        accentColor = accentColor,
        subtitleTypeface = subtitleTypeface,
        onBack = onBack
    )
}

@Composable
internal fun LedSubtitleWindowSettingsEffect(settings: LedSubtitleSettings) {
    val activity = LocalContext.current as? Activity
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    val originalKeepScreenOn = remember(activity) {
        activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    }
    val originalStatusBarColor = remember(activity) { activity?.window?.statusBarColor }
    val originalNavigationBarColor = remember(activity) { activity?.window?.navigationBarColor }
    val originalCutoutMode = remember(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity?.window?.attributes?.layoutInDisplayCutoutMode
        } else {
            null
        }
    }
    DisposableEffect(
        activity,
        settings.keepScreenOn,
        settings.followSystemBrightness,
        settings.screenBrightness,
        settings.backgroundColorArgb
    ) {
        val window = activity?.window
        if (window != null) {
            if (settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val attributes = window.attributes
            attributes.screenBrightness = if (settings.followSystemBrightness) -1f else settings.screenBrightness
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.attributes = attributes
            window.statusBarColor = settings.backgroundColorArgb
            window.navigationBarColor = settings.backgroundColorArgb
        }
        onDispose {
            if (window != null) {
                if (originalKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val attributes = window.attributes
                attributes.screenBrightness = originalBrightness
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                }
                window.attributes = attributes
                originalStatusBarColor?.let { window.statusBarColor = it }
                originalNavigationBarColor?.let { window.navigationBarColor = it }
            }
        }
    }
}

@Composable
internal fun LedSubtitleOrientationLockEffect(locked: Boolean) {
    val activity = LocalContext.current as? Activity
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity, locked) {
        if (activity != null) {
            activity.requestedOrientation = if (locked) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                originalOrientation
            }
        }
        onDispose {
            if (locked && activity != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}
