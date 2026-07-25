package com.lhtstudio.kigtts.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhtstudio.kigtts.app.data.LockScreenScrimStyle
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import com.lhtstudio.kigtts.app.data.LockScreenWallpaperStore
import com.lhtstudio.kigtts.app.data.formatColorHexAndNameZhCn
import com.lhtstudio.kigtts.app.overlay.LockScreenWallpaperAppearance

@Composable
internal fun LockScreenWallpaperSettingsCard(
    settings: LockScreenSettings,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSettingsChange: (LockScreenSettings) -> Unit
) {
    var showScrimColorPicker by remember { mutableStateOf(false) }
    Md2SettingsCard(title = "锁屏壁纸") {
        LockScreenWallpaperPreview(settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Md2OutlinedButton(onClick = onChooseWallpaper) {
                MsIcon("image", contentDescription = null, iconSize = 18.dp)
                Text(if (settings.wallpaperPath.isBlank()) "选择壁纸" else "更换壁纸")
            }
            if (settings.wallpaperPath.isNotBlank()) {
                Md2TextButton(onClick = onClearWallpaper) { Text("清除") }
            }
        }
        Text(
            "未设置时保持透明并显示系统锁屏壁纸；横竖屏会自动居中裁切。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (settings.wallpaperPath.isNotBlank()) {
            LockScreenEffectSlider(
                title = "背景模糊",
                valueLabel = if (settings.wallpaperBlurRadius < 0.5f) {
                    "关闭"
                } else {
                    "${settings.wallpaperBlurRadius.toInt()}"
                },
                value = settings.wallpaperBlurRadius,
                valueRange = 0f..30f,
                onValueChange = {
                    onSettingsChange(settings.copy(wallpaperBlurRadius = it))
                }
            )
            LockScreenEffectSlider(
                title = "遮罩透明度",
                valueLabel = "${(settings.scrimOpacity * 100).toInt()}%",
                value = settings.scrimOpacity,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChange(settings.copy(scrimOpacity = it)) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showScrimColorPicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(settings.scrimColorArgb), androidx.compose.foundation.shape.CircleShape)
                )
                Column(Modifier.weight(1f)) {
                    Text("遮罩颜色", fontWeight = FontWeight.SemiBold)
                    Text(
                        formatColorHexAndNameZhCn(settings.scrimColorArgb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MsIcon("chevron_right", contentDescription = "选择遮罩颜色")
            }
            Text(
                "遮罩样式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TabRow(
                selectedTabIndex = settings.scrimStyle.ordinal,
                backgroundColor = md2CardContainerColor(),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                LockScreenScrimStyle.entries.forEach { style ->
                    Tab(
                        selected = settings.scrimStyle == style,
                        onClick = { onSettingsChange(settings.copy(scrimStyle = style)) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MsIcon(
                                    if (style == LockScreenScrimStyle.EdgeGradient) "gradient" else "crop_square",
                                    contentDescription = null,
                                    iconSize = 18.dp
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(if (style == LockScreenScrimStyle.EdgeGradient) "渐变" else "全填充")
                            }
                        }
                    )
                }
            }
            Text(
                "竖屏渐变覆盖顶部和底部；横屏仅从左侧向右淡出，保证时钟与解锁提示清晰。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showScrimColorPicker) {
        ThemeColorPickerDialog(
            title = "选择遮罩颜色",
            initialColor = Color(settings.scrimColorArgb),
            colorLabel = "候选遮罩色",
            onDismissRequest = { showScrimColorPicker = false },
            onColorSelected = { color ->
                onSettingsChange(settings.copy(scrimColorArgb = color.toArgb()))
                showScrimColorPicker = false
            }
        )
    }
}

@Composable
private fun LockScreenEffectSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
}

@Composable
private fun LockScreenWallpaperPreview(settings: LockScreenSettings) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bitmap by produceState<android.graphics.Bitmap?>(null, settings.wallpaperPath) {
        value = LockScreenWallpaperStore.loadForDisplay(settings.wallpaperPath, 1280, 720)
    }
    val shape = RoundedCornerShape(UiTokens.Radius)
    val scrim = Color(settings.scrimColorArgb).copy(alpha = settings.scrimOpacity.coerceIn(0f, 1f))
    val scrimBrush = when {
        settings.scrimStyle == LockScreenScrimStyle.Full -> Brush.linearGradient(listOf(scrim, scrim))
        landscape -> Brush.horizontalGradient(listOf(scrim, Color.Transparent))
        else -> Brush.verticalGradient(listOf(scrim, Color.Transparent, scrim))
    }
    val darkContent = LockScreenWallpaperAppearance.shouldUseDarkContent(bitmap, settings, landscape)
    val contentColor = if (darkContent) Color.Black.copy(alpha = 0.87f) else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = "锁屏壁纸预览",
                modifier = Modifier
                    .matchParentSize()
                    .blur(settings.wallpaperBlurRadius.dp),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.matchParentSize().background(scrimBrush))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = if (landscape) Arrangement.Center else Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally) {
                    Text("10:26", color = contentColor, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("7月26日 星期日", color = contentColor.copy(alpha = 0.76f), fontSize = 11.sp)
                }
                Text("⌃  滑动解锁", color = contentColor.copy(alpha = 0.82f), fontSize = 11.sp)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MsIcon("wallpaper", contentDescription = null, iconSize = 36.dp)
                Spacer(Modifier.size(6.dp))
                Text("使用系统锁屏壁纸", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
