package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.DropdownMenuItem as M2DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.audio.AudioDenoiserMode
import com.lhtstudio.kigtts.app.audio.SpeechEnhancementMode
import com.lhtstudio.kigtts.app.audio.VadMode
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage

@Composable
internal fun ListeningAudioSettingsScreen(
    viewModel: MainViewModel,
    state: UiState
) {
    val settings = state.listeningModeSettings
    var languageExpanded by remember { mutableStateOf(false) }
    var inputExpanded by remember { mutableStateOf(false) }
    var denoiserExpanded by remember { mutableStateOf(false) }
    var enhancementExpanded by remember { mutableStateOf(false) }
    var vadExpanded by remember { mutableStateOf(false) }
    val vadMode = VadMode.fromFlags(
        settings.classicVadEnabled,
        settings.sileroVadEnabled
    )
    val update = viewModel::updateListeningModeSettings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(UiTokens.PageTopBlank))
        Md2SettingsCard(title = "字幕识别") {
            Text(
                text = "这些设置只用于聆听模式，不会改变平时按住说话的识别设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
            )
            Md2SettingSwitchRow(
                title = "输入模式下隐藏聆听字幕",
                checked = settings.hideDuringTextInput,
                onCheckedChange = { enabled ->
                    update { it.copy(hideDuringTextInput = enabled) }
                },
                supportingText = "输入文字时暂时收起聆听区域，为大字幕预览和键盘留出更多空间。"
            )
            Md2SettingDropdownRow(
                title = "聆听语言",
                value = AsrRecognitionLanguage.label(settings.recognitionLanguage),
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it },
                supportingText = AsrRecognitionLanguage.description(settings.recognitionLanguage)
            ) {
                AsrRecognitionLanguage.entries.forEach { language ->
                    M2DropdownMenuItem(
                        onClick = {
                            languageExpanded = false
                            update { it.copy(recognitionLanguage = language) }
                        }
                    ) { Text(AsrRecognitionLanguage.label(language)) }
                }
            }
            Md2SettingDropdownRow(
                title = "聆听麦克风",
                value = listeningInputOptions
                    .firstOrNull { it.first == settings.preferredInputType }
                    ?.second ?: listeningInputOptions.first().second,
                expanded = inputExpanded,
                onExpandedChange = { inputExpanded = it },
                supportingText = "默认使用设备内置麦克风，与普通语音输入的设备选择分开保存。"
            ) {
                listeningInputOptions.forEach { (type, label) ->
                    M2DropdownMenuItem(
                        onClick = {
                            inputExpanded = false
                            update { it.copy(preferredInputType = type) }
                        }
                    ) { Text(label) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Md2SettingsCard(title = "环境收音") {
            Md2SettingDropdownRow(
                title = "语音增强",
                value = SpeechEnhancementMode.labelOf(settings.speechEnhancementMode),
                expanded = enhancementExpanded,
                onExpandedChange = { enhancementExpanded = it },
                supportingText = "增强较轻或隔着头套传来的声音，并降低持续环境噪声。"
            ) {
                SpeechEnhancementMode.options.forEach { (mode, label) ->
                    M2DropdownMenuItem(
                        onClick = {
                            enhancementExpanded = false
                            update {
                                it.copy(
                                    speechEnhancementMode = mode,
                                    denoiserMode = if (SpeechEnhancementMode.isEnabled(mode)) {
                                        AudioDenoiserMode.OFF
                                    } else {
                                        it.denoiserMode
                                    }
                                )
                            }
                        }
                    ) { Text(label) }
                }
            }
            Md2SettingDropdownRow(
                title = "兼容降噪",
                value = AudioDenoiserMode.labelOf(settings.denoiserMode),
                expanded = denoiserExpanded,
                onExpandedChange = { denoiserExpanded = it },
                supportingText = "关闭语音增强时可使用较轻量的降噪。"
            ) {
                AudioDenoiserMode.options.forEach { (mode, label) ->
                    M2DropdownMenuItem(
                        onClick = {
                            denoiserExpanded = false
                            update {
                                it.copy(
                                    denoiserMode = mode,
                                    speechEnhancementMode = if (mode != AudioDenoiserMode.OFF) {
                                        SpeechEnhancementMode.OFF
                                    } else {
                                        it.speechEnhancementMode
                                    }
                                )
                            }
                        }
                    ) { Text(label) }
                }
            }
            Md2SettingDropdownRow(
                title = "断句方式",
                value = VadMode.options.first { it.first == vadMode }.second,
                expanded = vadExpanded,
                onExpandedChange = { vadExpanded = it },
                supportingText = "智能断句更适合连续对话和现场字幕。"
            ) {
                VadMode.options.forEach { (mode, label) ->
                    M2DropdownMenuItem(
                        onClick = {
                            vadExpanded = false
                            val (classic, silero) = VadMode.toFlags(mode)
                            update {
                                it.copy(
                                    classicVadEnabled = classic,
                                    sileroVadEnabled = silero
                                )
                            }
                        }
                    ) { Text(label) }
                }
            }
            Text("最低收音音量：${settings.minVolumePercent}%")
            Slider(
                value = settings.minVolumePercent.toFloat(),
                onValueChange = { value ->
                    update { it.copy(minVolumePercent = value.toInt()) }
                },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth()
            )
            if (settings.sileroVadEnabled) {
                Text("智能断句灵敏度：${(settings.sileroVadThreshold * 100).toInt()}%")
                Slider(
                    value = settings.sileroVadThreshold,
                    onValueChange = { value ->
                        update { it.copy(sileroVadThreshold = value) }
                    },
                    valueRange = 0.05f..0.95f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
