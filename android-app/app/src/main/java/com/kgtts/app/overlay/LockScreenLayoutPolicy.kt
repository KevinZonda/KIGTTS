package com.lhtstudio.kigtts.app.overlay

import kotlin.math.max
import kotlin.math.min

internal enum class LockScreenLayoutMode {
    PhonePortrait,
    PhoneLandscape,
    TabletPortrait,
    TabletLandscape;

    val isPortrait: Boolean
        get() = this == PhonePortrait || this == TabletPortrait
}

internal object LockScreenLayoutPolicy {
    private const val TABLET_MIN_WIDTH_DP = 600f

    fun hideClock(mode: LockScreenLayoutMode, miniOverlayVisible: Boolean): Boolean =
        mode == LockScreenLayoutMode.PhonePortrait && miniOverlayVisible

    fun mode(
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float
    ): LockScreenLayoutMode {
        val safeDensity = density.takeIf { it > 0f } ?: 1f
        val smallestWidthDp = minOf(screenWidthPx, screenHeightPx) / safeDensity
        return when {
            smallestWidthDp >= TABLET_MIN_WIDTH_DP && screenWidthPx > screenHeightPx ->
                LockScreenLayoutMode.TabletLandscape
            smallestWidthDp >= TABLET_MIN_WIDTH_DP -> LockScreenLayoutMode.TabletPortrait
            screenWidthPx > screenHeightPx -> LockScreenLayoutMode.PhoneLandscape
            else -> LockScreenLayoutMode.PhonePortrait
        }
    }

    fun overlayLeftPx(
        mode: LockScreenLayoutMode,
        screenWidthPx: Int,
        contentWidthPx: Int,
        sideMarginPx: Int
    ): Int {
        val maxLeft = max(sideMarginPx, screenWidthPx - contentWidthPx - sideMarginPx)
        val requested = when (mode) {
            LockScreenLayoutMode.PhonePortrait,
            LockScreenLayoutMode.TabletPortrait -> (screenWidthPx - contentWidthPx) / 2
            LockScreenLayoutMode.PhoneLandscape,
            LockScreenLayoutMode.TabletLandscape -> screenWidthPx - contentWidthPx - sideMarginPx
        }
        return requested.coerceIn(sideMarginPx, maxLeft)
    }

    fun overlayWidthPx(
        mode: LockScreenLayoutMode,
        screenWidthPx: Int,
        density: Float,
        sideMarginPx: Int
    ): Int {
        val phoneWidth = (360f * density).toInt()
        val phoneLandscapeWidth = (540f * density).toInt()
        val tabletWidth = (400f * density).toInt()
        val minimumWidth = (280f * density).toInt()
        val availableWidth = when {
            mode == LockScreenLayoutMode.TabletLandscape ->
                screenWidthPx / 2 - sideMarginPx * 2
            else -> screenWidthPx - sideMarginPx * 2
        }
        val requestedWidth = when (mode) {
            LockScreenLayoutMode.PhonePortrait -> phoneWidth
            LockScreenLayoutMode.PhoneLandscape -> phoneLandscapeWidth
            LockScreenLayoutMode.TabletPortrait,
            LockScreenLayoutMode.TabletLandscape -> tabletWidth
        }
        return min(availableWidth, requestedWidth).coerceAtLeast(minimumWidth)
    }

    fun hostColumnWidthPx(
        overlayLeftPx: Int,
        contentStartInsetPx: Int,
        startMarginPx: Int,
        overlayGapPx: Int,
        minimumWidthPx: Int
    ): Int = (overlayLeftPx - overlayGapPx - contentStartInsetPx - startMarginPx)
        .coerceAtLeast(minimumWidthPx)

    fun portraitOverlayTopPx(
        screenHeightPx: Int,
        contentHeightPx: Int,
        preferredTopPx: Int,
        bottomReservePx: Int,
        marginPx: Int
    ): Int {
        val maxTop = (screenHeightPx - contentHeightPx - bottomReservePx)
            .coerceAtLeast(marginPx)
        return preferredTopPx.coerceIn(marginPx, maxTop)
    }

    fun centeredOverlayTopPx(
        screenHeightPx: Int,
        contentHeightPx: Int,
        verticalBiasPx: Int,
        marginPx: Int
    ): Int {
        val maxTop = (screenHeightPx - contentHeightPx - marginPx)
            .coerceAtLeast(marginPx)
        return ((screenHeightPx - contentHeightPx) / 2 - verticalBiasPx)
            .coerceIn(marginPx, maxTop)
    }
}
