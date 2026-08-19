package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import kotlin.math.roundToInt

private enum class CastColorTarget { Subtitle, Background }

@Composable
internal fun LanCastDisplaySettingsDialog(
    settings: LedSubtitleSettings,
    onSettingsChange: (LedSubtitleSettings) -> Unit,
    onReset: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var colorTarget by remember { mutableStateOf<CastColorTarget?>(null) }
    var shapeMenu by remember { mutableStateOf(false) }
    var directionMenu by remember { mutableStateOf(false) }
    var alignmentMenu by remember { mutableStateOf(false) }

    Md2ScrollableDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("投屏显示设置") },
        confirmButton = { Md2TextButton(onClick = onDismissRequest) { Text("完成") } },
        dismissButton = {
            Md2TextButton(onClick = onReset) {
                MsIcon("settings_backup_restore", contentDescription = null)
                Text("恢复默认")
            }
        },
        contentSpacing = 8.dp
    ) {
        CastSettingsSection("外观")
        Md2SettingSwitchRow(
            title = "使用正常字形",
            checked = !settings.dotMatrixEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(dotMatrixEnabled = !it)) }
        )
        CastColorRow("字幕颜色", settings.ledColorArgb) {
            colorTarget = CastColorTarget.Subtitle
        }
        CastColorRow("背景颜色", settings.backgroundColorArgb) {
            colorTarget = CastColorTarget.Background
        }
        if (settings.dotMatrixEnabled) {
            Md2SettingDropdownRow(
                title = "点阵形状",
                value = if (settings.dotShape == LedSubtitleSettings.DOT_SHAPE_SQUARE) "方点" else "圆点",
                expanded = shapeMenu,
                onExpandedChange = { shapeMenu = it }
            ) {
                CastMenuOption("圆点", settings.dotShape == 0) {
                    shapeMenu = false
                    onSettingsChange(settings.copy(dotShape = 0))
                }
                CastMenuOption("方点", settings.dotShape == 1) {
                    shapeMenu = false
                    onSettingsChange(settings.copy(dotShape = 1))
                }
            }
            CastSettingsSlider(
                "灯珠密度",
                settings.dotRowsPerLine.toFloat(),
                LedSubtitleSettings.MIN_DOT_ROWS_PER_LINE.toFloat()..
                    LedSubtitleSettings.MAX_DOT_ROWS_PER_LINE.toFloat(),
                "${settings.dotRowsPerLine} 行"
            ) {
                onSettingsChange(settings.copy(dotRowsPerLine = it.roundToInt()))
            }
            CastSettingsSlider(
                "灯珠尺寸",
                settings.dotSizeFraction,
                LedSubtitleSettings.MIN_DOT_SIZE_FRACTION..
                    LedSubtitleSettings.MAX_DOT_SIZE_FRACTION,
                "${(settings.dotSizeFraction * 100).roundToInt()}%"
            ) {
                onSettingsChange(settings.copy(dotSizeFraction = it))
            }
            Md2SettingSwitchRow("发光效果", settings.glowEnabled, {
                onSettingsChange(settings.copy(glowEnabled = it))
            })
            if (settings.glowEnabled) {
                CastSettingsSlider("发光强度", settings.glowStrength, 0f..1f, "${(settings.glowStrength * 100).roundToInt()}%") {
                    onSettingsChange(settings.copy(glowStrength = it))
                }
            }
        }
        CastSettingsSlider("显示高度", settings.displayHeightFraction, 0.35f..0.92f, "${(settings.displayHeightFraction * 100).roundToInt()}%") {
            onSettingsChange(settings.copy(displayHeightFraction = it))
        }

        CastSettingsSection("排版")
        Md2SettingSwitchRow("内容较多时自动换行", settings.adaptiveMultiLine, {
            onSettingsChange(settings.copy(adaptiveMultiLine = it))
        })
        if (!settings.adaptiveMultiLine) {
            CastSettingsSlider(
                "滚动速度",
                settings.scrollSpeedDpPerSecond,
                LedSubtitleSettings.MIN_SCROLL_SPEED_DP_PER_SECOND..
                    LedSubtitleSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond.roundToInt().toString()
            ) {
                onSettingsChange(settings.copy(scrollSpeedDpPerSecond = it))
            }
            Md2SettingDropdownRow(
                title = "滚动方向",
                value = if (settings.scrollDirection == 0) "向左" else "向右",
                expanded = directionMenu,
                onExpandedChange = { directionMenu = it }
            ) {
                CastMenuOption("向左", settings.scrollDirection == 0) {
                    directionMenu = false
                    onSettingsChange(settings.copy(scrollDirection = 0))
                }
                CastMenuOption("向右", settings.scrollDirection == 1) {
                    directionMenu = false
                    onSettingsChange(settings.copy(scrollDirection = 1))
                }
            }
            CastSettingsSlider(
                "字幕间距",
                settings.loopGapDp,
                LedSubtitleSettings.MIN_LOOP_GAP_DP..LedSubtitleSettings.MAX_LOOP_GAP_DP,
                settings.loopGapDp.roundToInt().toString()
            ) {
                onSettingsChange(settings.copy(loopGapDp = it))
            }
        }
        Md2SettingSwitchRow("快速左滑打开快捷文本面板", settings.quickSwipeOpensQuickText, {
            onSettingsChange(settings.copy(quickSwipeOpensQuickText = it))
        })
        Md2SettingDropdownRow(
            title = "文本对齐",
            value = listOf("居左", "居中", "居右")[settings.shortTextAlignment.coerceIn(0, 2)],
            expanded = alignmentMenu,
            onExpandedChange = { alignmentMenu = it }
        ) {
            listOf("居左", "居中", "居右").forEachIndexed { index, label ->
                CastMenuOption(label, settings.shortTextAlignment == index) {
                    alignmentMenu = false
                    onSettingsChange(settings.copy(shortTextAlignment = index))
                }
            }
        }

        CastSettingsSection("屏幕")
        Md2SettingSwitchRow("保持屏幕常亮", settings.keepScreenOn, {
            onSettingsChange(settings.copy(keepScreenOn = it))
        })
        Md2SettingSwitchRow("亮度跟随系统", settings.followSystemBrightness, {
            onSettingsChange(settings.copy(followSystemBrightness = it))
        })
        if (!settings.followSystemBrightness) {
            CastSettingsSlider("屏幕亮度", settings.screenBrightness, 0.1f..1f, "${(settings.screenBrightness * 100).roundToInt()}%") {
                onSettingsChange(settings.copy(screenBrightness = it))
            }
        }
    }

    colorTarget?.let { target ->
        val initial = if (target == CastColorTarget.Subtitle) {
            settings.ledColorArgb
        } else {
            settings.backgroundColorArgb
        }
        ThemeColorPickerDialog(
            title = if (target == CastColorTarget.Subtitle) "投屏字幕颜色" else "投屏背景颜色",
            initialColor = Color(initial),
            colorLabel = "候选颜色",
            onDismissRequest = { colorTarget = null },
            onColorSelected = { color ->
                val next = if (target == CastColorTarget.Subtitle) {
                    settings.copy(ledColorArgb = color.toArgb())
                } else {
                    settings.copy(backgroundColorArgb = color.toArgb())
                }.normalized()
                onSettingsChange(next)
                if (ColorUtils.calculateContrast(next.ledColorArgb, next.backgroundColorArgb) < 2.0) {
                    toast(context, "字幕与背景颜色太接近，可能看不清")
                }
                colorTarget = null
            }
        )
    }
}

@Composable
private fun CastSettingsSection(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun CastColorRow(label: String, argb: Int, onClick: () -> Unit) {
    ColorPickerSettingRow(
        title = label,
        colorArgb = argb,
        onClick = onClick
    )
}

@Composable
private fun CastSettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun CastMenuOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
