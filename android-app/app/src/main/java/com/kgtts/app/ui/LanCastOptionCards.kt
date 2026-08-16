package com.lhtstudio.kigtts.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lhtstudio.kigtts.app.lan.LanCastAudioOutputMode

@Composable
internal fun LanCastAudioCard(viewModel: MainViewModel, state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    val mode = LanCastAudioOutputMode.fromPreferenceValue(state.lanCastAudioOutputMode)
    Md2StaggeredFloatIn(index = 4) {
        Md2SettingsCard(title = "投屏端音频") {
            Md2SettingDropdownRow(
                title = "音频播放位置",
                value = mode.label,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                supportingText = "开启后，声音会在投屏设备上播放，如果未连接投屏设备时则会自动改为手机播放。"
            ) {
                LanCastAudioOutputMode.entries.forEach { option ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        viewModel.setLanCastAudioOutputMode(option.preferenceValue)
                    }) {
                        RadioButton(selected = mode == option, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(option.label)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LanCastDisplaySettingsCard(onOpen: () -> Unit) {
    Md2StaggeredFloatIn(index = 3) {
        Md2SettingsCard(title = "投屏显示") {
            Text(
                "网页投屏使用独立的显示样式，不会修改原生 LED 页面设置。",
                style = androidx.compose.material.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Md2TextButton(onClick = onOpen) {
                MsIcon("tune", contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("投屏显示设置")
            }
        }
    }
}

@Composable
internal fun LanCastExternalSubtitleCard(
    viewModel: MainViewModel,
    state: UiState,
    onNotificationPermissionRequired: () -> Unit
) {
    val context = LocalContext.current
    Md2StaggeredFloatIn(index = 5) {
        Md2SettingsCard(title = "其它字幕显示") {
            Md2SettingSwitchRow(
                title = "蓝牙设备字幕显示",
                checked = state.bluetoothMediaTitleSubtitle,
                onCheckedChange = viewModel::setBluetoothMediaTitleSubtitle,
                supportingText = "把当前上屏大字幕写入系统媒体标题，用于支持媒体标题显示的蓝牙歌词屏、车机或小屏设备。"
            )
            Md2SettingSwitchRow(
                title = "实时通知",
                checked = state.liveSubtitleNotificationEnabled,
                onCheckedChange = { enabled ->
                    val permissionMissing = enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (permissionMissing) {
                        onNotificationPermissionRequired()
                    } else {
                        viewModel.setLiveSubtitleNotificationEnabled(enabled)
                    }
                },
                supportingText = "在前台、后台或锁屏时通过系统通知显示当前字幕，并提供朗读、打开便捷字幕和关闭操作。"
            )
        }
    }
}
