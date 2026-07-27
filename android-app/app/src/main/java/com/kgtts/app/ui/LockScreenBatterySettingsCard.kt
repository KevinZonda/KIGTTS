package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.LockScreenBatteryStatus
import com.lhtstudio.kigtts.app.data.LockScreenBatteryStyle
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import com.lhtstudio.kigtts.app.data.MAX_LOW_BATTERY_THRESHOLD
import com.lhtstudio.kigtts.app.data.MIN_LOW_BATTERY_THRESHOLD
import com.lhtstudio.kigtts.app.data.formatBatteryStatus
import kotlin.math.roundToInt

@Composable
internal fun LockScreenBatterySettingsCard(
    settings: LockScreenSettings,
    onSettingsChange: (LockScreenSettings) -> Unit
) {
    val hapticStyleChange = rememberKigttsHapticValueChange<LockScreenBatteryStyle> { style ->
        onSettingsChange(settings.copy(batteryStyle = style))
    }
    Md2SettingsCard(title = "电量状态") {
        Md2SettingSwitchRow(
            title = "显示电量和充电状态",
            checked = settings.showBatteryStatus,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(showBatteryStatus = enabled))
            }
        )
        Text(
            "开启后显示在日期和时间下方。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (settings.showBatteryStatus) {
            Text(
                "显示样式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TabRow(
                selectedTabIndex = settings.batteryStyle.ordinal,
                backgroundColor = md2CardContainerColor(),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                LockScreenBatteryStyle.entries.forEach { style ->
                    Tab(
                        selected = settings.batteryStyle == style,
                        onClick = { hapticStyleChange(style) },
                        text = {
                            Text(
                                if (style == LockScreenBatteryStyle.Compact) {
                                    "简洁"
                                } else {
                                    "完整"
                                }
                            )
                        }
                    )
                }
            }
            val sample = settings.formatBatteryStatus(
                LockScreenBatteryStatus(
                    percentage = 76,
                    isCharging = true,
                    isFull = false
                )
            )
            Text(
                "预览：$sample",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Md2SettingSwitchRow(
                title = "仅在充电或低电量时显示",
                checked = settings.batteryOnlyWhenChargingOrLow,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(batteryOnlyWhenChargingOrLow = enabled)
                    )
                }
            )
            if (settings.batteryOnlyWhenChargingOrLow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("低电量阈值", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${settings.lowBatteryThreshold}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = settings.lowBatteryThreshold.toFloat(),
                    onValueChange = { value ->
                        onSettingsChange(
                            settings.copy(lowBatteryThreshold = value.roundToInt())
                        )
                    },
                    valueRange = MIN_LOW_BATTERY_THRESHOLD.toFloat()..
                        MAX_LOW_BATTERY_THRESHOLD.toFloat(),
                    steps = MAX_LOW_BATTERY_THRESHOLD - MIN_LOW_BATTERY_THRESHOLD - 1
                )
                Text(
                    "正在充电，或电量低于 ${settings.lowBatteryThreshold}% 时显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
