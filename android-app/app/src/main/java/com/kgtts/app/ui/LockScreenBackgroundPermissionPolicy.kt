package com.lhtstudio.kigtts.app.ui

import java.util.Locale

internal enum class LockScreenBackgroundPermissionVendor {
    XIAOMI,
    VIVO,
    NONE
}

internal data class LockScreenBackgroundPermissionCopy(
    val description: String,
    val instructions: String,
    val settingsEntryLabel: String
)

internal object LockScreenBackgroundPermissionPolicy {
    fun detectVendor(manufacturer: String?, brand: String?): LockScreenBackgroundPermissionVendor {
        val vendor = "$manufacturer $brand".lowercase(Locale.ROOT)
        return when {
            listOf("xiaomi", "redmi", "poco").any(vendor::contains) ->
                LockScreenBackgroundPermissionVendor.XIAOMI

            listOf("vivo", "iqoo").any(vendor::contains) ->
                LockScreenBackgroundPermissionVendor.VIVO

            else -> LockScreenBackgroundPermissionVendor.NONE
        }
    }

    fun copyFor(vendor: LockScreenBackgroundPermissionVendor): LockScreenBackgroundPermissionCopy =
        when (vendor) {
            LockScreenBackgroundPermissionVendor.XIAOMI ->
                LockScreenBackgroundPermissionCopy(
                    description = "小米设备需要允许“锁屏显示”和“后台弹出界面”，否则 KIGTTS 将无法正常显示自定义锁屏。",
                    instructions = "打开权限设置后，将“锁屏显示”和“后台弹出界面”设为“允许”。这些权限只用于显示你已启用的自定义锁屏。",
                    settingsEntryLabel = "锁屏显示权限"
                )

            LockScreenBackgroundPermissionVendor.VIVO ->
                LockScreenBackgroundPermissionCopy(
                    description = "vivo 和 iQOO 设备需要允许“锁屏显示”和“后台弹出界面”，否则 KIGTTS 将无法正常显示自定义锁屏。",
                    instructions = "打开权限设置后，在“设备管理”中开启“锁屏显示”和“后台弹出界面”。这些权限只用于显示你已启用的自定义锁屏。",
                    settingsEntryLabel = "锁屏显示权限"
                )

            LockScreenBackgroundPermissionVendor.NONE ->
                LockScreenBackgroundPermissionCopy(
                    description = "部分设备需要允许锁屏显示或后台弹出界面，以便正常显示自定义锁屏。",
                    instructions = "请在系统应用权限设置中允许 KIGTTS 在锁屏和后台显示界面。",
                    settingsEntryLabel = "后台显示权限"
                )
        }
}
