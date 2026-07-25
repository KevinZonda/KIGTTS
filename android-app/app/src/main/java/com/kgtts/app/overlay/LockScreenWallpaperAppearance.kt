package com.lhtstudio.kigtts.app.overlay

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.lhtstudio.kigtts.app.data.LockScreenScrimStyle
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import kotlin.math.pow

internal object LockScreenWallpaperAppearance {
    fun scrimDrawable(settings: LockScreenSettings, landscape: Boolean): Drawable {
        val color = Color.argb(
            (settings.scrimOpacity.coerceIn(0f, 1f) * 255f).toInt(),
            Color.red(settings.scrimColorArgb),
            Color.green(settings.scrimColorArgb),
            Color.blue(settings.scrimColorArgb)
        )
        if (settings.scrimStyle == LockScreenScrimStyle.Full) return ColorDrawable(color)
        val transparent = ColorUtils.withAlpha(color, 0)
        return if (landscape) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(color, transparent)
            )
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color, transparent, color)
            )
        }
    }

    fun shouldUseDarkContent(
        bitmap: Bitmap?,
        settings: LockScreenSettings,
        landscape: Boolean
    ): Boolean {
        bitmap ?: return false
        val background = averageRelevantColor(bitmap, landscape)
        val effective = blend(background, settings.scrimColorArgb, settings.scrimOpacity)
        return relativeLuminance(effective) >= 0.42
    }

    private fun averageRelevantColor(bitmap: Bitmap, landscape: Boolean): Int {
        val columns = 12
        val rows = 12
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (row in 0 until rows) {
            val verticalFraction = if (landscape) {
                (row + 0.5f) / rows
            } else if (row < rows / 2) {
                (row + 0.5f) / rows * 0.7f
            } else {
                0.7f + ((row - rows / 2) + 0.5f) / rows * 0.6f
            }
            val y = (verticalFraction * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until columns) {
                val horizontalFraction = (column + 0.5f) / columns *
                    if (landscape) 0.45f else 1f
                val x = (horizontalFraction * bitmap.width).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val color = bitmap.getPixel(x, y)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
                count++
            }
        }
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun blend(background: Int, overlay: Int, opacity: Float): Int {
        val alpha = opacity.coerceIn(0f, 1f)
        fun channel(base: Int, top: Int): Int = (base * (1f - alpha) + top * alpha).toInt()
        return Color.rgb(
            channel(Color.red(background), Color.red(overlay)),
            channel(Color.green(background), Color.green(overlay)),
            channel(Color.blue(background), Color.blue(overlay))
        )
    }

    private fun relativeLuminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
    }

    private object ColorUtils {
        fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}
