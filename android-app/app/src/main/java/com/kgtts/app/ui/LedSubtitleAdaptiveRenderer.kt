package com.lhtstudio.kigtts.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun LedAdaptiveSubtitleDisplay(
    text: String,
    settings: LedSubtitleSettings,
    typeface: Typeface,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val densityScale = LocalDensity.current.density
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeightPx = with(LocalDensity.current) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val normalizedText = remember(text) { normalizeAdaptiveLedText(text) }
        val defaults = remember { LedSubtitleSettings() }
        val renderSettings = remember(settings) {
            settings.copy(
                scrollSpeedDpPerSecond = defaults.scrollSpeedDpPerSecond,
                scrollDirection = defaults.scrollDirection,
                quickSwipeOpensQuickText = defaults.quickSwipeOpensQuickText,
                loopGapDp = defaults.loopGapDp,
                keepScreenOn = defaults.keepScreenOn,
                followSystemBrightness = defaults.followSystemBrightness,
                screenBrightness = defaults.screenBrightness
            )
        }
        val frame by produceState<ImageBitmap?>(
            initialValue = null,
            normalizedText,
            renderSettings,
            typeface,
            viewportWidthPx,
            viewportHeightPx,
            densityScale
        ) {
            value = withContext(Dispatchers.Default) {
                renderAdaptiveLedFrame(
                    text = normalizedText,
                    widthPx = viewportWidthPx,
                    heightPx = viewportHeightPx,
                    densityScale = densityScale,
                    settings = renderSettings,
                    typeface = typeface
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            frame?.let { image ->
                drawImage(
                    image = image,
                    topLeft = Offset(
                        x = (size.width - image.width) / 2f,
                        y = (size.height - image.height) / 2f
                    )
                )
            }
        }
    }
}

private fun normalizeAdaptiveLedText(text: String): String = text
    .replace('\t', ' ')
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .lineSequence()
    .joinToString("\n") { line -> line.replace(Regex(" {2,}"), " ").trim() }
    .trim()

private suspend fun renderAdaptiveLedFrame(
    text: String,
    widthPx: Int,
    heightPx: Int,
    densityScale: Float,
    settings: LedSubtitleSettings,
    typeface: Typeface
): ImageBitmap? {
    if (text.isBlank() || widthPx <= 1 || heightPx <= 1) return null
    val value = settings.normalized()
    val paddingPx = (min(widthPx, heightPx) * 0.04f)
        .coerceAtLeast(8f * densityScale)
        .roundToInt()
    val contentWidth = (widthPx - paddingPx * 2).coerceAtLeast(1)
    val contentHeight = (heightPx - paddingPx * 2).coerceAtLeast(1)
    val textPaint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = value.ledColorArgb
        this.typeface = typeface
    }
    val alignment = when (value.shortTextAlignment) {
        LedSubtitleSettings.ALIGN_START -> Layout.Alignment.ALIGN_NORMAL
        LedSubtitleSettings.ALIGN_END -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_CENTER
    }
    val maxTextSize = (contentHeight * value.displayHeightFraction * 0.78f)
        .coerceAtLeast(12f * densityScale)
    val minTextSize = (12f * densityScale).coerceAtMost(maxTextSize)
    var low = minTextSize
    var high = maxTextSize
    repeat(12) {
        currentCoroutineContext().ensureActive()
        val middle = (low + high) / 2f
        val candidate = buildAdaptiveLayout(text, textPaint, middle, contentWidth, alignment)
        if (candidate.height <= contentHeight && !candidate.hasHorizontalOverflow(contentWidth)) {
            low = middle
        } else {
            high = middle
        }
    }
    val bestLayout = buildAdaptiveLayout(text, textPaint, low, contentWidth, alignment)
    val mask = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
    val maskCanvas = AndroidCanvas(mask)
    val top = paddingPx + ((contentHeight - bestLayout.height) / 2f).coerceAtLeast(0f)
    maskCanvas.save()
    maskCanvas.translate(paddingPx.toFloat(), top)
    bestLayout.draw(maskCanvas)
    maskCanvas.restore()

    val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val outputCanvas = AndroidCanvas(output)
    if (value.dotMatrixEnabled) {
        val pitchDp = 12f - value.dotDensity * 7f
        val pitchPx = (pitchDp * densityScale).roundToInt().coerceAtLeast(4)
        drawLedCells(outputCanvas, mask, pitchPx, value)
    } else {
        val textBitmapPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = value.ledColorArgb
        }
        val layers = createLedPaintLayers(
            source = textBitmapPaint,
            glowEnabled = value.glowEnabled,
            glowStrength = value.glowStrength,
            glowRadiusPx = ledTextGlowRadiusPx(
                textSizePx = low,
                densityScale = densityScale,
                glowStrength = value.glowStrength
            )
        )
        layers.outerGlow?.let { outputCanvas.drawBitmap(mask, 0f, 0f, it) }
        layers.innerGlow?.let { outputCanvas.drawBitmap(mask, 0f, 0f, it) }
        outputCanvas.drawBitmap(mask, 0f, 0f, layers.core)
    }
    mask.recycle()
    return output.asImageBitmap()
}

private fun buildAdaptiveLayout(
    text: String,
    paint: TextPaint,
    textSizePx: Float,
    widthPx: Int,
    alignment: Layout.Alignment
): StaticLayout {
    paint.textSize = textSizePx
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.15f)
        .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        .build()
}

private fun StaticLayout.hasHorizontalOverflow(maxWidthPx: Int): Boolean {
    for (line in 0 until lineCount) {
        if (getLineWidth(line) > maxWidthPx + 1f) return true
    }
    return false
}
