package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal data class PermissionPurposeInfo(
    val permissionName: String,
    val serviceFeature: String,
    val purpose: String,
    val privacyNote: String,
    val confirmLabel: String = "允许并继续"
)

@Composable
internal fun PermissionPurposeDialog(
    info: PermissionPurposeInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限申请说明") },
        text = { PermissionPurposeDetails(info) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(info.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

@Composable
internal fun PermissionPurposeDetails(
    info: PermissionPurposeInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PermissionPurposeLine("权限名称", info.permissionName)
        PermissionPurposeLine("服务功能", info.serviceFeature)
        PermissionPurposeLine("用途说明", info.purpose)
        PermissionPurposeLine("隐私说明", info.privacyNote)
        Text(
            "该说明不会自动消失，请确认理解用途后再继续授权。",
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun PermissionPurposeLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
        Text(value, style = MaterialTheme.typography.body2)
    }
}

internal fun recordAudioPermissionPurpose(
    serviceFeature: String,
    purpose: String
): PermissionPurposeInfo = PermissionPurposeInfo(
    permissionName = "麦克风/录音（android.permission.RECORD_AUDIO）",
    serviceFeature = serviceFeature,
    purpose = purpose,
    privacyNote = "音频仅用于当前语音识别、按住说话或本地测试流程；KIGTTS 不会上传原始录音。"
)

internal fun cameraScannerPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    permissionName = "相机（android.permission.CAMERA）",
    serviceFeature = "快捷名片扫一扫 / 二维码扫描",
    purpose = "调用相机预览画面并在本机识别二维码内容。",
    privacyNote = "识别过程在本机完成，KIGTTS 不会上传相机画面或二维码截图。"
)

internal fun notificationPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    permissionName = "通知（android.permission.POST_NOTIFICATIONS）",
    serviceFeature = "实时通知和前台运行状态提示",
    purpose = "在前台、后台或锁屏时显示当前上屏字幕、运行状态和快捷操作。",
    privacyNote = "通知内容来自你在应用内生成或上屏的字幕文本，可随时在设置中关闭实时通知。"
)

internal fun floatingOverlayPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    permissionName = "悬浮窗/显示在其他应用上层（android.permission.SYSTEM_ALERT_WINDOW）",
    serviceFeature = "独立悬浮窗、迷你字幕、迷你名片和权限步骤提示",
    purpose = "在其它应用上方显示可拖动的快捷入口和字幕/名片内容，便于跨应用操作。",
    privacyNote = "悬浮窗只显示 KIGTTS 本地界面内容，不读取其它应用的文字、账号密码或支付信息。",
    confirmLabel = "前往设置"
)
