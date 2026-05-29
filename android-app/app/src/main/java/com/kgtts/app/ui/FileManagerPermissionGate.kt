package com.lhtstudio.kigtts.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
internal fun rememberFileManagerPermissionGate(): (Set<String>, () -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingPermission = null
        pendingAction = null
        if (granted) {
            action?.invoke()
        } else {
            toast(context, "未授予文件读取权限，无法打开文件管理器")
        }
    }

    pendingPermission?.let { permission ->
        PermissionPurposeDialog(
            info = fileManagerReadPermissionPurpose(permission),
            onConfirm = {
                permissionLauncher.launch(permission)
            },
            onDismiss = {
                pendingPermission = null
                pendingAction = null
            }
        )
    }

    return { allowedExtensions, openFileManager ->
        val normalizedExtensions = allowedExtensions
            .map { it.lowercase(Locale.US).trim('.') }
            .filter { it.isNotBlank() }
            .toSet()
        val permission = builtinReadPermissionForExtensions(normalizedExtensions)
        val hasPermission = permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            openFileManager()
        } else {
            pendingPermission = permission
            pendingAction = openFileManager
        }
    }
}

private fun fileManagerReadPermissionPurpose(permission: String): PermissionPurposeInfo {
    return when (permission) {
        Manifest.permission.READ_MEDIA_AUDIO -> PermissionPurposeInfo(
            permissionName = "音频读取（android.permission.READ_MEDIA_AUDIO）",
            serviceFeature = "选择并导入音频文件",
            purpose = "读取共享媒体库中的音频文件，用于在文件管理器或系统文件选择器中选择并导入语音、音效等音频资源。",
            privacyNote = "仅在你主动选择导入文件时读取本机音频文件，不会自动上传。"
        )
        Manifest.permission.READ_MEDIA_IMAGES -> PermissionPurposeInfo(
            permissionName = "图片读取（android.permission.READ_MEDIA_IMAGES）",
            serviceFeature = "选择并导入图片文件",
            purpose = "读取共享媒体库中的图片文件，用于在文件管理器或系统文件选择器中选择并导入图片资源。",
            privacyNote = "仅在你主动选择导入文件时读取本机图片文件，不会自动上传。"
        )
        else -> PermissionPurposeInfo(
            permissionName = "存储读取（android.permission.READ_EXTERNAL_STORAGE）",
            serviceFeature = "选择并导入本机文件",
            purpose = "读取共享存储中的可导入文件，用于在文件管理器或系统文件选择器中选择语音包、预设包、模型或媒体文件。",
            privacyNote = "仅在你主动选择导入文件时读取本机文件，不会自动上传。"
        )
    }
}
