package com.lhtstudio.kigtts.app.overlay

import kotlin.math.max
import kotlin.math.min

internal data class OverlayListeningCardLayout(
    val mainLeftPx: Int,
    val mainTopPx: Int,
    val listeningLeftPx: Int,
    val listeningTopPx: Int,
    val listeningWidthPx: Int,
    val listeningHeightPx: Int
)

internal object OverlayListeningCardLayoutPolicy {
    fun resolve(
        vertical: Boolean,
        safeLeftPx: Int,
        safeTopPx: Int,
        safeRightPx: Int,
        safeBottomPx: Int,
        mainOuterWidthPx: Int,
        mainOuterHeightPx: Int,
        mainPaddingLeftPx: Int,
        mainPaddingTopPx: Int,
        mainPaddingRightPx: Int,
        mainPaddingBottomPx: Int,
        preferredMainLeftPx: Int,
        preferredMainTopPx: Int,
        requestedPortraitListeningHeightPx: Int,
        requestedLandscapeListeningWidthPx: Int,
        minimumListeningExtentPx: Int,
        gapPx: Int,
        edgeInsetPx: Int,
        verticalTopInsetPx: Int,
        verticalBottomInsetPx: Int,
        landscapeCenterXPx: Int,
        listeningOnRight: Boolean
    ): OverlayListeningCardLayout {
        val mainVisibleWidth =
            (mainOuterWidthPx - mainPaddingLeftPx - mainPaddingRightPx).coerceAtLeast(1)
        val mainVisibleHeight =
            (mainOuterHeightPx - mainPaddingTopPx - mainPaddingBottomPx).coerceAtLeast(1)
        if (vertical) {
            val minimumTop = safeTopPx + verticalTopInsetPx
            val maximumListeningHeight =
                safeBottomPx - verticalBottomInsetPx - minimumTop - gapPx - mainVisibleHeight
            val listeningHeight = min(
                requestedPortraitListeningHeightPx,
                max(minimumListeningExtentPx, maximumListeningHeight)
            )
            val groupHeight = listeningHeight + gapPx + mainVisibleHeight
            val maximumGroupTop = safeBottomPx - verticalBottomInsetPx - groupHeight
            val preferredGroupTop =
                preferredMainTopPx + mainPaddingTopPx - gapPx - listeningHeight
            val groupTop = if (maximumGroupTop >= minimumTop) {
                preferredGroupTop.coerceIn(minimumTop, maximumGroupTop)
            } else {
                minimumTop
            }
            val mainVisibleTop = groupTop + listeningHeight + gapPx
            return OverlayListeningCardLayout(
                mainLeftPx = preferredMainLeftPx,
                mainTopPx = mainVisibleTop - mainPaddingTopPx,
                listeningLeftPx = preferredMainLeftPx + mainPaddingLeftPx,
                listeningTopPx = groupTop,
                listeningWidthPx = mainVisibleWidth,
                listeningHeightPx = listeningHeight
            )
        }

        val minimumGroupLeft = safeLeftPx + edgeInsetPx
        val maximumGroupRight = safeRightPx - edgeInsetPx
        val maximumListeningWidth =
            maximumGroupRight - minimumGroupLeft - gapPx - mainVisibleWidth
        val listeningWidth = min(
            requestedLandscapeListeningWidthPx,
            max(minimumListeningExtentPx, maximumListeningWidth)
        )
        val groupWidth = mainVisibleWidth + gapPx + listeningWidth
        val maximumGroupLeft = maximumGroupRight - groupWidth
        val preferredGroupLeft = landscapeCenterXPx - groupWidth / 2
        val groupLeft = if (maximumGroupLeft >= minimumGroupLeft) {
            preferredGroupLeft.coerceIn(minimumGroupLeft, maximumGroupLeft)
        } else {
            minimumGroupLeft
        }
        val mainVisibleLeft = if (listeningOnRight) {
            groupLeft
        } else {
            groupLeft + listeningWidth + gapPx
        }
        val listeningLeft = if (listeningOnRight) {
            groupLeft + mainVisibleWidth + gapPx
        } else {
            groupLeft
        }
        return OverlayListeningCardLayout(
            mainLeftPx = mainVisibleLeft - mainPaddingLeftPx,
            mainTopPx = preferredMainTopPx,
            listeningLeftPx = listeningLeft,
            listeningTopPx = preferredMainTopPx + mainPaddingTopPx,
            listeningWidthPx = listeningWidth,
            listeningHeightPx = mainVisibleHeight
        )
    }
}
