package com.lhtstudio.kigtts.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object LockScreenWallpaperStore {
    private const val DirectoryName = "lock_screen"
    private const val WallpaperFileName = "wallpaper"
    private const val TempFileName = "wallpaper.tmp"
    private const val MaxWallpaperBytes = 40L * 1024L * 1024L

    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DirectoryName).apply { mkdirs() }
        val temp = File(directory, TempFileName)
        val target = File(directory, WallpaperFileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MaxWallpaperBytes) throw IOException("图片文件过大")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("无法读取所选图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("所选文件不是有效图片")
            }
            if (target.exists() && !target.delete()) throw IOException("无法替换锁屏壁纸")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.absolutePath
        } finally {
            temp.delete()
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DirectoryName)
        File(directory, WallpaperFileName).delete()
        File(directory, TempFileName).delete()
    }

    suspend fun loadForDisplay(
        path: String,
        maxWidth: Int,
        maxHeight: Int,
        blurRadius: Float = 0f
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(path).takeIf { it.isFile } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sampleSize = 1
            val safeWidth = maxWidth.coerceAtLeast(1)
            val safeHeight = maxHeight.coerceAtLeast(1)
            while (
                bounds.outWidth / (sampleSize * 2) >= safeWidth &&
                bounds.outHeight / (sampleSize * 2) >= safeHeight
            ) {
                sampleSize *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
            decoded?.let { applyGaussianBlur(it, blurRadius) }
        }

    private fun applyGaussianBlur(source: Bitmap, radius: Float): Bitmap {
        val safeRadius = radius.coerceIn(0f, 30f)
        if (safeRadius < 0.5f || source.width <= 1 || source.height <= 1) return source
        val width = source.width
        val height = source.height
        var pixels = IntArray(width * height)
        var scratch = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        gaussianBoxSizes(safeRadius, passCount = 3).forEach { boxSize ->
            val boxRadius = (boxSize - 1) / 2
            boxBlurHorizontal(pixels, scratch, width, height, boxRadius)
            boxBlurVertical(scratch, pixels, width, height, boxRadius)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { blurred ->
            blurred.setPixels(pixels, 0, width, 0, 0, width, height)
            source.recycle()
        }
    }

    private fun gaussianBoxSizes(sigma: Float, passCount: Int): IntArray {
        val idealWidth = sqrt((12.0 * sigma * sigma / passCount) + 1.0)
        var lowerWidth = floor(idealWidth).toInt()
        if (lowerWidth % 2 == 0) lowerWidth--
        lowerWidth = lowerWidth.coerceAtLeast(1)
        val upperWidth = lowerWidth + 2
        val lowerPasses = (
            (
                12.0 * sigma * sigma -
                    passCount * lowerWidth * lowerWidth -
                    4.0 * passCount * lowerWidth -
                    3.0 * passCount
                ) / (-4.0 * lowerWidth - 4.0)
            ).roundToInt().coerceIn(0, passCount)
        return IntArray(passCount) { index ->
            if (index < lowerPasses) lowerWidth else upperWidth
        }
    }

    private fun boxBlurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val divisor = radius * 2 + 1
        for (y in 0 until height) {
            val rowOffset = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = source[rowOffset + offset.coerceIn(0, width - 1)]
                alpha += color ushr 24
                red += color ushr 16 and 0xFF
                green += color ushr 8 and 0xFF
                blue += color and 0xFF
            }
            for (x in 0 until width) {
                target[rowOffset + x] = averageColor(alpha, red, green, blue, divisor)
                val leaving = source[rowOffset + (x - radius).coerceIn(0, width - 1)]
                val entering = source[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += (entering ushr 24) - (leaving ushr 24)
                red += (entering ushr 16 and 0xFF) - (leaving ushr 16 and 0xFF)
                green += (entering ushr 8 and 0xFF) - (leaving ushr 8 and 0xFF)
                blue += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val divisor = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = source[offset.coerceIn(0, height - 1) * width + x]
                alpha += color ushr 24
                red += color ushr 16 and 0xFF
                green += color ushr 8 and 0xFF
                blue += color and 0xFF
            }
            for (y in 0 until height) {
                target[y * width + x] = averageColor(alpha, red, green, blue, divisor)
                val leaving = source[(y - radius).coerceIn(0, height - 1) * width + x]
                val entering = source[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += (entering ushr 24) - (leaving ushr 24)
                red += (entering ushr 16 and 0xFF) - (leaving ushr 16 and 0xFF)
                green += (entering ushr 8 and 0xFF) - (leaving ushr 8 and 0xFF)
                blue += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    private fun averageColor(
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
        divisor: Int
    ): Int =
        ((alpha / divisor) shl 24) or
            ((red / divisor) shl 16) or
            ((green / divisor) shl 8) or
            (blue / divisor)
}
