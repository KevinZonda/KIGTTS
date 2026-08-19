package com.lhtstudio.kigtts.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lhtstudio.kigtts.app.lan.LanCastBackgroundAccess
import com.lhtstudio.kigtts.app.lan.LanCastRuntime

@Composable
internal fun LanCastEntryCard(
    viewModel: MainViewModel,
    state: UiState,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val status by LanCastRuntime.statusFlow().collectAsState()
    var backgroundPurposeOpen by rememberSaveable { mutableStateOf(false) }
    var dismissBackgroundReminder by rememberSaveable { mutableStateOf(false) }
    val connected = status.displayClients + status.remoteClients

    OverlaySettingsEntryCard(
        iconName = "cast",
        title = "投屏与遥控",
        status = when {
            !status.running -> "已关闭"
            connected == 0 -> "已开启 · 等待连接"
            else -> "已开启 · 已连接 $connected 个网页端"
        },
        switchLabel = "启用投屏与网页遥控",
        checked = status.running,
        supportingText = "在同一局域网内显示字幕，或通过网页遥控 KIGTTS。",
        onCheckedChange = { enabled ->
            if (!enabled) {
                viewModel.stopLanCast()
            } else {
                viewModel.startLanCast()
                if (
                    !LanCastBackgroundAccess.isGranted(context) &&
                    !state.lanCastBackgroundReminderDismissed
                ) {
                    dismissBackgroundReminder = false
                    backgroundPurposeOpen = true
                }
            }
        },
        onOpen = onOpen,
        footer = {
            if (status.running) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                LanCastQrContent(status = status)
            }
        }
    )

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
}

@Composable
internal fun ExternalSubtitleEntryCards(
    viewModel: MainViewModel,
    state: UiState
) {
    val context = LocalContext.current
    var notificationPurposeOpen by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setLiveSubtitleNotificationEnabled(true)
        } else {
            toast(context, "未授予通知权限，实时通知无法显示")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverlaySettingsSwitchCard(
            iconName = "bluetooth",
            title = "蓝牙设备字幕显示",
            checked = state.bluetoothMediaTitleSubtitle,
            supportingText = "在支持媒体标题显示的蓝牙歌词屏、车机或小屏设备上显示当前字幕。",
            onCheckedChange = viewModel::setBluetoothMediaTitleSubtitle
        )
        OverlaySettingsSwitchCard(
            iconName = "notifications_active",
            title = "实时通知",
            checked = state.liveSubtitleNotificationEnabled,
            supportingText = "在前台、后台或锁屏时，通过系统通知显示当前字幕。",
            onCheckedChange = { enabled ->
                if (enabled && notificationPermissionMissing(context)) {
                    notificationPurposeOpen = true
                } else {
                    viewModel.setLiveSubtitleNotificationEnabled(enabled)
                }
            }
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
}

private fun notificationPermissionMissing(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
