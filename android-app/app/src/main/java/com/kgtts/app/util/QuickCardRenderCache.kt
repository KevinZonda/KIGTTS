package com.lhtstudio.kigtts.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import kotlin.math.max

internal const val QUICK_CARD_CROP_LONG_EDGE_PX = 3840
internal const val QUICK_CARD_CROP_SHORT_EDGE_PX = 2160
internal const val QUICK_CARD_RENDER_LONG_EDGE_PX = 2880

object QuickCardRenderCache {
    private const val defaultQrSizePx = 640

    private val imageCache = object : LruCache<String, Bitmap>(max(8 * 1024, cacheSizeKb() / 12)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val qrCache = object : LruCache<String, Bitmap>(max(4 * 1024, cacheSizeKb() / 24)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun loadImage(path: String, maxDimensionPx: Int = QUICK_CARD_RENDER_LONG_EDGE_PX): Bitmap? {
        val normalized = path.trim()
        if (normalized.isEmpty()) return null
        val file = File(normalized)
        if (!file.exists()) return null
        val safeMaxDimensionPx = maxDimensionPx.coerceAtLeast(256)
        val cacheKey = "$normalized:${file.lastModified()}:$safeMaxDimensionPx"
        synchronized(imageCache) {
            imageCache.get(cacheKey)?.let { return it }
        }
        val bitmap = decodeSampledBitmap(file, safeMaxDimensionPx) ?: return null
        synchronized(imageCache) {
            imageCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun loadQr(content: String, sizePx: Int = defaultQrSizePx): Bitmap? {
        val normalized = content.trim()
        if (normalized.isEmpty()) return null
        val cacheKey = "$sizePx:$normalized"
        synchronized(qrCache) {
            qrCache.get(cacheKey)?.let { return it }
        }
        val bitmap = runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name()
            )
            val matrix = QRCodeWriter().encode(normalized, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }.getOrNull() ?: return null
        synchronized(qrCache) {
            qrCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun decodeSampledBitmap(file: File, maxDimensionPx: Int): Bitmap? {
        val safeMax = maxDimensionPx.coerceAtLeast(256)
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = quickCardImageSampleSize(bounds.outWidth, bounds.outHeight, safeMax)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val decodedMax = max(decoded.width, decoded.height)
        if (decodedMax <= safeMax) return decoded

        val scale = safeMax.toFloat() / decodedMax.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun cacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        return maxMemoryKb.coerceAtLeast(12 * 1024)
    }
}

internal fun quickCardImageSampleSize(width: Int, height: Int, targetMaxDimensionPx: Int): Int {
    val safeTarget = targetMaxDimensionPx.coerceAtLeast(1)
    var sampleSize = 1
    var currentWidth = width.coerceAtLeast(1)
    var currentHeight = height.coerceAtLeast(1)
    while (currentWidth / 2 >= safeTarget || currentHeight / 2 >= safeTarget) {
        sampleSize *= 2
        currentWidth /= 2
        currentHeight /= 2
    }
    return sampleSize
}
