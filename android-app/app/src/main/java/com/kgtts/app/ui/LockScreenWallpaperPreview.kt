package com.lhtstudio.kigtts.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhtstudio.kigtts.app.data.AppFontDefaults
import com.lhtstudio.kigtts.app.data.AppFontRepository
import com.lhtstudio.kigtts.app.data.LockScreenBatteryStatus
import com.lhtstudio.kigtts.app.data.LockScreenScrimStyle
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import com.lhtstudio.kigtts.app.data.LockScreenWallpaperStore
import com.lhtstudio.kigtts.app.data.formatBatteryStatus
import com.lhtstudio.kigtts.app.data.shouldShowBatteryStatus
import com.lhtstudio.kigtts.app.overlay.LockScreenBatteryMonitor
import com.lhtstudio.kigtts.app.overlay.LockScreenDateFormatter
import com.lhtstudio.kigtts.app.overlay.LockScreenWallpaperAppearance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun LockScreenWallpaperPreview(
    settings: LockScreenSettings,
    wallpaperRevision: Long
) {
    val context = LocalContext.current
    var landscape by rememberSaveable { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        settings.wallpaperPath,
        wallpaperRevision,
        landscape
    ) {
        value = LockScreenWallpaperStore.loadForDisplay(
            settings.wallpaperPath,
            if (landscape) 1280 else 720,
            if (landscape) 720 else 1280
        )
    }
    val now by produceState(initialValue = Date()) {
        while (isActive) {
            value = Date()
            delay(1_000L)
        }
    }
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeLabel = remember(now, is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    }
    val dateLabel = remember(now, settings.showLunarDate) {
        LockScreenDateFormatter.currentLabel(context, settings.showLunarDate, now)
    }
    var batteryStatus by remember {
        mutableStateOf(
            LockScreenBatteryStatus(
                percentage = -1,
                isCharging = false,
                isFull = false
            )
        )
    }
    DisposableEffect(context) {
        val monitor = LockScreenBatteryMonitor(context) { batteryStatus = it }
        monitor.start()
        onDispose { monitor.stop() }
    }
    val batteryLabel = remember(settings, batteryStatus) {
        if (settings.shouldShowBatteryStatus(batteryStatus)) {
            settings.formatBatteryStatus(batteryStatus)
        } else {
            null
        }
    }
    val separateClockSource = remember(settings.useSeparateClockFont, settings.clockFontId) {
        if (settings.useSeparateClockFont) {
            AppFontRepository.resolveFontFamilySource(context, settings.clockFontId)
        } else {
            null
        }
    }
    val clockFontState = rememberAppFontFamilyLoadState(
        source = separateClockSource,
        preferredWeight = settings.clockFontWeight
    )
    val baseFontFamily = if (settings.useSystemFont) {
        FontFamily.Default
    } else {
        MaterialTheme.typography.body1.fontFamily
    }
    val timeFontFamily = when {
        !settings.useSeparateClockFont -> baseFontFamily
        settings.clockFontId == AppFontDefaults.SystemFontId -> FontFamily.Default
        else -> clockFontState.fontFamily ?: FontFamily.Default
    }
    LockScreenPreviewFrame(
        settings = settings,
        bitmap = bitmap,
        landscape = landscape,
        timeLabel = timeLabel,
        dateLabel = dateLabel,
        batteryLabel = batteryLabel,
        baseFontFamily = baseFontFamily,
        timeFontFamily = timeFontFamily,
        onToggleOrientation = { landscape = !landscape }
    )
}

