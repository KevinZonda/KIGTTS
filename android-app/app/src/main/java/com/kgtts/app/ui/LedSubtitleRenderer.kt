package com.lhtstudio.kigtts.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private data class LedBitmapChunk(
    val startX: Float,
    val image: ImageBitmap
)

private data class LedRenderedStrip(
    val widthPx: Float,
    val heightPx: Float,
    val chunks: List<LedBitmapChunk>
)

private data class LedDisplayTarget(
    val text: String,
    val dotMatrixEnabled: Boolean
)

@Stable
internal class LedMarqueeMotionState {
    var positionPx by mutableFloatStateOf(0f)
        private set

    private var horizontalDragActive = false
    private var flingVelocityPxPerSecond = 0f

    fun beginHorizontalDrag() {
        horizontalDragActive = true
        flingVelocityPxPerSecond = 0f
    }

    fun dragBy(deltaXPx: Float) {
        if (horizontalDragActive && deltaXPx.isFinite()) positionPx -= deltaXPx
    }

    fun endHorizontalDrag(velocityXPxPerSecond: Float) {
        horizontalDragActive = false
        flingVelocityPxPerSecond = velocityXPxPerSecond
            .takeIf(Float::isFinite)
            ?.coerceIn(-MAX_FLING_VELOCITY_PX_PER_SECOND, MAX_FLING_VELOCITY_PX_PER_SECOND)
            ?: 0f
    }

    fun cancelHorizontalDrag() {
        horizontalDragActive = false
        flingVelocityPxPerSecond = 0f
    }

    fun resetToStart() {
        positionPx = 0f
        flingVelocityPxPerSecond = 0f
    }

    internal fun advance(seconds: Float, autoVelocityPxPerSecond: Float) {
        if (horizontalDragActive || seconds <= 0f) return
        val flingVelocity = flingVelocityPxPerSecond
        if (abs(flingVelocity) >= MIN_FLING_VELOCITY_PX_PER_SECOND) {
            positionPx -= flingVelocity * seconds
            val decayed = flingVelocity * exp(-FLING_FRICTION_PER_SECOND * seconds)
            flingVelocityPxPerSecond = if (abs(decayed) < MIN_FLING_VELOCITY_PX_PER_SECOND) {
                0f
            } else {
                decayed
            }
        } else {
            flingVelocityPxPerSecond = 0f
            positionPx += autoVelocityPxPerSecond * seconds
        }
    }

    internal fun phaseFor(cycleWidthPx: Float): Float {
        if (cycleWidthPx <= 0f || !cycleWidthPx.isFinite()) return 0f
        val remainder = positionPx % cycleWidthPx
        return if (remainder < 0f) remainder + cycleWidthPx else remainder
    }

    private companion object {
        const val MAX_FLING_VELOCITY_PX_PER_SECOND = 24_000f
        const val MIN_FLING_VELOCITY_PX_PER_SECOND = 18f
        const val FLING_FRICTION_PER_SECOND = 4.2f
    }
}

@Composable
internal fun rememberLedMarqueeMotionState(settings: LedSubtitleSettings): LedMarqueeMotionState {
    val state = remember { LedMarqueeMotionState() }
    val densityScale = LocalDensity.current.density
    LaunchedEffect(
        state,
        settings.scrollSpeedDpPerSecond,
        settings.scrollDirection,
        densityScale
    ) {
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = (frame - previousFrame).coerceAtMost(100_000_000L) / 1_000_000_000f
            previousFrame = frame
            val direction = if (
                settings.scrollDirection == LedSubtitleSettings.SCROLL_RIGHT_TO_LEFT
            ) {
                1f
            } else {
                -1f
            }
            state.advance(
                seconds = seconds,
                autoVelocityPxPerSecond = settings.scrollSpeedDpPerSecond * densityScale * direction
            )
        }
    }
    return state
}

