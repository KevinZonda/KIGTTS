package com.lhtstudio.kigtts.app.overlay

import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CompletableDeferred

/** A complete second overlay instance attached to the transparent lock-screen Activity. */
internal class LockScreenFloatingOverlayService : FloatingOverlayService() {
    private val hostReady = CompletableDeferred<Unit>()
    private var hostToken: IBinder? = null
    private var hostUnlockRequester: (((() -> Unit) -> Unit))? = null
    private var hostMiniVisibilityListener: ((Boolean) -> Unit)? = null
    private var lastReportedMiniVisible = false
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun attachHost(
            token: IBinder,
            unlockRequester: ((() -> Unit) -> Unit),
            miniVisibilityListener: (Boolean) -> Unit
        ) {
            hostToken = token
            hostUnlockRequester = unlockRequester
            hostMiniVisibilityListener = miniVisibilityListener
            miniVisibilityListener(lastReportedMiniVisible)
            if (!hostReady.isCompleted) hostReady.complete(Unit)
        }

        fun detachHost() {
            removeAttachedHostWindowsImmediately()
            hostUnlockRequester = null
            hostMiniVisibilityListener = null
            hostToken = null
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun requiresOverlayPermission(): Boolean = false

    override fun managesLockScreenHost(): Boolean = false

    override fun runsAsForegroundService(): Boolean = false

    override fun restartsAfterTaskRemoval(): Boolean = false

    override fun usesAttachedHostWindow(): Boolean = true

    override fun persistsFabAnchors(): Boolean = false

    override fun supportsCollapsedFabState(): Boolean = false

    override fun runAfterOverlayHostUnlock(action: () -> Unit): Boolean {
        val requester = hostUnlockRequester
        if (requester == null) {
            AppLogger.w("Lock screen overlay launch ignored because host is unavailable")
            return false
        }
        requester(action)
        return true
    }

    override fun onOverlayVisibilityChanged(panelVisible: Boolean, miniVisible: Boolean) {
        if (lastReportedMiniVisible == miniVisible) return
        lastReportedMiniVisible = miniVisible
        hostMiniVisibilityListener?.invoke(miniVisible)
    }

    override suspend fun awaitOverlayHostReady() {
        hostReady.await()
    }

    override fun overlayWindowToken(): IBinder? = hostToken

    override fun overlayWindowType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL

    override fun overlayContentWidthPx(phoneMaxDp: Int, tabletMaxDp: Int): Int {
        val metrics = resources.displayMetrics
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        return LockScreenLayoutPolicy.overlayWidthPx(
            mode = mode,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density,
            sideMarginPx = (16f * metrics.density).toInt()
        )
    }

    override fun overlayContentLeftPx(contentWidth: Int): Int {
        val metrics = resources.displayMetrics
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        return LockScreenLayoutPolicy.overlayLeftPx(
            mode = mode,
            screenWidthPx = metrics.widthPixels,
            contentWidthPx = contentWidth,
            sideMarginPx = (16f * metrics.density).toInt()
        )
    }

    override fun overlayContentTopPx(contentHeight: Int): Int {
        val base = super.overlayContentTopPx(contentHeight)
        val metrics = resources.displayMetrics
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        if (mode != LockScreenLayoutMode.PhonePortrait) return base
        return LockScreenLayoutPolicy.portraitOverlayTopPx(
            screenHeightPx = metrics.heightPixels,
            contentHeightPx = contentHeight,
            preferredTopPx = (220f * metrics.density).toInt(),
            bottomReservePx = (64f * metrics.density).toInt(),
            marginPx = (20f * metrics.density).toInt()
        )
    }

    override fun miniOverlayContentTopPx(contentHeight: Int): Int {
        val metrics = resources.displayMetrics
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        if (mode != LockScreenLayoutMode.PhonePortrait) {
            return super.miniOverlayContentTopPx(contentHeight)
        }
        return LockScreenLayoutPolicy.centeredOverlayTopPx(
            screenHeightPx = metrics.heightPixels,
            contentHeightPx = contentHeight,
            verticalBiasPx = (16f * metrics.density).toInt(),
            marginPx = (20f * metrics.density).toInt()
        )
    }

    override fun onOverlayWindowsInitialized() {
        Handler(Looper.getMainLooper()).post(::requestExpandedPanel)
    }
}