@Composable
private fun LockScreenPreviewFrame(
    settings: LockScreenSettings,
    bitmap: android.graphics.Bitmap?,
    landscape: Boolean,
    timeLabel: String,
    dateLabel: String,
    batteryLabel: String?,
    baseFontFamily: FontFamily?,
    timeFontFamily: FontFamily?,
    onToggleOrientation: () -> Unit
) {
    val scrim = Color(settings.scrimColorArgb).copy(alpha = settings.scrimOpacity.coerceIn(0f, 1f))
    val scrimBrush = when {
        settings.scrimStyle == LockScreenScrimStyle.Full -> Brush.linearGradient(listOf(scrim, scrim))
        landscape -> Brush.horizontalGradient(listOf(scrim, Color.Transparent))
        else -> Brush.verticalGradient(listOf(scrim, Color.Transparent, scrim))
    }
    val darkContent = LockScreenWallpaperAppearance.shouldUseDarkContent(bitmap, settings, landscape)
    val contentColor = if (darkContent) Color.Black.copy(alpha = 0.87f) else Color.White
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val longEdge = maxWidth.coerceAtMost(420.dp)
        val previewWidth = if (landscape) longEdge else longEdge * (9f / 16f)
        Box(
            modifier = Modifier
                .width(previewWidth)
                .aspectRatio(if (landscape) 16f / 9f else 9f / 16f)
                .clip(RoundedCornerShape(UiTokens.Radius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "锁屏壁纸预览",
                    modifier = Modifier.matchParentSize().blur(settings.wallpaperBlurRadius.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MsIcon("wallpaper", contentDescription = null, iconSize = 30.dp)
                    Spacer(Modifier.size(4.dp))
                    Text("系统锁屏壁纸", style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(Modifier.matchParentSize().background(scrimBrush))
            LockScreenPreviewLabels(
                modifier = Modifier.align(
                    if (landscape) Alignment.CenterStart else Alignment.Center
                ),
                settings = settings,
                landscape = landscape,
                timeLabel = timeLabel,
                dateLabel = dateLabel,
                batteryLabel = batteryLabel,
                contentColor = contentColor,
                baseFontFamily = baseFontFamily,
                timeFontFamily = timeFontFamily
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                elevation = 4.dp
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Md2IconButton(
                        icon = "screen_rotation",
                        contentDescription = if (landscape) "切换到竖屏预览" else "切换到横屏预览",
                        onClick = onToggleOrientation
                    )
                }
            }
        }
    }
}

@Composable
private fun LockScreenPreviewLabels(
    modifier: Modifier,
    settings: LockScreenSettings,
    landscape: Boolean,
    timeLabel: String,
    dateLabel: String,
    batteryLabel: String?,
    contentColor: Color,
    baseFontFamily: FontFamily?,
    timeFontFamily: FontFamily?
) {
    val groupAlignment = if (settings.timeAndDateAlignedStart) {
        Alignment.Start
    } else {
        Alignment.CenterHorizontally
    }
    val textAlign = if (settings.timeAndDateAlignedStart) TextAlign.Start else TextAlign.Center
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(if (landscape) 0.58f else 1f)
            .padding(14.dp),
        horizontalAlignment = groupAlignment,
        verticalArrangement = if (landscape) Arrangement.Center else Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = groupAlignment) {
            Text(
                timeLabel,
                modifier = Modifier.fillMaxWidth(),
                color = contentColor,
                fontSize = 30.sp,
                fontFamily = timeFontFamily,
                textAlign = textAlign
            )
            Text(
                dateLabel,
                modifier = Modifier.fillMaxWidth(),
                color = contentColor.copy(alpha = 0.76f),
                fontSize = 10.sp,
                fontFamily = baseFontFamily,
                lineHeight = 13.sp,
                textAlign = textAlign
            )
            if (batteryLabel != null) {
                Text(
                    batteryLabel,
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor.copy(alpha = 0.68f),
                    fontSize = 9.sp,
                    fontFamily = baseFontFamily,
                    lineHeight = 12.sp,
                    textAlign = textAlign
                )
            }
        }
        if (landscape) Spacer(Modifier.height(16.dp))
        Text(
            "滑动解锁",
            modifier = Modifier.fillMaxWidth(),
            color = contentColor.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontFamily = baseFontFamily,
            textAlign = TextAlign.Center
        )
    }
}
