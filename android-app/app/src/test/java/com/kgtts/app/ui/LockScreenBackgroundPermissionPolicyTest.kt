package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenBackgroundPermissionPolicyTest {
    @Test
    fun `detects Xiaomi family devices`() {
        assertEquals(
            LockScreenBackgroundPermissionVendor.XIAOMI,
            LockScreenBackgroundPermissionPolicy.detectVendor("Xiaomi", "Redmi")
        )
        assertEquals(
            LockScreenBackgroundPermissionVendor.XIAOMI,
            LockScreenBackgroundPermissionPolicy.detectVendor("POCO", "POCO")
        )
    }

    @Test
    fun `detects vivo and iQOO devices`() {
        assertEquals(
            LockScreenBackgroundPermissionVendor.VIVO,
            LockScreenBackgroundPermissionPolicy.detectVendor("vivo", "vivo")
        )
        assertEquals(
            LockScreenBackgroundPermissionVendor.VIVO,
            LockScreenBackgroundPermissionPolicy.detectVendor("BBK", "iQOO")
        )
    }

    @Test
    fun `does not require a vendor guide on other devices`() {
        assertEquals(
            LockScreenBackgroundPermissionVendor.NONE,
            LockScreenBackgroundPermissionPolicy.detectVendor("samsung", "samsung")
        )
    }

    @Test
    fun `vivo guide names both required controls`() {
        val copy = LockScreenBackgroundPermissionPolicy.copyFor(
            LockScreenBackgroundPermissionVendor.VIVO
        )

        assertTrue(copy.instructions.contains("锁屏显示"))
        assertTrue(copy.instructions.contains("后台弹出界面"))
        assertEquals("锁屏显示权限", copy.settingsEntryLabel)
    }
}
