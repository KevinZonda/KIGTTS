package com.lhtstudio.kigtts.app.ui

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
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.lan.LanCastAudioOutputMode

@Composable
internal fun LanCastAudioCard(viewModel: MainViewModel, state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    val mode = LanCastAudioOutputMode.fromPreferenceValue(state.lanCastAudioOutputMode)
    Md2StaggeredFloatIn(index = 4) {
        Md2SettingsCard(title = "投屏端音频") {
            Md2SettingDropdownRow(
                title = "音频播放位置",
                icon = "speaker",
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
            Md2SettingActionRow(
                title = "显示设置",
                icon = "tune",
                supportingText = "网页投屏使用独立样式，不会修改原生 LED 页面设置。",
                onClick = onOpen
            )
        }
    }
}
