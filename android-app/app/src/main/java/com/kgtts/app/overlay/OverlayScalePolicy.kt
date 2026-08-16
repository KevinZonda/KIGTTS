package com.lhtstudio.kigtts.app.overlay

import kotlin.math.roundToInt

internal data class OverlayScaleResult(
    val scale: Float,
    val visualWidthPx: Int,
    val visualHeightPx: Int
)

internal object OverlayScalePolicy {
    const val DEFAULT_MINIMUM_SCALE = 0.5f

    fun resolve(
        requiredWidthPx: Int,
        requiredHeightPx: Int,
        safeWidthPx: Int,
        safeHeightPx: Int,
        horizontalMarginPx: Int,
        verticalMarginPx: Int,
        minimumScale: Float = DEFAULT_MINIMUM_SCALE
    ): OverlayScaleResult {
        if (requiredWidthPx <= 0 || requiredHeightPx <= 0) {
            return OverlayScaleResult(1f, requiredWidthPx.coerceAtLeast(0), requiredHeightPx.coerceAtLeast(0))
        }
        val availableWidth = (safeWidthPx - horizontalMarginPx.coerceAtLeast(0) * 2).coerceAtLeast(1)
        val availableHeight = (safeHeightPx - verticalMarginPx.coerceAtLeast(0) * 2).coerceAtLeast(1)
        val requestedScale = minOf(
            1f,
            availableWidth.toFloat() / requiredWidthPx,
            availableHeight.toFloat() / requiredHeightPx
        )
        val scale = requestedScale.coerceAtLeast(minimumScale.coerceIn(0.1f, 1f))
        return OverlayScaleResult(
            scale = scale,
            visualWidthPx = (requiredWidthPx * scale).roundToInt().coerceAtLeast(1),
            visualHeightPx = (requiredHeightPx * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun placeGroupStart(
        logicalStartPx: Int,
        logicalExtentPx: Int,
        visualExtentPx: Int,
        safeStartPx: Int,
        safeEndPx: Int,
        marginPx: Int
    ): Int {
        val minimum = safeStartPx + marginPx.coerceAtLeast(0)
        val maximum = (safeEndPx - marginPx.coerceAtLeast(0) - visualExtentPx).coerceAtLeast(minimum)
        val logicalCenter = logicalStartPx + logicalExtentPx / 2f
        return (logicalCenter - visualExtentPx / 2f).roundToInt().coerceIn(minimum, maximum)
    }

    fun placeCenteredGroupStart(
        preferredCenterPx: Int,
        visualExtentPx: Int,
        safeStartPx: Int,
        safeEndPx: Int,
        marginPx: Int
    ): Int {
        val minimum = safeStartPx + marginPx.coerceAtLeast(0)
        val maximum = (safeEndPx - marginPx.coerceAtLeast(0) - visualExtentPx)
            .coerceAtLeast(minimum)
        return (preferredCenterPx - visualExtentPx / 2f).roundToInt().coerceIn(minimum, maximum)
    }

    fun transformChildStart(
        logicalChildStartPx: Int,
        logicalGroupStartPx: Int,
        visualGroupStartPx: Int,
        scale: Float
    ): Int = visualGroupStartPx +
        ((logicalChildStartPx - logicalGroupStartPx) * scale).roundToInt()
}
