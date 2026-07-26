package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal data class PermissionPurposeInfo(
    val title: String,
    val iconName: String,
    val summary: String,
    val permissionName: String,
    val serviceFeature: String,
    val purpose: String,
    val privacyNote: String,
    val confirmLabel: String = "授权并继续",
    val dismissLabel: String = "取消"
)

@Composable
internal fun PermissionPurposeDialog(
    info: PermissionPurposeInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDontAskAgain: Boolean = false,
    dontAskAgainChecked: Boolean = false,
    onDontAskAgainChange: (Boolean) -> Unit = {}
) {
    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { PermissionDialogTitle(info.title, info.iconName) },
        text = {
            PermissionPurposeDetails(
                info = info,
                showDontAskAgain = showDontAskAgain,
                dontAskAgainChecked = dontAskAgainChecked,
                onDontAskAgainChange = onDontAskAgainChange
            )
        },
        confirmButton = {
            Md2TextButton(onClick = onConfirm) {
                Text(info.confirmLabel)
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text(info.dismissLabel)
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

@Composable
internal fun PermissionDialogTitle(title: String, iconName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.h6
        )
        Spacer(Modifier.width(12.dp))
        MsIcon(
            name = iconName,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            iconSize = 26.dp
        )
    }
}

@Composable
internal fun PermissionPurposeDetails(
    info: PermissionPurposeInfo,
    modifier: Modifier = Modifier,
    showDontAskAgain: Boolean = false,
    dontAskAgainChecked: Boolean = false,
    onDontAskAgainChange: (Boolean) -> Unit = {}
) {
    var detailsExpanded by remember(info.title) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(info.summary, style = MaterialTheme.typography.body2)
        Md2TextButton(
            onClick = { detailsExpanded = !detailsExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (detailsExpanded) "收起详细说明" else "查看详细说明",
                modifier = Modifier.weight(1f)
            )
            MsIcon(
                name = if (detailsExpanded) "expand_less" else "expand_more",
                contentDescription = null
            )
        }
        if (detailsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionPurposeLine("权限名称", info.permissionName)
                PermissionPurposeRange(
                    serviceFeature = info.serviceFeature,
                    purpose = info.purpose
                )
                PermissionPurposeLine("隐私说明", info.privacyNote)
            }
        }
        if (showDontAskAgain) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = dontAskAgainChecked,
                        role = Role.Checkbox,
                        onValueChange = onDontAskAgainChange
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dontAskAgainChecked,
                    onCheckedChange = null
                )
                Text("下次不再提示", style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
private fun PermissionPurposeLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
        Text(value, style = MaterialTheme.typography.body2)
    }
}

@Composable
private fun PermissionPurposeRange(serviceFeature: String, purpose: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("用途与范围", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
        Text(serviceFeature, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.body2)
        Text(purpose, style = MaterialTheme.typography.body2)
    }
}

internal fun recordAudioPermissionPurpose(
    serviceFeature: String,
    purpose: String
): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "需要使用麦克风",
    iconName = "mic",
    summary = "用于在你主动使用语音输入、实时识别或按住说话时采集声音并生成字幕。",
    permissionName = "麦克风权限",
    serviceFeature = serviceFeature,
    purpose = purpose,
    privacyNote = "音频仅用于当前语音识别、按住说话或本地测试流程；KIGTTS 不会上传原始录音。"
)

internal fun cameraScannerPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "需要使用相机",
    iconName = "photo_camera",
    summary = "用于扫码时调用相机预览画面并在本机识别二维码。",
    permissionName = "相机权限",
    serviceFeature = "快捷名片扫一扫 / 二维码扫描",
    purpose = "调用相机预览画面并在本机识别二维码内容。",
    privacyNote = "识别过程在本机完成，KIGTTS 不会上传相机画面或二维码截图。"
)

internal fun notificationPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "需要发送通知",
    iconName = "notifications",
    summary = "用于显示实时字幕、前台运行状态和快捷操作通知。",
    permissionName = "通知权限",
    serviceFeature = "实时通知和前台运行状态提示",
    purpose = "在前台、后台或锁屏时显示当前上屏字幕、运行状态和快捷操作。",
    privacyNote = "通知内容来自你在应用内生成或上屏的字幕文本，可随时在设置中关闭实时通知。"
)

internal fun floatingOverlayPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "需要显示悬浮窗",
    iconName = "picture_in_picture_alt",
    summary = "用于在其它应用上方显示可拖动的快捷入口和字幕/名片内容。",
    permissionName = "悬浮窗权限（显示在其他应用上层）",
    serviceFeature = "独立悬浮窗、迷你字幕、迷你名片和权限步骤提示",
    purpose = "在其它应用上方显示可拖动的快捷入口和字幕/名片内容，便于跨应用操作。",
    privacyNote = "悬浮窗只显示 KIGTTS 本地界面内容，不读取其它应用的文字、账号密码或支付信息。",
    confirmLabel = "前往设置"
)
