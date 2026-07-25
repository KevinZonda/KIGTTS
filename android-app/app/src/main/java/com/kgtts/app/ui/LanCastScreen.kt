package com.lhtstudio.kigtts.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                    viewModel.updateLanCastDisplaySettings(
                        LedSubtitleSettings(
                            ledColorArgb = value.colorArgb,
                            backgroundColorArgb = value.backgroundArgb,
                            dotMatrixEnabled = value.dotMatrix,
                            dotShape = value.dotShape,
                            dotDensity = value.dotDensity,
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
                LanCastUiCommand.ResetDisplaySettings -> viewModel.resetLanCastDisplaySettings()
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
    var notificationPurposeOpen by remember { mutableStateOf(false) }
    var displaySettingsOpen by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setLiveSubtitleNotificationEnabled(true)
        } else {
            toast(context, "未授予通知权限，实时通知无法显示")
        }
    }

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
        LanCastServiceCard(viewModel = viewModel, status = status)
        if (status.running) {
            LanCastAddressCard(viewModel = viewModel, status = status)
            LanCastQrCard(status = status)
        }
        LanCastDisplaySettingsCard(onOpen = { displaySettingsOpen = true })
        LanCastAudioCard(viewModel = viewModel, state = state)
        LanCastExternalSubtitleCard(
            viewModel = viewModel,
            state = state,
            onNotificationPermissionRequired = { notificationPurposeOpen = true }
        )
        Spacer(
            Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            )
        )
    }

    if (notificationPurposeOpen) {
        PermissionPurposeDialog(
            info = notificationPermissionPurpose(),
            onConfirm = {
                notificationPurposeOpen = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { notificationPurposeOpen = false }
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
