package com.lhtstudio.kigtts.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal data class AllFilesAccessController(
    val supported: Boolean,
    val granted: Boolean,
    val openSettings: () -> Unit
)

internal fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

@Composable
internal fun rememberAllFilesAccessController(): AllFilesAccessController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    var showPurpose by remember { mutableStateOf(false) }

    fun refresh() {
        granted = hasAllFilesAccess()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }

    fun launchSettings() {
        val launched = runCatching {
            settingsLauncher.launch(allFilesAccessSettingsIntent(context))
        }.isSuccess
        if (!launched) {
            toast(context, "无法打开全部文件访问权限设置")
        }
    }

    DisposableEffect(lifecycleOwner, supported) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPurpose) {
        PermissionPurposeDialog(
            info = allFilesAccessPermissionPurpose(),
            onConfirm = {
                showPurpose = false
                launchSettings()
            },
            onDismiss = { showPurpose = false }
        )
    }

    return AllFilesAccessController(
        supported = supported,
        granted = granted,
        openSettings = {
            if (granted) launchSettings() else showPurpose = true
        }
    )
}

private fun allFilesAccessSettingsIntent(context: Context): Intent {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    if (appIntent.resolveActivity(context.packageManager) != null) return appIntent
    return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

private fun allFilesAccessPermissionPurpose(): PermissionPurposeInfo = PermissionPurposeInfo(
    title = "允许管理全部文件",
    iconName = "folder_open",
    summary = "开启后，内置文件管理器可以一次访问共享存储，不再逐个目录授权。",
    permissionName = "所有文件访问权限",
    serviceFeature = "内置文件管理器浏览和导入本机资源",
    purpose = "仅在你主动打开文件管理器时浏览共享存储中的文件和目录，用于选择并导入语音包、模型、预设、字体、音频或图片。",
    privacyNote = "KIGTTS 不会在后台扫描或上传你的文件。拒绝此权限后，仍可使用目录授权和系统文件选择器。",
    confirmLabel = "前往系统设置"
)
