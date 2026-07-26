package com.lhtstudio.kigtts.app.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.lhtstudio.kigtts.app.util.AppLogger

internal class LanCastPowerGuard(context: Context) {
    private val appContext = context.applicationContext
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        acquireCpuWakeLock()
        acquireWifiLock()
    }

    fun release() {
        releaseWifiLock()
        releaseCpuWakeLock()
    }

    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        runCatching {
            val powerManager = appContext.getSystemService(PowerManager::class.java)
                ?: return@runCatching
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                CPU_WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire()
                cpuWakeLock = this
            }
        }.onFailure {
            AppLogger.e("LanCast CPU wake lock unavailable", it)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            val wifiManager = appContext.getSystemService(WifiManager::class.java)
                ?: return@runCatching
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                WIFI_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire()
                wifiLock = this
            }
        }.onFailure {
            AppLogger.e("LanCast Wi-Fi lock unavailable", it)
        }
    }

    private fun releaseCpuWakeLock() {
        runCatching {
            cpuWakeLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            AppLogger.e("LanCast CPU wake lock release failed", it)
        }
        cpuWakeLock = null
    }

    private fun releaseWifiLock() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            AppLogger.e("LanCast Wi-Fi lock release failed", it)
        }
        wifiLock = null
    }

    private companion object {
        const val CPU_WAKE_LOCK_TAG = "KIGTTS:LanCastCpu"
        const val WIFI_LOCK_TAG = "KIGTTS:LanCastWifi"
    }
}
