package com.lhtstudio.kigtts.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lhtstudio.kigtts.app.data.LockScreenBatteryStatus
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import com.lhtstudio.kigtts.app.data.formatBatteryStatus
import com.lhtstudio.kigtts.app.data.normalized
import com.lhtstudio.kigtts.app.data.shouldShowBatteryStatus
import kotlin.math.roundToInt

internal class LockScreenBatteryController(
    context: Context,
    private val batteryView: TextView
) {
    private var settings = LockScreenSettings()
    private var status = LockScreenBatteryStatus(
        percentage = -1,
        isCharging = false,
        isFull = false
    )
    private val monitor = LockScreenBatteryMonitor(context) { updated ->
        status = updated
        render()
    }

    fun start() = monitor.start()

    fun stop() = monitor.stop()

    fun applySettings(settings: LockScreenSettings) {
        this.settings = settings.normalized()
        render()
    }

    private fun render() {
        batteryView.text = settings.formatBatteryStatus(status)
        batteryView.visibility = if (settings.shouldShowBatteryStatus(status)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}

internal class LockScreenBatteryMonitor(
    private val context: Context,
    private val onStatusChanged: (LockScreenBatteryStatus) -> Unit
) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.toBatteryStatus()?.let(onStatusChanged)
        }
    }

    fun start() {
        if (registered) return
        val sticky = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        }.onSuccess {
            registered = true
        }.getOrNull()
        sticky?.toBatteryStatus()?.let(onStatusChanged)
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}

private fun Intent.toBatteryStatus(): LockScreenBatteryStatus? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val batteryStatus = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return LockScreenBatteryStatus(
        percentage = ((level.toFloat() / scale) * 100f).roundToInt().coerceIn(0, 100),
        isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL,
        isFull = batteryStatus == BatteryManager.BATTERY_STATUS_FULL
    )
}
