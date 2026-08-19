package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.lan.LanCastBackgroundAccess
import com.lhtstudio.kigtts.app.lan.LanCastRuntime
import com.lhtstudio.kigtts.app.lan.LanCastUiCommand
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings

@Composable
internal fun LanCastUiCommandEffect(viewModel: MainViewModel) {
    LaunchedEffect(viewModel) {
        LanCastRuntime.uiCommands().collect { command ->
            when (command) {
                is LanCastUiCommand.UpdateDisplaySettings -> {
                    val value = command.settings
                    viewModel.applyLanCastDisplaySettingsFromWeb(
                        LedSubtitleSettings(
                            ledColorArgb = value.colorArgb,
                            backgroundColorArgb = value.backgroundArgb,
                            dotMatrixEnabled = value.dotMatrix,
                            dotShape = value.dotShape,
                            dotRowsPerLine = value.dotRowsPerLine,
                            dotSizeFraction = value.dotSizeFraction,
                            glowEnabled = value.glowEnabled,
                            glowStrength = value.glowStrength,
                            displayHeightFraction = value.displayHeightFraction,
                            adaptiveMultiLine = value.adaptiveMultiLine,
                            scrollSpeedDpPerSecond = value.speed,
                            scrollDirection = value.direction,
                            quickSwipeOpensQuickText = value.quickSwipeOpensQuickText,
                            loopGapDp = value.loopGap,
                            shortTextAlignment = value.shortTextAlignment,
                            keepScreenOn = value.keepScreenOn,
                            followSystemBrightness = value.followSystemBrightness,
                            screenBrightness = value.screenBrightness
                        ).normalized()
                    )
                }
                is LanCastUiCommand.SelectQuickTextGroup -> {
                    val index = viewModel.quickSubtitleGroups.indexOfFirst {
                        it.id == command.groupId
                    }
                    if (index >= 0) viewModel.selectQuickSubtitleGroup(index)
                }
                is LanCastUiCommand.AddCurrentText -> {
                    val index = viewModel.quickSubtitleGroups.indexOfFirst {
                        it.id == command.groupId
                    }
                    if (index >= 0) {
                        viewModel.addQuickSubtitleItem(index, viewModel.quickSubtitleCurrentText)
                    }
                }
                is LanCastUiCommand.SetPlayOnSend -> {
                    viewModel.updateQuickSubtitlePlayOnSend(command.enabled)
                }
                is LanCastUiCommand.UpdateQuickSubtitleStyle -> {
                    viewModel.updateQuickSubtitleBold(command.bold)
                    viewModel.updateQuickSubtitleCentered(command.centered)
                    viewModel.updateQuickSubtitleRotated180(command.rotated180)
                    viewModel.setQuickSubtitleFontSize(command.fontSizeSp)
                }
                is LanCastUiCommand.SetAudioOutputMode -> {
                    viewModel.setLanCastAudioOutputMode(command.mode)
                }
            }
        }
    }
}

@Composable
internal fun LanCastScreen(
    viewModel: MainViewModel,
    state: UiState
) {
    val context = LocalContext.current
    val status by LanCastRuntime.statusFlow().collectAsState()
    var backgroundPurposeOpen by rememberSaveable { mutableStateOf(false) }
    var dismissBackgroundReminder by rememberSaveable { mutableStateOf(false) }
    var displaySettingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.refreshLanCastAddresses()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LanCastServiceCard(
            viewModel = viewModel,
            status = status,
            onEnableRequested = {
                viewModel.startLanCast()
                if (
                    !LanCastBackgroundAccess.isGranted(context) &&
                    !state.lanCastBackgroundReminderDismissed
                ) {
                    dismissBackgroundReminder = false
                    backgroundPurposeOpen = true
                }
            },
            onBackgroundSettingsRequested = {
                if (
                    LanCastBackgroundAccess.isGranted(context) ||
                    state.lanCastBackgroundReminderDismissed
                ) {
                    viewModel.openLanCastBackgroundSettings()
                } else {
                    dismissBackgroundReminder = false
                    backgroundPurposeOpen = true
                }
            }
        )
        if (status.running) {
            LanCastAddressCard(viewModel = viewModel, status = status)
            LanCastQrCard(status = status)
        }
        LanCastDisplaySettingsCard(onOpen = { displaySettingsOpen = true })
        LanCastAudioCard(viewModel = viewModel, state = state)
        Spacer(
            Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            )
        )
    }

    if (backgroundPurposeOpen) {
        PermissionPurposeDialog(
            info = lanCastBackgroundPermissionPurpose(),
            onConfirm = {
                if (dismissBackgroundReminder) {
                    viewModel.setLanCastBackgroundReminderDismissed(true)
                }
                backgroundPurposeOpen = false
                dismissBackgroundReminder = false
                viewModel.openLanCastBackgroundSettings()
            },
            onDismiss = {
                if (dismissBackgroundReminder) {
                    viewModel.setLanCastBackgroundReminderDismissed(true)
                }
                backgroundPurposeOpen = false
                dismissBackgroundReminder = false
            },
            showDontAskAgain = true,
            dontAskAgainChecked = dismissBackgroundReminder,
            onDontAskAgainChange = { dismissBackgroundReminder = it }
        )
    }

    if (displaySettingsOpen) {
        LanCastDisplaySettingsDialog(
            settings = state.lanCastDisplaySettings,
            onSettingsChange = viewModel::updateLanCastDisplaySettings,
            onReset = viewModel::resetLanCastDisplaySettings,
            onDismissRequest = { displaySettingsOpen = false }
        )
    }
}

internal fun lanCastBackgroundPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "需要允许后台运行",
    iconName = "battery_saver",
    summary = "建议允许后台持续运行，以防止熄屏后投屏和网页遥控无法连接设备。",
    permissionName = "忽略电池优化（后台运行不受限制）",
    serviceFeature = "局域网投屏、网页遥控和投屏音频",
    purpose = "仅在你手动开启投屏服务后维持本机服务器和局域网连接，避免熄屏后停止响应。",
    privacyNote = "该权限不会让 KIGTTS 扫描或上传其它数据；投屏服务仍会在你手动关闭后停止。",
    confirmLabel = "前往设置",
    dismissLabel = "暂不设置"
)