@Composable
internal fun LedSubtitleDisplay(
    text: String,
    settings: LedSubtitleSettings,
    motionState: LedMarqueeMotionState,
    typeface: Typeface,
    modifier: Modifier = Modifier
) {
    val target = remember(text, settings.dotMatrixEnabled) {
        LedDisplayTarget(normalizeLedText(text), settings.dotMatrixEnabled)
    }
    LaunchedEffect(target) { motionState.resetToStart() }
    Crossfade(
        targetState = target,
        animationSpec = tween(190),
        modifier = modifier,
        label = "led_subtitle_content_change"
    ) { current ->
        if (current.dotMatrixEnabled) {
            LedMarqueeDisplay(
                text = current.text,
                settings = settings,
                motionState = motionState,
                typeface = typeface,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LedNormalFontMarqueeDisplay(
                text = current.text,
                settings = settings,
                motionState = motionState,
                typeface = typeface,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LedNormalFontMarqueeDisplay(
    text: String,
    settings: LedSubtitleSettings,
    motionState: LedMarqueeMotionState,
    typeface: Typeface,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val densityScale = density.density
        val normalizedText = remember(text) { normalizeLedText(text) }
        val textPaint = remember(
            normalizedText,
            settings.ledColorArgb,
            settings.displayHeightFraction,
            typeface,
            viewportHeightPx
        ) {
            val displayHeight = viewportHeightPx * settings.displayHeightFraction
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = settings.ledColorArgb
                textSize = (displayHeight * 0.78f).coerceAtLeast(1f)
                this.typeface = typeface
                val measuredWidth = measureText(normalizedText)
                if (measuredWidth > MAXIMUM_STRIP_WIDTH) {
                    textSize *= (MAXIMUM_STRIP_WIDTH / measuredWidth).coerceAtLeast(0.2f)
                }
            }
        }
        val textWidthPx = remember(normalizedText, textPaint.textSize, typeface) {
            textPaint.measureText(normalizedText)
        }
        val gapPx = settings.loopGapDp * densityScale
        val cycleWidth = textWidthPx + gapPx
        val shouldScroll = normalizedText.isNotBlank() && textWidthPx > viewportWidthPx
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (normalizedText.isBlank()) return@Canvas
            val baseline = size.height / 2f -
                (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
            fun drawTextAt(left: Float) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(normalizedText, left, baseline, textPaint)
                }
            }
            if (!shouldScroll) {
                val left = when (settings.shortTextAlignment) {
                    LedSubtitleSettings.ALIGN_START -> 0f
                    LedSubtitleSettings.ALIGN_END -> size.width - textWidthPx
                    else -> (size.width - textWidthPx) / 2f
                }.coerceAtLeast(0f)
                drawTextAt(left)
                return@Canvas
            }

            val firstLeft = -motionState.phaseFor(cycleWidth)
            var left = firstLeft
            while (left > -cycleWidth) left -= cycleWidth
            while (left < size.width) {
                drawTextAt(left)
                left += cycleWidth
            }
        }
    }
}

@Composable
internal fun LedMarqueeDisplay(
    text: String,
    settings: LedSubtitleSettings,
    motionState: LedMarqueeMotionState,
    typeface: Typeface,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val densityScale = density.density
        val normalizedText = remember(text) { normalizeLedText(text) }
        val defaultSettings = remember { LedSubtitleSettings() }
        val requestedRenderSettings = remember(settings) {
            settings.copy(
                scrollSpeedDpPerSecond = defaultSettings.scrollSpeedDpPerSecond,
                scrollDirection = defaultSettings.scrollDirection,
                loopGapDp = defaultSettings.loopGapDp,
                shortTextAlignment = defaultSettings.shortTextAlignment,
                keepScreenOn = defaultSettings.keepScreenOn,
                followSystemBrightness = defaultSettings.followSystemBrightness,
                screenBrightness = defaultSettings.screenBrightness
            )
        }
        var settledRenderSettings by remember { mutableStateOf(requestedRenderSettings) }
        LaunchedEffect(requestedRenderSettings) {
            delay(90)
            settledRenderSettings = requestedRenderSettings
        }
        val strip by produceState<LedRenderedStrip?>(
            initialValue = null,
            normalizedText,
            settledRenderSettings,
            typeface,
            viewportHeightPx,
            densityScale
        ) {
            value = withContext(Dispatchers.Default) {
                renderLedStrip(
                    text = normalizedText,
                    viewportHeightPx = viewportHeightPx,
                    densityScale = densityScale,
                    settings = settledRenderSettings,
                    typeface = typeface
                )
            }
        }
        val rendered = strip
        val gapPx = settings.loopGapDp * densityScale
        val cycleWidth = (rendered?.widthPx ?: 0f) + gapPx
        val shouldScroll = rendered != null && rendered.widthPx > viewportWidthPx
        Canvas(modifier = Modifier.fillMaxSize()) {
            val value = rendered ?: return@Canvas
            val top = (size.height - value.heightPx) / 2f
            if (!shouldScroll) {
                val left = when (settings.shortTextAlignment) {
                    LedSubtitleSettings.ALIGN_START -> 0f
                    LedSubtitleSettings.ALIGN_END -> size.width - value.widthPx
                    else -> (size.width - value.widthPx) / 2f
                }.coerceAtLeast(0f)
                drawLedStrip(value, left, top)
                return@Canvas
            }

            val firstLeft = -motionState.phaseFor(cycleWidth)
            var left = firstLeft
            while (left > -cycleWidth) left -= cycleWidth
            while (left < size.width) {
                drawLedStrip(value, left, top)
                left += cycleWidth
            }
        }
    }
}

