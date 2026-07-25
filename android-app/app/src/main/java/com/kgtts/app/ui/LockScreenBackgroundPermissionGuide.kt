package com.lhtstudio.kigtts.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.util.Locale

internal object LockScreenBackgroundPermissionGuide {
    fun isRequiredForDevice(): Boolean {
        val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.ROOT)
        return listOf("xiaomi", "redmi", "poco").any(vendor::contains)
    }

    fun openSettings(context: Context): Boolean {
        val packageName = context.packageName
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                putExtra("extra_pkgname", packageName)
            },
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
        return candidates.any { intent ->
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }
}

@Composable
internal fun LockScreenBackgroundPermissionGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("允许后台显示锁屏") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("小米设备需要额外允许“后台弹出界面”，否则 KIGTTS 退到后台后无法显示自定义锁屏。")
                Text("打开权限设置后，找到“后台弹出界面”并设为“允许”。该权限只用于你已开启的锁屏悬浮窗。")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
        confirmButton = {
            Md2TextButton(onClick = onOpenSettings) {
                Text("打开权限设置")
            }
        }
    )
}
