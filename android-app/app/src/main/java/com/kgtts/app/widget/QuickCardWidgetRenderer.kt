package com.lhtstudio.kigtts.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import com.lhtstudio.kigtts.app.util.QuickCardRenderCache
import kotlin.math.min
import kotlin.math.roundToInt

internal object QuickCardWidgetRenderer {
    private const val MaxWidthPx = 520
    private const val MaxHeightPx = 720

    fun render(
        card: WidgetCardSnapshot,
        widgetWidthDp: Int,
        widgetHeightDp: Int
    ): Bitmap {
        val previewWidthDp = widgetWidthDp.coerceAtLeast(180)
        val previewHeightDp = widgetHeightDp.coerceAtLeast(180)
        val aspect = (previewWidthDp.toFloat() / previewHeightDp).coerceIn(0.5f, 2f)
        var width = (previewWidthDp * 1.45f).roundToInt().coerceIn(280, MaxWidthPx)
        var height = (width / aspect).roundToInt()
        if (height > MaxHeightPx) {
            height = MaxHeightPx
            width = (height * aspect).roundToInt().coerceAtMost(MaxWidthPx)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = min(width, height) * 0.025f
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)

        val themeColor = runCatching { Color.parseColor(card.themeColor) }
            .getOrDefault(Color.rgb(3, 131, 135))
        val portrait = height >= width
        val image = loadHeroImage(card, portrait)
        canvas.drawColor(themeColor)
        if (image != null) {
            drawCenterCrop(canvas, image, width, height)
            drawImageScrims(canvas, width, height)
        }

        val foreground = if (image != null) Color.WHITE else readableTextColor(themeColor)
        if (image == null) {
            drawWatermark(
                canvas = canvas,
                title = card.title,
                foreground = foreground,
                width = width,
                height = height,
                portrait = portrait,
                renderScale = width.toFloat() / previewWidthDp
            )
        }
        canvas.restore()
        return bitmap
    }

    fun foregroundColor(card: WidgetCardSnapshot): Int {
        if (hasAvailableImage(card)) return Color.WHITE
        val themeColor = runCatching { Color.parseColor(card.themeColor) }
            .getOrDefault(Color.rgb(3, 131, 135))
        return readableTextColor(themeColor)
    }

    private fun hasAvailableImage(card: WidgetCardSnapshot): Boolean =
        sequenceOf(card.portraitImagePath, card.landscapeImagePath)
            .filter { it.isNotBlank() }
            .any { java.io.File(it).isFile }

    private fun loadHeroImage(card: WidgetCardSnapshot, portrait: Boolean): Bitmap? {
        val primary = if (portrait) card.portraitImagePath else card.landscapeImagePath
        val fallback = if (portrait) card.landscapeImagePath else card.portraitImagePath
        return QuickCardRenderCache.loadImage(primary, 960)
            ?: QuickCardRenderCache.loadImage(fallback, 960)
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, width: Int, height: Int) {
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val sourceWidth = width / scale
        val sourceHeight = height / scale
        val left = (source.width - sourceWidth) / 2f
        val top = (source.height - sourceHeight) / 2f
        canvas.drawBitmap(
            source,
            Rect(
                left.roundToInt(),
                top.roundToInt(),
                (left + sourceWidth).roundToInt(),
                (top + sourceHeight).roundToInt()
            ),
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawImageScrims(canvas: Canvas, width: Int, height: Int) {
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height * 0.28f,
            Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height * 0.28f,
                    Color.argb(150, 0, 0, 0),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
        )
        canvas.drawRect(
            0f,
            height * 0.7f,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f,
                    height * 0.7f,
                    0f,
                    height.toFloat(),
                    Color.TRANSPARENT,
                    Color.argb(158, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
        )
    }

    private fun drawWatermark(
        canvas: Canvas,
        title: String,
        foreground: Int,
        width: Int,
        height: Int,
        portrait: Boolean,
        renderScale: Float
    ) {
        val value = title.trim()
        if (value.isEmpty()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            alpha = 56
            textSize = if (portrait) width * 0.25f else height * 0.34f
            isFakeBoldText = true
        }
        if (portrait) {
            val layout = quickCardPortraitWatermarkLayout(
                width = width,
                height = height,
                textAscent = paint.ascent(),
                renderScale = renderScale
            )
            canvas.save()
            canvas.rotate(90f)
            drawSingleLine(
                canvas,
                value,
                paint,
                layout.drawX,
                layout.drawBaseline,
                layout.maxWidth
            )
            canvas.restore()
        } else {
            drawSingleLine(canvas, value, paint, width * 0.05f, height * 0.92f, width * 0.78f)
        }
    }

    internal fun quickCardPortraitWatermarkLayout(
        width: Int,
        height: Int,
        textAscent: Float,
        renderScale: Float
    ): PortraitWatermarkLayout {
        val rightInset = 10f * renderScale
        val topInset = 10f * renderScale
        val transformedBaselineX = width - rightInset + textAscent
        return PortraitWatermarkLayout(
            drawX = topInset,
            drawBaseline = -transformedBaselineX,
            maxWidth = (height - 24f * renderScale).coerceAtLeast(height * 0.4f)
        )
    }

    internal data class PortraitWatermarkLayout(
        val drawX: Float,
        val drawBaseline: Float,
        val maxWidth: Float
    )

    private fun drawSingleLine(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        baseline: Float,
        maxWidth: Float
    ) {
        var value = text.trim()
        if (value.isEmpty()) return
        if (paint.measureText(value) > maxWidth) {
            while (value.isNotEmpty() && paint.measureText("$value…") > maxWidth) {
                value = value.dropLast(1)
            }
            value += "…"
        }
        canvas.drawText(value, x, baseline, paint)
    }

    private fun readableTextColor(background: Int): Int {
        val luminance = (
            Color.red(background) * 299 +
                Color.green(background) * 587 +
                Color.blue(background) * 114
            ) / 1000
        return if (luminance >= 150) Color.rgb(24, 24, 24) else Color.WHITE
    }
}