private fun DrawScope.drawLedStrip(strip: LedRenderedStrip, left: Float, top: Float) {
    strip.chunks.forEach { chunk ->
        val chunkLeft = left + chunk.startX
        if (chunkLeft < size.width && chunkLeft + chunk.image.width > 0f) {
            drawImage(chunk.image, topLeft = Offset(chunkLeft, top))
        }
    }
}

private fun normalizeLedText(text: String): String {
    return text
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex(" {2,}"), " ")
        .trim()
}

private suspend fun renderLedStrip(
    text: String,
    viewportHeightPx: Float,
    densityScale: Float,
    settings: LedSubtitleSettings,
    typeface: Typeface
): LedRenderedStrip? {
    if (text.isBlank() || viewportHeightPx <= 1f) return null
    val value = settings.normalized()
    val stripHeight = (viewportHeightPx * value.displayHeightFraction)
        .roundToInt()
        .coerceIn(48, viewportHeightPx.roundToInt().coerceAtLeast(48))
    val pitchDp = 12f - value.dotDensity * 7f
    val pitchPx = (pitchDp * densityScale).roundToInt().coerceAtLeast(4)
    val horizontalPadding = pitchPx * 2f
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = stripHeight * 0.78f
        this.typeface = typeface
    }
    val initialWidth = textPaint.measureText(text) + horizontalPadding * 2f
    if (initialWidth > MAXIMUM_STRIP_WIDTH) {
        textPaint.textSize *= (MAXIMUM_STRIP_WIDTH / initialWidth).coerceAtLeast(0.2f)
    }
    val measuredWidth = textPaint.measureText(text) + horizontalPadding * 2f
    val totalWidth = ceil(measuredWidth / pitchPx).toInt().coerceAtLeast(1) * pitchPx
    val chunkCellCount = (2_048 / pitchPx).coerceAtLeast(1)
    val standardChunkWidth = chunkCellCount * pitchPx
    val baseline = stripHeight / 2f - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
    val chunks = mutableListOf<LedBitmapChunk>()
    var chunkStart = 0

    while (chunkStart < totalWidth) {
        currentCoroutineContext().ensureActive()
        val chunkWidth = minOf(standardChunkWidth, totalWidth - chunkStart)
        val mask = Bitmap.createBitmap(chunkWidth, stripHeight, Bitmap.Config.ALPHA_8)
        AndroidCanvas(mask).drawText(text, horizontalPadding - chunkStart, baseline, textPaint)
        val output = Bitmap.createBitmap(chunkWidth, stripHeight, Bitmap.Config.RGB_565)
        val canvas = AndroidCanvas(output)
        canvas.drawColor(value.backgroundColorArgb)
        drawLedCells(canvas, mask, pitchPx, value)
        mask.recycle()
        chunks += LedBitmapChunk(chunkStart.toFloat(), output.asImageBitmap())
        chunkStart += chunkWidth
    }
    return LedRenderedStrip(totalWidth.toFloat(), stripHeight.toFloat(), chunks)
}

private const val MAXIMUM_STRIP_WIDTH = 32_768f

private fun drawLedCells(
    canvas: AndroidCanvas,
    mask: Bitmap,
    pitchPx: Int,
    settings: LedSubtitleSettings
) {
    val dotRadius = pitchPx * (0.22f + settings.dotDensity * 0.12f)
    val glowRadius = dotRadius * (1.3f + settings.glowStrength * 1.8f)
    val glowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = settings.ledColorArgb }
    val ledPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = settings.ledColorArgb }
    val halfPitch = pitchPx / 2
    var y = halfPitch
    while (y < mask.height) {
        var x = halfPitch
        while (x < mask.width) {
            val sampleAlpha = AndroidColor.alpha(mask.getPixel(x, y))
            if (sampleAlpha >= 28) {
                if (settings.glowEnabled && settings.glowStrength > 0f) {
                    glowPaint.alpha = (sampleAlpha * settings.glowStrength * 0.28f)
                        .roundToInt()
                        .coerceIn(0, 255)
                    canvas.drawCircle(x.toFloat(), y.toFloat(), glowRadius, glowPaint)
                }
                ledPaint.alpha = sampleAlpha.coerceIn(64, 255)
                if (settings.dotShape == LedSubtitleSettings.DOT_SHAPE_SQUARE) {
                    canvas.drawRect(
                        x - dotRadius,
                        y - dotRadius,
                        x + dotRadius,
                        y + dotRadius,
                        ledPaint
                    )
                } else {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, ledPaint)
                }
            }
            x += pitchPx
        }
        y += pitchPx
    }
}
