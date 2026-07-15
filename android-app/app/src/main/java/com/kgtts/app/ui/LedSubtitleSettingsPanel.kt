package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

private val LedSettingsPanelBackground = Color(0xF51A1B1E)
private val LedSettingsContent = Color(0xFFF5F5F5)
private val LedSettingsSecondary = Color(0xFFB8BBC2)
private val LedSettingsOutline = Color(0xFF5D616A)

private enum class LedColorTarget { Led, Background }

@Composable
internal fun LedSubtitleSettingsPanel(
    settings: LedSubtitleSettings,
    accentColor: Color,
    panelWidth: Dp,
    onSettingsChange: (LedSubtitleSettings) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentColor
    val context = LocalContext.current
    var colorTarget by remember { mutableStateOf<LedColorTarget?>(null) }
    var resetConfirmationVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .width(panelWidth)
            .widthIn(min = 280.dp, max = 480.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = UiTokens.Radius, bottomStart = UiTokens.Radius),
        color = LedSettingsPanelBackground,
        contentColor = LedSettingsContent,
        elevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LED 设置", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = onClose) {
                    MsIcon("close", contentDescription = "关闭设置", tint = LedSettingsContent)
                }
            }
            Divider(color = LedSettingsOutline.copy(alpha = 0.72f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LedSettingsSectionTitle("外观")
                LedSwitchSetting(
                    label = "使用正常字形",
                    checked = !settings.dotMatrixEnabled,
                    accent = accent,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(dotMatrixEnabled = !it))
                    }
                )
                LedColorSettingRow(
                    label = "字幕颜色",
                    color = Color(settings.ledColorArgb),
                    onClick = { colorTarget = LedColorTarget.Led }
                )
                LedColorSettingRow(
                    label = "背景颜色",
                    color = Color(settings.backgroundColorArgb),
                    onClick = { colorTarget = LedColorTarget.Background }
                )
                if (settings.dotMatrixEnabled) {
                    LedSegmentedSetting(
                        label = "点阵形状",
                        options = listOf("circle" to "圆点", "square" to "方点"),
                        selectedIndex = settings.dotShape,
                        accent = accent,
                        onSelected = { onSettingsChange(settings.copy(dotShape = it)) }
                    )
                    LedSettingsSlider(
                        label = "点阵密度",
                        value = settings.dotDensity,
                        valueRange = 0f..1f,
                        valueLabel = "${(settings.dotDensity * 100).roundToInt()}%",
                        accent = accent,
                        onValueChange = { onSettingsChange(settings.copy(dotDensity = it)) }
                    )
                    LedSwitchSetting(
                        label = "发光效果",
                        checked = settings.glowEnabled,
                        accent = accent,
                        onCheckedChange = { onSettingsChange(settings.copy(glowEnabled = it)) }
                    )
                    if (settings.glowEnabled) {
                        LedSettingsSlider(
                            label = "发光强度",
                            value = settings.glowStrength,
                            valueRange = 0f..1f,
                            valueLabel = "${(settings.glowStrength * 100).roundToInt()}%",
                            accent = accent,
                            onValueChange = { onSettingsChange(settings.copy(glowStrength = it)) }
                        )
                    }
                }
                LedSettingsSlider(
                    label = "显示高度",
                    value = settings.displayHeightFraction,
                    valueRange = 0.35f..0.92f,
                    valueLabel = "${(settings.displayHeightFraction * 100).roundToInt()}%",
                    accent = accent,
                    onValueChange = { onSettingsChange(settings.copy(displayHeightFraction = it)) }
                )

                LedSettingsSectionTitle("滚动")
                LedSettingsSlider(
                    label = "滚动速度",
                    value = settings.scrollSpeedDpPerSecond,
                    valueRange = 24f..220f,
                    valueLabel = "${settings.scrollSpeedDpPerSecond.roundToInt()} dp/s",
                    accent = accent,
                    onValueChange = { onSettingsChange(settings.copy(scrollSpeedDpPerSecond = it)) }
                )
                LedSwitchSetting(
                    label = "快速左滑打开快捷文本",
                    checked = settings.quickSwipeOpensQuickText,
                    accent = accent,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(quickSwipeOpensQuickText = it))
                    }
                )
                LedSegmentedSetting(
                    label = "滚动方向",
                    options = listOf("arrow_back" to "向左", "arrow_forward" to "向右"),
                    selectedIndex = settings.scrollDirection,
                    accent = accent,
                    onSelected = { onSettingsChange(settings.copy(scrollDirection = it)) }
                )
                LedSettingsSlider(
                    label = "循环间距",
                    value = settings.loopGapDp,
                    valueRange = 24f..240f,
                    valueLabel = "${settings.loopGapDp.roundToInt()} dp",
                    accent = accent,
                    onValueChange = { onSettingsChange(settings.copy(loopGapDp = it)) }
                )
                LedSegmentedSetting(
                    label = "短文本对齐",
                    options = listOf(
                        "format_align_left" to "居左",
                        "format_align_center" to "居中",
                        "format_align_right" to "居右"
                    ),
                    selectedIndex = settings.shortTextAlignment,
                    accent = accent,
                    onSelected = { onSettingsChange(settings.copy(shortTextAlignment = it)) }
                )

                LedSettingsSectionTitle("屏幕")
                LedSwitchSetting(
                    label = "保持屏幕常亮",
                    checked = settings.keepScreenOn,
                    accent = accent,
                    onCheckedChange = { onSettingsChange(settings.copy(keepScreenOn = it)) }
                )
                LedSwitchSetting(
                    label = "亮度跟随系统",
                    checked = settings.followSystemBrightness,
                    accent = accent,
                    onCheckedChange = { onSettingsChange(settings.copy(followSystemBrightness = it)) }
                )
                if (!settings.followSystemBrightness) {
                    LedSettingsSlider(
                        label = "屏幕亮度",
                        value = settings.screenBrightness,
                        valueRange = 0.1f..1f,
                        valueLabel = "${(settings.screenBrightness * 100).roundToInt()}%",
                        accent = accent,
                        onValueChange = { onSettingsChange(settings.copy(screenBrightness = it)) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Md2TextButton(
                    onClick = { resetConfirmationVisible = true },
                    contentColor = LedSettingsContent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MsIcon("settings_backup_restore", contentDescription = null, tint = LedSettingsContent)
                    Spacer(Modifier.width(8.dp))
                    Text("恢复 LED 默认设置")
                }
            }
        }
    }

    colorTarget?.let { target ->
        ThemeColorPickerDialog(
            title = if (target == LedColorTarget.Led) "字幕颜色" else "背景颜色",
            initialColor = if (target == LedColorTarget.Led) {
                Color(settings.ledColorArgb)
            } else {
                Color(settings.backgroundColorArgb)
            },
            colorLabel = if (target == LedColorTarget.Led) "候选字幕颜色" else "候选背景颜色",
            onDismissRequest = { colorTarget = null },
            onColorSelected = { color ->
                val next = if (target == LedColorTarget.Led) {
                    settings.copy(ledColorArgb = color.toArgb())
                } else {
                    settings.copy(backgroundColorArgb = color.toArgb())
                }.normalized()
                onSettingsChange(next)
                if (ColorUtils.calculateContrast(next.ledColorArgb, next.backgroundColorArgb) < 2.0) {
                    toast(context, "字幕与背景颜色对比度较低")
                }
                colorTarget = null
            }
        )
    }

    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text("恢复 LED 默认设置") },
            text = { Text("确定恢复全部 LED 显示设置？") },
            confirmButton = {
                Md2TextButton(
                    onClick = {
                        resetConfirmationVisible = false
                        onReset()
                    }
                ) { Text("恢复") }
            },
            dismissButton = {
                Md2TextButton(onClick = { resetConfirmationVisible = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LedSettingsSectionTitle(text: String) {
    Text(text, color = LedSettingsSecondary, fontWeight = FontWeight.Medium)
}

@Composable
private fun LedColorSettingRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(color, CircleShape)
                .border(1.dp, LedSettingsOutline, CircleShape)
        )
    }
}

@Composable
private fun LedSettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueLabel, color = LedSettingsSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = LedSettingsOutline
            )
        )
    }
}

@Composable
private fun LedSwitchSetting(
    label: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.52f),
                uncheckedThumbColor = LedSettingsSecondary,
                uncheckedTrackColor = LedSettingsOutline
            )
        )
    }
}

@Composable
private fun LedSegmentedSetting(
    label: String,
    options: List<Pair<String, String>>,
    selectedIndex: Int,
    accent: Color,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, (icon, description) ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(UiTokens.Radius),
                    color = if (selected) accent.copy(alpha = 0.24f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) accent else LedSettingsOutline
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MsIcon(
                            icon,
                            contentDescription = description,
                            tint = if (selected) accent else LedSettingsSecondary
                        )
                    }
                }
            }
        }
    }
}
