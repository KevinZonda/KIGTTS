package com.lhtstudio.kigtts.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.AppConfigBackupOptions
import com.lhtstudio.kigtts.app.data.AppFontDefaults

@Composable
internal fun AppConfigBackupSettingsCard(
    viewModel: MainViewModel,
    state: UiState
) {
    val extensions = remember { setOf("kigconfig") }
    val openFileManagerAfterPermission = rememberFileManagerPermissionGate()
    var backupDialogVisible by remember { mutableStateOf(false) }
    var builtinPickerVisible by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    fun openRestorePicker() {
        openFileManagerAfterPermission(extensions) {
            if (state.useBuiltinFileManager) {
                builtinPickerVisible = true
            } else {
                systemPicker.launch("*/*")
            }
        }
    }

    if (builtinPickerVisible) {
        BuiltinFilePickerDialog(
            title = "选择软件配置备份",
            allowedExtensions = extensions,
            onDismiss = { builtinPickerVisible = false },
            onPicked = { uri ->
                builtinPickerVisible = false
                pendingRestoreUri = uri
            },
            onOpenSystemPicker = {
                builtinPickerVisible = false
                systemPicker.launch("*/*")
            }
        )
    }
    if (backupDialogVisible) {
        AppConfigBackupOptionsDialog(
            currentFontAvailable =
                state.appFontId != AppFontDefaults.SystemFontId ||
                    (
                        state.lockScreenSettings.useSeparateClockFont &&
                            state.lockScreenSettings.clockFontId != AppFontDefaults.SystemFontId
                        ),
            onDismiss = { backupDialogVisible = false },
            onConfirm = { options ->
                backupDialogVisible = false
                viewModel.exportAppConfigBackup(options)
            }
        )
    }
    pendingRestoreUri?.let { uri ->
        KigttsAlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复应用数据") },
            text = {
                Text("恢复后，应用会回到备份时的设置。备份中未包含的快捷文本、音效板和其它本地内容会保留。部分功能需要重新打开应用后生效。")
            },
            confirmButton = {
                Md2TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        viewModel.importAppConfigBackup(uri)
                    }
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                Md2TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("取消")
                }
            }
        )
    }

    Md2SettingsCard(title = null) {
        AppConfigCommandRow(
            iconName = "backup",
            title = "备份应用数据",
            supportingText = "保存应用设置、快捷名片和锁屏壁纸，可按需包含快捷文本、字体、音效与语音包。",
            enabled = !viewModel.appConfigBackupBusy,
            onClick = { backupDialogVisible = true }
        )
        AppConfigCommandRow(
            iconName = "settings_backup_restore",
            title = "恢复应用数据",
            supportingText = "从备份文件恢复设置与资源，不会删除备份中未包含的本机数据。",
            enabled = !viewModel.appConfigBackupBusy,
            onClick = ::openRestorePicker
        )
    }
}

@Composable
private fun AppConfigBackupOptionsDialog(
    currentFontAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AppConfigBackupOptions) -> Unit
) {
    var quickSubtitle by remember { mutableStateOf(true) }
    var quickCardImages by remember { mutableStateOf(true) }
    var currentFont by remember(currentFontAvailable) { mutableStateOf(currentFontAvailable) }
    var soundboard by remember { mutableStateOf(false) }
    var voicePacks by remember { mutableStateOf(false) }
    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备份应用数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "应用设置、快捷名片信息和锁屏壁纸会自动备份。以下内容可能明显增加文件大小，请按需选择：",
                    style = MaterialTheme.typography.body2
                )
                BackupOptionRow("快捷文本预设", quickSubtitle) { quickSubtitle = it }
                BackupOptionRow("快捷名片图片", quickCardImages) { quickCardImages = it }
                BackupOptionRow(
                    label = if (currentFontAvailable) {
                        "当前使用的字体文件（含时钟字体）"
                    } else {
                        "当前使用系统字体（无需备份）"
                    },
                    checked = currentFont,
                    enabled = currentFontAvailable,
                    onCheckedChange = { currentFont = it }
                )
                BackupOptionRow("音效板预设与音频文件", soundboard) { soundboard = it }
                BackupOptionRow("已安装的本地语音包", voicePacks) { voicePacks = it }
            }
        },
        confirmButton = {
            Md2TextButton(
                onClick = {
                    onConfirm(
                        AppConfigBackupOptions(
                            includeQuickSubtitlePresets = quickSubtitle,
                            includeQuickCardImages = quickCardImages,
                            includeCurrentFont = currentFont,
                            includeSoundboard = soundboard,
                            includeVoicePacks = voicePacks
                        )
                    )
                }
            ) {
                Text("备份")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BackupOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun AppConfigCommandRow(
    iconName: String,
    title: String,
    supportingText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsIcon(
            name = iconName,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.accentText else MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (enabled) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = if (enabled) 0.68f else 0.38f)
            )
        }
        MsIcon(
            name = "chevron_right",
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f)
        )
    }
}
