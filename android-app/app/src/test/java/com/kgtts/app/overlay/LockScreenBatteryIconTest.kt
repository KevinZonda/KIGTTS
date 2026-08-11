package com.lhtstudio.kigtts.app.overlay

import com.lhtstudio.kigtts.app.data.LockScreenBatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LockScreenBatteryIconTest {
    @Test
    fun `battery icon uses android frames by percentage`() {
        assertEquals("battery_android_alert", icon(3))
        assertEquals("battery_android_frame_1", icon(15))
        assertEquals("battery_android_frame_3", icon(45))
        assertEquals("battery_android_frame_6", icon(90))
        assertEquals("battery_android_full", icon(100, full = true))
    }

    @Test
    fun `charging battery uses android bolt`() {
        assertEquals("battery_android_bolt", icon(38, charging = true))
    }

    private fun icon(
        percentage: Int,
        charging: Boolean = false,
        full: Boolean = false
    ): String = lockScreenBatteryMaterialSymbol(
        LockScreenBatteryStatus(
            percentage = percentage,
            isCharging = charging,
            isFull = full
        )
    )
}
