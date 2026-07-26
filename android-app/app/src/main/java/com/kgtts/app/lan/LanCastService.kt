package com.lhtstudio.kigtts.app.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.overlay.OverlayBridge
import com.lhtstudio.kigtts.app.service.RealtimeHostService
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class LanCastService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val powerGuard by lazy(LazyThreadSafetyMode.NONE) { LanCastPowerGuard(this) }
    private var statusJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        RealtimeHostService.ensureStarted(applicationContext)
        if (!LanCastRuntime.startServer(applicationContext)) {
            stopSelf()
            return
        }
        powerGuard.acquire()
        observeStatus()
        observeNetworks()
        AppLogger.i("LanCastService started port=${LanCastRuntime.DEFAULT_PORT}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NETWORKS -> LanCastRuntime.refreshAddresses()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        statusJob?.cancel()
        statusJob = null
        networkCallback?.let { callback ->
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        connectivityManager = null
        LanCastRuntime.stopServer()
        powerGuard.release()
        serviceScope.cancel()
        AppLogger.i("LanCastService stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeStatus() {
        statusJob = serviceScope.launch {
            LanCastRuntime.statusFlow().collectLatest {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun observeNetworks() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshNetworks()
            override fun onLost(network: Network) = refreshNetworks()
        }
        networkCallback = callback
        runCatching {
            manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        }.onFailure {
            AppLogger.e("LanCast network callback unavailable", it)
        }
    }

    private fun refreshNetworks() {
        serviceScope.launch {
            LanCastRuntime.refreshAddresses()
        }
    }

    private fun buildNotification(): Notification {
        val status = LanCastRuntime.status()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            OverlayBridge.buildOpenPageIntent(
                this,
                OverlayBridge.TARGET_OPEN_LAN_CAST
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LanCastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clients = status.displayClients + status.remoteClients
        val detail = status.url("display")?.let { url ->
            if (clients > 0) "$clients 个网页已连接 · $url" else "等待连接 · $url"
        } ?: "等待局域网连接"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_subtitle_notification_logo)
            .setContentTitle("投屏与遥控已开启")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止投屏", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "投屏与遥控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "局域网字幕投屏和网页遥控服务的运行状态"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "lan_cast_service"
        private const val NOTIFICATION_ID = 0x4B47_31
        private const val ACTION_STOP = "com.lhtstudio.kigtts.app.lan.STOP"
        private const val ACTION_REFRESH_NETWORKS = "com.lhtstudio.kigtts.app.lan.REFRESH"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LanCastService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LanCastService::class.java))
        }

        fun refresh(context: Context) {
            context.startService(
                Intent(context, LanCastService::class.java).setAction(ACTION_REFRESH_NETWORKS)
            )
        }
    }
}
