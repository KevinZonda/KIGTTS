package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.lhtstudio.kigtts.app.util.AppLogger

internal object LockScreenSystemAppLauncher {
    fun openClock(context: Context): Boolean {
        val handlerPackages = listOf(
            AlarmClock.ACTION_SHOW_ALARMS,
            AlarmClock.ACTION_SET_ALARM
        ).flatMap { action -> queryHandlerPackages(context.packageManager, action) }
        val packageCandidates = (handlerPackages + knownClockPackages).distinct()
            .mapNotNull(context.packageManager::getLaunchIntentForPackage)
        return launchFirstAvailable(
            context,
            packageCandidates + listOf(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                Intent(AlarmClock.ACTION_SET_ALARM)
            ),
            label = "clock"
        )
    }

    fun openCalendar(
        context: Context,
        timestampMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val currentTimeUri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(timestampMillis.toString())
            .build()
        return launchFirstAvailable(
            context,
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
                Intent(Intent.ACTION_VIEW, currentTimeUri)
            ),
            label = "calendar"
        )
    }

    private fun launchFirstAvailable(
        context: Context,
        candidates: List<Intent>,
        label: String
    ): Boolean {
        candidates.forEachIndexed { index, candidate ->
            val intent = Intent(candidate).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            val result = runCatching { context.startActivity(intent) }
            if (result.isSuccess) {
                AppLogger.i("Lock screen $label launch succeeded: ${intent.component ?: intent.action}")
                return true
            }
            AppLogger.w(
                "Lock screen $label launch candidate ${index + 1} failed: " +
                    result.exceptionOrNull()?.message.orEmpty()
            )
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun queryHandlerPackages(packageManager: PackageManager, action: String): List<String> =
        packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }

    private val knownClockPackages = listOf(
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.oneplus.deskclock",
        "com.android.BBKClock",
        "com.vivo.alarmclock",
        "com.huawei.deskclock",
        "com.hihonor.deskclock"
    )
}
