package com.lhtstudio.kigtts.app.lan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.lhtstudio.kigtts.app.util.AppLogger

internal object LanCastBackgroundAccess {
    fun isGranted(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun openSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val directRequest = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri
        )
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val intent = directRequest.takeIf {
            it.resolveActivity(context.packageManager) != null
        } ?: fallback
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            AppLogger.e("LanCast background settings unavailable", it)
        }
    }
}
