package com.lhtstudio.kigtts.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.ui.MainActivity
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LockScreenMonitorService : Service() {
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var settingsJob: Job? = null
    private var receiverRegistered = false
    private var lockScreenEnabled = false
    private val showRetry = Runnable {
        if (lockScreenEnabled) {
            LockScreenOverlayHostActivity.showIfLocked(this, "screen_on_retry")
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(showRetry)
                    LockScreenOverlayHostActivity.dismiss(this@LockScreenMonitorService)
                }

                Intent.ACTION_SCREEN_ON -> {
                    if (lockScreenEnabled) {
                        LockScreenOverlayHostActivity.showIfLocked(
                            this@LockScreenMonitorService,
                            "screen_on"
                        )
                        handler.removeCallbacks(showRetry)
                        handler.postDelayed(showRetry, SCREEN_ON_RETRY_DELAY_MS)
                    }
                }

                Intent.ACTION_USER_PRESENT -> {
                    handler.removeCallbacks(showRetry)
                    LockScreenOverlayHostActivity.dismiss(this@LockScreenMonitorService)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("LockScreenMonitorService.onCreate")
        val foregroundStarted = runCatching {
            startForegroundInternal()
            true
        }.onFailure {
            AppLogger.e("LockScreenMonitorService.startForeground failed", it)
        }.getOrDefault(false)
        if (!foregroundStarted) {
            stopSelf()
            return
        }
        registerScreenReceiver()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (lockScreenEnabled) {
            LockScreenOverlayHostActivity.showIfLocked(this, "monitor_started")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.i("LockScreenMonitorService.onDestroy")
        lockScreenEnabled = false
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        settingsJob?.cancel()
        settingsJob = null
        scope.cancel()
        LockScreenOverlayHostActivity.dismiss(this)
        super.onDestroy()
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            UserPrefs.observeSettings(this@LockScreenMonitorService).collectLatest { settings ->
                val enabled = settings.floatingOverlayShowOnLockScreen
                lockScreenEnabled = enabled
                if (!enabled) {
                    LockScreenOverlayHostActivity.dismiss(this@LockScreenMonitorService)
                    stopSelf()
                    return@collectLatest
                }
                LockScreenOverlayHostActivity.showIfLocked(
                    this@LockScreenMonitorService,
                    "setting_enabled"
                )
            }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }.onFailure {
            AppLogger.e("LockScreenMonitorService.registerScreenReceiver failed", it)
        }
    }

    private fun startForegroundInternal() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "自定义锁屏",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "KIGTTS 自定义锁屏监听正在运行"
                    setShowBadge(false)
                }
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KIGTTS 自定义锁屏")
            .setContentText("锁屏监听正在运行")
            .setContentIntent(openAppIntent)
            .setColor(0xFF038387.toInt())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "lock_screen_monitor"
        private const val NOTIFICATION_ID = 3210
        private const val SCREEN_ON_RETRY_DELAY_MS = 500L

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LockScreenMonitorService::class.java)
                )
            } else {
                context.stopService(Intent(context, LockScreenMonitorService::class.java))
                LockScreenOverlayHostActivity.dismiss(context)
            }
        }
    }
}
