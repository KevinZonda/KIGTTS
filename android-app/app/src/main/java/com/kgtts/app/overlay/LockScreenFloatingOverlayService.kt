package com.lhtstudio.kigtts.app.overlay

import android.content.Intent
import android.graphics.Rect
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
    private var hostListeningVisibilityListener: ((Boolean) -> Unit)? = null
    private var hostListeningTopClearanceListener: ((Int?) -> Unit)? = null
    private var hostEntryRevealListener: (() -> Unit)? = null
    private var hostEntryRevealDispatched = false
    private var lastReportedPanelVisible = false
    private var lastReportedMiniVisible = false
    private var lastReportedListeningVisible = false
    private var lastReportedListeningTopClearancePx: Int? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun attachHost(
            token: IBinder,
            unlockRequester: ((() -> Unit) -> Unit),
            miniVisibilityListener: (Boolean) -> Unit,
            listeningVisibilityListener: (Boolean) -> Unit,
            listeningTopClearanceListener: (Int?) -> Unit,
            entryRevealListener: () -> Unit
        ) {
            hostToken = token
            hostUnlockRequester = unlockRequester
            hostMiniVisibilityListener = miniVisibilityListener
            hostListeningVisibilityListener = listeningVisibilityListener
            hostListeningTopClearanceListener = listeningTopClearanceListener
            hostEntryRevealListener = entryRevealListener
            hostEntryRevealDispatched = false
            miniVisibilityListener(lastReportedMiniVisible)
            listeningVisibilityListener(lastReportedListeningVisible)
            listeningTopClearanceListener(lastReportedListeningTopClearancePx)
            dispatchHostEntryRevealIfNeeded()
            if (!hostReady.isCompleted) hostReady.complete(Unit)
        }

        fun detachHost() {
            removeAttachedHostWindowsImmediately()
            hostUnlockRequester = null
            hostMiniVisibilityListener = null
            hostListeningVisibilityListener = null
            hostListeningTopClearanceListener = null
            hostEntryRevealListener = null
            hostEntryRevealDispatched = false
            lastReportedPanelVisible = false
            lastReportedMiniVisible = false
            lastReportedListeningVisible = false
            lastReportedListeningTopClearancePx = null
            hostToken = null
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun requiresOverlayPermission(): Boolean = false

    override fun requiresFloatingOverlayEnabled(): Boolean = false

    override fun managesLockScreenHost(): Boolean = false

    override fun observesScreenState(): Boolean = false

    override fun runsAsForegroundService(): Boolean = false

    override fun restartsAfterTaskRemoval(): Boolean = false

    override fun usesAttachedHostWindow(): Boolean = true

    override fun persistsFabAnchors(): Boolean = false

    override fun supportsCollapsedFabState(): Boolean = false

    override fun shouldSyncTopStatusContent(): Boolean = hostEntryRevealDispatched

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
        lastReportedPanelVisible = panelVisible
        if (lastReportedMiniVisible != miniVisible) {
            lastReportedMiniVisible = miniVisible
            hostMiniVisibilityListener?.invoke(miniVisible)
        }
        dispatchHostEntryRevealIfNeeded()
    }

    override fun onListeningOverlayVisibilityChanged(visible: Boolean) {
        if (lastReportedListeningVisible == visible) return
        lastReportedListeningVisible = visible
        hostListeningVisibilityListener?.invoke(visible)
    }

    override fun onListeningOverlayTopClearanceChanged(clearancePx: Int?) {
        if (lastReportedListeningTopClearancePx == clearancePx) return
        lastReportedListeningTopClearancePx = clearancePx
        hostListeningTopClearanceListener?.invoke(clearancePx)
    }

    private fun dispatchHostEntryRevealIfNeeded() {
        if (hostEntryRevealDispatched || (!lastReportedPanelVisible && !lastReportedMiniVisible)) return
        val listener = hostEntryRevealListener ?: return
        hostEntryRevealDispatched = true
        listener()
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
        return when (mode) {
            LockScreenLayoutMode.PhonePortrait -> LockScreenLayoutPolicy.portraitOverlayTopPx(
                screenHeightPx = metrics.heightPixels,
                contentHeightPx = contentHeight,
                preferredTopPx = (220f * metrics.density).toInt(),
                bottomReservePx = (64f * metrics.density).toInt(),
                marginPx = (20f * metrics.density).toInt()
            )
            LockScreenLayoutMode.TabletPortrait -> LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = metrics.heightPixels,
                contentHeightPx = contentHeight,
                verticalBiasPx = 0,
                marginPx = (20f * metrics.density).toInt()
            )
            LockScreenLayoutMode.LargeSquare -> LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = metrics.heightPixels,
                contentHeightPx = contentHeight,
                verticalBiasPx = 0,
                marginPx = (20f * metrics.density).toInt()
            )
            else -> base
        }
    }

    override fun miniOverlayContentTopPx(contentHeight: Int): Int {
        val metrics = resources.displayMetrics
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        if (!mode.isPortrait && mode != LockScreenLayoutMode.LargeSquare) {
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

    override fun usesVerticalListeningOverlayLayout(): Boolean {
        val mode = currentLayoutMode()
        return mode.isPortrait || mode == LockScreenLayoutMode.LargeSquare
    }

    override fun verticalListeningOverlayTopInsetPx(): Int {
        val density = resources.displayMetrics.density
        return when (currentLayoutMode()) {
            LockScreenLayoutMode.PhonePortrait -> (94f * density).toInt()
            LockScreenLayoutMode.LargeSquare -> (88f * density).toInt()
            else -> (12f * density).toInt()
        }
    }

    override fun verticalListeningOverlayBottomInsetPx(): Int {
        val density = resources.displayMetrics.density
        return when (currentLayoutMode()) {
            LockScreenLayoutMode.PhonePortrait,
            LockScreenLayoutMode.LargeSquare -> (68f * density).toInt()
            else -> (12f * density).toInt()
        }
    }

    override fun landscapeListeningOverlayCenterXPx(safeBounds: Rect): Int =
        if (currentLayoutMode() == LockScreenLayoutMode.TabletLandscape) {
            safeBounds.left + safeBounds.width() * 3 / 4
        } else {
            safeBounds.centerX()
        }

    private fun currentLayoutMode(): LockScreenLayoutMode {
        val metrics = resources.displayMetrics
        return LockScreenLayoutPolicy.mode(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
    }
}
