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
import kotlin.math.roundToInt

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
        val bounds = displayBounds()
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
        return LockScreenLayoutPolicy.overlayDesignWidthPx(
            mode = mode,
            density = metrics.density
        )
    }

    override fun overlayContentLeftPx(contentWidth: Int): Int {
        val metrics = resources.displayMetrics
        val bounds = displayBounds()
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
        return LockScreenLayoutPolicy.overlayLeftPx(
            mode = mode,
            screenWidthPx = bounds.width(),
            contentWidthPx = contentWidth,
            sideMarginPx = (16f * metrics.density).toInt()
        )
    }

    override fun overlayContentTopPx(contentHeight: Int): Int {
        val base = super.overlayContentTopPx(contentHeight)
        val metrics = resources.displayMetrics
        val bounds = displayBounds()
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
        return when (mode) {
            LockScreenLayoutMode.PhoneLandscape -> LockScreenLayoutPolicy.phoneLandscapeOverlayTopPx(
                baseTopPx = base,
                density = metrics.density
            )
            LockScreenLayoutMode.PhonePortrait -> LockScreenLayoutPolicy.portraitOverlayTopPx(
                screenHeightPx = bounds.height(),
                contentHeightPx = contentHeight,
                preferredTopPx = (
                    if (isLargePhonePortrait()) 260f else 220f
                ).times(metrics.density).toInt(),
                bottomReservePx = (64f * metrics.density).toInt(),
                marginPx = (20f * metrics.density).toInt()
            )
            LockScreenLayoutMode.TabletPortrait -> LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = bounds.height(),
                contentHeightPx = contentHeight,
                verticalBiasPx = 0,
                marginPx = (20f * metrics.density).toInt()
            )
            LockScreenLayoutMode.LargeSquare -> LockScreenLayoutPolicy.centeredOverlayTopPx(
                screenHeightPx = bounds.height(),
                contentHeightPx = contentHeight,
                verticalBiasPx = 0,
                marginPx = (20f * metrics.density).toInt()
            )
            else -> base
        }
    }

    override fun miniOverlayContentTopPx(contentHeight: Int): Int {
        val metrics = resources.displayMetrics
        val bounds = displayBounds()
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
        if (!mode.isPortrait && mode != LockScreenLayoutMode.LargeSquare) {
            val base = super.miniOverlayContentTopPx(contentHeight)
            return if (mode == LockScreenLayoutMode.PhoneLandscape) {
                LockScreenLayoutPolicy.phoneLandscapeOverlayTopPx(
                    baseTopPx = base,
                    density = metrics.density
                )
            } else {
                base
            }
        }
        return LockScreenLayoutPolicy.centeredOverlayTopPx(
            screenHeightPx = bounds.height(),
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

    override fun listeningOverlaySafeBounds(safeBounds: Rect): Rect {
        if (currentLayoutMode() != LockScreenLayoutMode.PhoneLandscape) {
            return super.listeningOverlaySafeBounds(safeBounds)
        }
        return Rect(safeBounds).apply {
            left = LockScreenLayoutPolicy.phoneLandscapeListeningSafeLeftPx(
                safeLeftPx = safeBounds.left,
                safeRightPx = safeBounds.right,
                density = resources.displayMetrics.density
            )
        }
    }

    override fun landscapeListeningOverlayMinimumWidthPx(): Int =
        if (currentLayoutMode() == LockScreenLayoutMode.PhoneLandscape) {
            (196f * resources.displayMetrics.density).toInt()
        } else {
            super.landscapeListeningOverlayMinimumWidthPx()
        }

    override fun portraitListeningOverlayMinimumHeightPx(): Int =
        if (currentLayoutMode() == LockScreenLayoutMode.PhonePortrait) {
            (144f * resources.displayMetrics.density).toInt()
        } else {
            super.portraitListeningOverlayMinimumHeightPx()
        }

    override fun listeningOverlayGroupVerticalOffsetPx(): Int {
        val density = resources.displayMetrics.density
        return when (currentLayoutMode()) {
            LockScreenLayoutMode.PhonePortrait -> {
                val offsetDp = LockScreenLayoutPolicy.phonePortraitListeningGroupOffsetDp(
                    largePhone = isLargePhonePortrait(),
                    launcherVisible = isExpandedLauncherOverlayVisible()
                )
                (offsetDp * density).toInt()
            }
            LockScreenLayoutMode.PhoneLandscape -> (-24f * density).toInt()
            else -> 0
        }
    }

    override fun listeningOverlayGroupTopPx(
        currentTopPx: Int,
        groupHeightPx: Int,
        safeBounds: Rect,
        minimumTopPx: Int,
        maximumTopPx: Int
    ): Int {
        if (
            currentLayoutMode() == LockScreenLayoutMode.PhonePortrait &&
            isLargePhonePortrait() &&
            isExpandedLauncherOverlayVisible()
        ) {
            val density = resources.displayMetrics.density
            return LockScreenLayoutPolicy.centeredPortraitLauncherGroupTopPx(
                currentTopPx = currentTopPx,
                groupHeightPx = groupHeightPx,
                safeTopPx = safeBounds.top,
                safeBottomPx = safeBounds.bottom,
                topReservePx = (72f * density).roundToInt(),
                bottomReservePx = (96f * density).roundToInt(),
                minimumTopPx = minimumTopPx,
                maximumTopPx = maximumTopPx
            )
        }
        return super.listeningOverlayGroupTopPx(
            currentTopPx = currentTopPx,
            groupHeightPx = groupHeightPx,
            safeBounds = safeBounds,
            minimumTopPx = minimumTopPx,
            maximumTopPx = maximumTopPx
        )
    }

    override fun verticalListeningOverlayTopInsetPx(): Int {
        val density = resources.displayMetrics.density
        return when (currentLayoutMode()) {
            LockScreenLayoutMode.PhonePortrait -> {
                val insetDp = if (isExpandedLauncherOverlayVisible()) 20f else 4f
                (insetDp * density).toInt()
            }
            LockScreenLayoutMode.LargeSquare -> (88f * density).toInt()
            else -> (12f * density).toInt()
        }
    }

    override fun portraitListeningOverlayHeightPx(safeBounds: Rect): Int {
        return super.portraitListeningOverlayHeightPx(safeBounds)
    }

    override fun preservePortraitListeningOverlayHeight(): Boolean = false

    override fun verticalListeningOverlayBottomInsetPx(): Int {
        val density = resources.displayMetrics.density
        return when (currentLayoutMode()) {
            LockScreenLayoutMode.PhonePortrait,
            LockScreenLayoutMode.LargeSquare -> (96f * density).toInt()
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
        val bounds = displayBounds()
        return LockScreenLayoutPolicy.mode(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
    }

    private fun isLargePhonePortrait(): Boolean {
        val metrics = resources.displayMetrics
        val bounds = displayBounds()
        return LockScreenLayoutPolicy.isLargePhonePortrait(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = metrics.density
        )
    }
}
