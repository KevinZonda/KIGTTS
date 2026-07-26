package com.lhtstudio.kigtts.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable

internal object LockScreenBackgroundPermissionGuide {
    fun vendorForDevice(): LockScreenBackgroundPermissionVendor =
        LockScreenBackgroundPermissionPolicy.detectVendor(Build.MANUFACTURER, Build.BRAND)

    fun openSettings(context: Context): Boolean {
        val packageName = context.packageName
        val vendorCandidates = when (vendorForDevice()) {
            LockScreenBackgroundPermissionVendor.XIAOMI -> listOf(
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
                }
            )

            LockScreenBackgroundPermissionVendor.VIVO -> listOf(
                Intent("permission.intent.action.softPermissionDetail").apply {
                    setClassName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                    putExtra("packagename", packageName)
                },
                Intent("secure.intent.action.softPermissionDetail").apply {
                    setClassName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.safeguard.SoftPermissionDetailActivity"
                    )
                    putExtra("packagename", packageName)
                }
            )

            LockScreenBackgroundPermissionVendor.NONE -> emptyList()
        }
        val candidates = vendorCandidates + Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
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
    vendor: LockScreenBackgroundPermissionVendor,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val copy = LockScreenBackgroundPermissionPolicy.copyFor(vendor)
    PermissionPurposeDialog(
        info = PermissionPurposeInfo(
            title = "允许锁屏与后台显示",
            iconName = "lock",
            summary = copy.description,
            permissionName = copy.settingsEntryLabel,
            serviceFeature = "自定义锁屏",
            purpose = copy.instructions,
            privacyNote = "仅用于显示你主动启用的自定义锁屏，不会读取锁屏通知、密码或其它应用内容。",
            confirmLabel = "打开权限设置",
            dismissLabel = "稍后"
        ),
        onConfirm = onOpenSettings,
        onDismiss = onDismiss
    )
}
