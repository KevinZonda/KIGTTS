package com.lhtstudio.kigtts.app.data

import org.json.JSONObject

data class LedSubtitleSettings(
    val ledColorArgb: Int = DEFAULT_LED_COLOR_ARGB,
    val backgroundColorArgb: Int = DEFAULT_LED_BACKGROUND_ARGB,
    val dotMatrixEnabled: Boolean = true,
    val dotShape: Int = DOT_SHAPE_CIRCLE,
    val dotDensity: Float = 0.58f,
    val glowEnabled: Boolean = true,
    val glowStrength: Float = 0.42f,
    val displayHeightFraction: Float = 0.72f,
    val adaptiveMultiLine: Boolean = false,
    val scrollSpeedDpPerSecond: Float = 72f,
    val scrollDirection: Int = SCROLL_RIGHT_TO_LEFT,
    val quickSwipeOpensQuickText: Boolean = true,
    val loopGapDp: Float = 96f,
    val shortTextAlignment: Int = ALIGN_CENTER,
    val keepScreenOn: Boolean = true,
    val followSystemBrightness: Boolean = false,
    val screenBrightness: Float = 1f
) {
    fun normalized(): LedSubtitleSettings = copy(
        ledColorArgb = opaque(ledColorArgb),
        backgroundColorArgb = opaque(backgroundColorArgb),
        dotShape = dotShape.coerceIn(DOT_SHAPE_CIRCLE, DOT_SHAPE_SQUARE),
        dotDensity = dotDensity.coerceIn(0f, 1f),
        glowStrength = glowStrength.coerceIn(0f, 1f),
        displayHeightFraction = displayHeightFraction.coerceIn(0.35f, 0.92f),
        scrollSpeedDpPerSecond = scrollSpeedDpPerSecond.coerceIn(
            MIN_SCROLL_SPEED_DP_PER_SECOND,
            MAX_SCROLL_SPEED_DP_PER_SECOND
        ),
        scrollDirection = scrollDirection.coerceIn(SCROLL_RIGHT_TO_LEFT, SCROLL_LEFT_TO_RIGHT),
        loopGapDp = loopGapDp.coerceIn(24f, 240f),
        shortTextAlignment = shortTextAlignment.coerceIn(ALIGN_START, ALIGN_END),
        screenBrightness = screenBrightness.coerceIn(0.1f, 1f)
    )

    companion object {
        const val DEFAULT_LED_COLOR_ARGB: Int = -1 // #FFFFFF
        const val DEFAULT_LED_BACKGROUND_ARGB: Int = -16777216 // #000000
        const val DOT_SHAPE_CIRCLE = 0
        const val DOT_SHAPE_SQUARE = 1
        const val SCROLL_RIGHT_TO_LEFT = 0
        const val SCROLL_LEFT_TO_RIGHT = 1
        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2
        const val MIN_SCROLL_SPEED_DP_PER_SECOND = 24f
        const val MAX_SCROLL_SPEED_DP_PER_SECOND = 1600f

        private fun opaque(color: Int): Int = color or (0xFF shl 24)
    }
}

internal fun encodeLedSubtitleSettings(settings: LedSubtitleSettings): String {
    val value = settings.normalized()
    return JSONObject().apply {
        put("version", 4)
        put("ledColorArgb", value.ledColorArgb)
        put("backgroundColorArgb", value.backgroundColorArgb)
        put("dotMatrixEnabled", value.dotMatrixEnabled)
        put("dotShape", value.dotShape)
        put("dotDensity", value.dotDensity.toDouble())
        put("glowEnabled", value.glowEnabled)
        put("glowStrength", value.glowStrength.toDouble())
        put("displayHeightFraction", value.displayHeightFraction.toDouble())
        put("adaptiveMultiLine", value.adaptiveMultiLine)
        put("scrollSpeedDpPerSecond", value.scrollSpeedDpPerSecond.toDouble())
        put("scrollDirection", value.scrollDirection)
        put("quickSwipeOpensQuickText", value.quickSwipeOpensQuickText)
        put("loopGapDp", value.loopGapDp.toDouble())
        put("shortTextAlignment", value.shortTextAlignment)
        put("keepScreenOn", value.keepScreenOn)
        put("followSystemBrightness", value.followSystemBrightness)
        put("screenBrightness", value.screenBrightness.toDouble())
    }.toString()
}

internal fun decodeLedSubtitleSettings(raw: String?): LedSubtitleSettings {
    if (raw.isNullOrBlank()) return LedSubtitleSettings()
    return runCatching {
        val json = JSONObject(raw)
        LedSubtitleSettings(
            ledColorArgb = json.optInt("ledColorArgb", LedSubtitleSettings.DEFAULT_LED_COLOR_ARGB),
            backgroundColorArgb = json.optInt(
                "backgroundColorArgb",
                LedSubtitleSettings.DEFAULT_LED_BACKGROUND_ARGB
            ),
            dotMatrixEnabled = json.optBoolean("dotMatrixEnabled", true),
            dotShape = json.optInt("dotShape", LedSubtitleSettings.DOT_SHAPE_CIRCLE),
            dotDensity = json.optDouble("dotDensity", 0.58).toFloat(),
            glowEnabled = json.optBoolean("glowEnabled", true),
            glowStrength = json.optDouble("glowStrength", 0.42).toFloat(),
            displayHeightFraction = json.optDouble("displayHeightFraction", 0.72).toFloat(),
            adaptiveMultiLine = json.optBoolean("adaptiveMultiLine", false),
            scrollSpeedDpPerSecond = json.optDouble("scrollSpeedDpPerSecond", 72.0).toFloat(),
            scrollDirection = json.optInt(
                "scrollDirection",
                LedSubtitleSettings.SCROLL_RIGHT_TO_LEFT
            ),
            quickSwipeOpensQuickText = json.optBoolean("quickSwipeOpensQuickText", true),
            loopGapDp = json.optDouble("loopGapDp", 96.0).toFloat(),
            shortTextAlignment = json.optInt("shortTextAlignment", LedSubtitleSettings.ALIGN_CENTER),
            keepScreenOn = json.optBoolean("keepScreenOn", true),
            followSystemBrightness = json.optBoolean("followSystemBrightness", false),
            screenBrightness = json.optDouble("screenBrightness", 1.0).toFloat()
        ).normalized()
    }.getOrDefault(LedSubtitleSettings())
}
