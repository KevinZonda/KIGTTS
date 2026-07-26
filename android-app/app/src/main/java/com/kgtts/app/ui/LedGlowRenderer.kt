package com.lhtstudio.kigtts.app.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class LedPaintLayers(
    val outerGlow: AndroidPaint?,
    val innerGlow: AndroidPaint?,
    val core: AndroidPaint
)

internal fun createLedPaintLayers(
    source: AndroidPaint,
    glowEnabled: Boolean,
    glowStrength: Float,
    glowRadiusPx: Float
): LedPaintLayers {
    val strength = glowStrength.coerceIn(0f, 1f)
    val core = AndroidPaint(source).apply {
        alpha = 255
        maskFilter = null
        isDither = true
    }
    if (!glowEnabled || strength <= 0f || glowRadiusPx <= 0f) {
        return LedPaintLayers(null, null, core)
    }
    val outer = AndroidPaint(source).apply {
        alpha = ledOuterGlowAlpha(strength)
        maskFilter = BlurMaskFilter(glowRadiusPx, BlurMaskFilter.Blur.NORMAL)
        isDither = true
    }
    val inner = AndroidPaint(source).apply {
        alpha = ledInnerGlowAlpha(strength)
        maskFilter = BlurMaskFilter(
            (glowRadiusPx * 0.42f).coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL
        )
        isDither = true
    }
    return LedPaintLayers(outer, inner, core)
}

internal fun ledTextGlowRadiusPx(
    textSizePx: Float,
    densityScale: Float,
    glowStrength: Float
): Float {
    val contentScale = textSizePx.coerceAtLeast(1f) * 0.022f
    val densityScalePx = densityScale.coerceAtLeast(1f) * 1.75f
    val strengthScale = 0.9f + glowStrength.coerceIn(0f, 1f) * 1.1f
    return ((contentScale + densityScalePx) * strengthScale).coerceAtLeast(1f)
}

internal fun ledDotGlowRadiusPx(pitchPx: Int, glowStrength: Float): Float {
    val strengthScale = 0.9f + glowStrength.coerceIn(0f, 1f) * 1.1f
    return (pitchPx.coerceAtLeast(1) * 0.44f * strengthScale).coerceAtLeast(1f)
}

internal fun ledOuterGlowAlpha(glowStrength: Float): Int =
    (132f * perceptualGlowStrength(glowStrength)).roundToInt().coerceIn(0, 132)

internal fun ledInnerGlowAlpha(glowStrength: Float): Int =
    (214f * perceptualGlowStrength(glowStrength)).roundToInt().coerceIn(0, 214)

private fun perceptualGlowStrength(glowStrength: Float): Float =
    sqrt(glowStrength.coerceIn(0f, 1f))
