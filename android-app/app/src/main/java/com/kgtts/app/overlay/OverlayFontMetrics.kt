package com.lhtstudio.kigtts.app.overlay

import kotlin.math.ceil

internal fun resolveOverlayStableLineHeightPx(
    textSizePx: Float,
    scaledDensity: Float,
    explicitMultiplier: Float? = null
): Int {
    val safeTextSizePx = textSizePx.coerceAtLeast(1f)
    val safeScaledDensity = scaledDensity.coerceAtLeast(0.01f)
    val targetPx = if (explicitMultiplier != null) {
        safeTextSizePx * explicitMultiplier.coerceAtLeast(1f)
    } else {
        val textSizeSp = safeTextSizePx / safeScaledDensity
        stableOverlayLineHeightSp(textSizeSp) * safeScaledDensity
    }
    return ceil(targetPx).toInt().coerceAtLeast(1)
}

private fun stableOverlayLineHeightSp(textSizeSp: Float): Float = when {
    textSizeSp <= 12.5f -> 16f
    textSizeSp <= 14.5f -> 20f
    textSizeSp <= 16.5f -> 24f
    textSizeSp <= 20.5f -> 24f
    textSizeSp <= 24.5f -> 32f
    textSizeSp <= 34.5f -> 40f
    textSizeSp <= 48.5f -> 56f
    textSizeSp <= 60.5f -> 72f
    else -> textSizeSp * 1.15f
}
