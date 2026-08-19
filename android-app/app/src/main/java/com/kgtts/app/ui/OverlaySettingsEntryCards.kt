package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun OverlaySettingsEntryCard(
    iconName: String,
    title: String,
    status: String,
    switchLabel: String,
    checked: Boolean,
    supportingText: String,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    val hapticOnOpen = rememberKigttsHapticClick(onOpen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = hapticOnOpen)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MsIcon(
                    iconName,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.accentText
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MsIcon("chevron_right", contentDescription = "打开$title")
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(switchLabel, modifier = Modifier.weight(1f))
                Md2Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            footer()
        }
    }
}

@Composable
internal fun OverlaySettingsSwitchCard(
    iconName: String,
    title: String,
    checked: Boolean,
    supportingText: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val hapticToggle = rememberKigttsHapticClick { onCheckedChange(!checked) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = hapticToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsIcon(
                iconName,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.accentText
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Md2Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
internal fun FloatingOverlayEntryCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    OverlaySettingsEntryCard(
        iconName = "picture_in_picture_alt",
        title = "悬浮窗",
        status = if (enabled) "已开启" else "已关闭",
        switchLabel = "使用悬浮窗",
        checked = enabled,
        supportingText = "在其它应用上方使用快捷字幕、快捷名片、画板和音效板。",
        onCheckedChange = onEnabledChange,
        onOpen = onOpen
    )
}

@Composable
internal fun VolumeHotkeyEntryCard(
    masterEnabled: Boolean,
    upDownEnabled: Boolean,
    downUpEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val enabledCount = listOf(upDownEnabled, downUpEnabled).count { it }
    OverlaySettingsEntryCard(
        iconName = "volume_up",
        title = "音量热键",
        status = when {
            !masterEnabled -> "已关闭"
            enabledCount == 0 -> "尚未设置"
            else -> "已启用 $enabledCount / 2 项"
        },
        switchLabel = "使用音量热键",
        checked = masterEnabled,
        supportingText = "在应用外按下设定的音量键顺序，也能执行对应的快捷操作。",
        onCheckedChange = onMasterEnabledChange,
        onOpen = onOpen
    )
}
