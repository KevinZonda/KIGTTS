package com.lhtstudio.kigtts.app.data

import org.json.JSONObject

data class LedSubtitleSettings(
    val ledColorArgb: Int = DEFAULT_LED_COLOR_ARGB,
    val backgroundColorArgb: Int = DEFAULT_LED_BACKGROUND_ARGB,
    val dotMatrixEnabled: Boolean = true,
    val dotShape: Int = DOT_SHAPE_CIRCLE,
    val dotRowsPerLine: Int = DEFAULT_DOT_ROWS_PER_LINE,
    val dotSizeFraction: Float = DEFAULT_DOT_SIZE_FRACTION,
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
        dotRowsPerLine = dotRowsPerLine.coerceIn(
            MIN_DOT_ROWS_PER_LINE,
            MAX_DOT_ROWS_PER_LINE
        ),
        dotSizeFraction = dotSizeFraction.coerceIn(
            MIN_DOT_SIZE_FRACTION,
            MAX_DOT_SIZE_FRACTION
        ),
        glowStrength = glowStrength.coerceIn(0f, 1f),
        displayHeightFraction = displayHeightFraction.coerceIn(0.35f, 0.92f),
        scrollSpeedDpPerSecond = scrollSpeedDpPerSecond.coerceIn(
            MIN_SCROLL_SPEED_DP_PER_SECOND,
            MAX_SCROLL_SPEED_DP_PER_SECOND
        ),
        scrollDirection = scrollDirection.coerceIn(SCROLL_RIGHT_TO_LEFT, SCROLL_LEFT_TO_RIGHT),
        loopGapDp = loopGapDp.coerceIn(MIN_LOOP_GAP_DP, MAX_LOOP_GAP_DP),
        shortTextAlignment = shortTextAlignment.coerceIn(ALIGN_START, ALIGN_END),
        screenBrightness = screenBrightness.coerceIn(0.1f, 1f)
    )

    companion object {
        const val DEFAULT_LED_COLOR_ARGB: Int = -1 // #FFFFFF
        const val DEFAULT_LED_BACKGROUND_ARGB: Int = -16777216 // #000000
        const val DOT_SHAPE_CIRCLE = 0
        const val DOT_SHAPE_SQUARE = 1
        const val DEFAULT_DOT_ROWS_PER_LINE = 24
        const val MIN_DOT_ROWS_PER_LINE = 8
        const val MAX_DOT_ROWS_PER_LINE = 48
        const val DEFAULT_DOT_SIZE_FRACTION = 0.58f
        const val MIN_DOT_SIZE_FRACTION = 0.1f
        const val MAX_DOT_SIZE_FRACTION = 1f
        const val SCROLL_RIGHT_TO_LEFT = 0
        const val SCROLL_LEFT_TO_RIGHT = 1
        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2
        const val MIN_SCROLL_SPEED_DP_PER_SECOND = 24f
        const val MAX_SCROLL_SPEED_DP_PER_SECOND = 1600f
        const val MIN_LOOP_GAP_DP = 24f
        const val MAX_LOOP_GAP_DP = 1600f

        private fun opaque(color: Int): Int = color or (0xFF shl 24)
    }
}

internal fun encodeLedSubtitleSettings(settings: LedSubtitleSettings): String {
    val value = settings.normalized()
    return JSONObject().apply {
        put("version", 5)
        put("ledColorArgb", value.ledColorArgb)
        put("backgroundColorArgb", value.backgroundColorArgb)
        put("dotMatrixEnabled", value.dotMatrixEnabled)
        put("dotShape", value.dotShape)
        put("dotRowsPerLine", value.dotRowsPerLine)
        put("dotSizeFraction", value.dotSizeFraction.toDouble())
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

internal fun defaultLanCastDisplaySettings(): LedSubtitleSettings = LedSubtitleSettings(
    dotMatrixEnabled = false,
    adaptiveMultiLine = true
)

internal fun decodeLedSubtitleSettings(
    raw: String?,
    defaults: LedSubtitleSettings = LedSubtitleSettings()
): LedSubtitleSettings {
    if (raw.isNullOrBlank()) return defaults
    return runCatching {
        val json = JSONObject(raw)
        val migratedRows = when {
            json.has("dotRowsPerLine") -> json.optInt(
                "dotRowsPerLine",
                defaults.dotRowsPerLine
            )
            json.has("dotDensity") -> {
                val legacyDensity = json.optDouble("dotDensity", 0.58).toFloat().coerceIn(0f, 1f)
                (8f + legacyDensity * 28f).toInt()
            }
            else -> defaults.dotRowsPerLine
        }
        LedSubtitleSettings(
            ledColorArgb = json.optInt("ledColorArgb", defaults.ledColorArgb),
            backgroundColorArgb = json.optInt(
                "backgroundColorArgb",
                defaults.backgroundColorArgb
            ),
            dotMatrixEnabled = json.optBoolean("dotMatrixEnabled", defaults.dotMatrixEnabled),
            dotShape = json.optInt("dotShape", defaults.dotShape),
            dotRowsPerLine = migratedRows,
            dotSizeFraction = json.optDouble(
                "dotSizeFraction",
                defaults.dotSizeFraction.toDouble()
            ).toFloat(),
            glowEnabled = json.optBoolean("glowEnabled", defaults.glowEnabled),
            glowStrength = json.optDouble(
                "glowStrength",
                defaults.glowStrength.toDouble()
            ).toFloat(),
            displayHeightFraction = json.optDouble(
                "displayHeightFraction",
                defaults.displayHeightFraction.toDouble()
            ).toFloat(),
            adaptiveMultiLine = json.optBoolean(
                "adaptiveMultiLine",
                defaults.adaptiveMultiLine
            ),
            scrollSpeedDpPerSecond = json.optDouble(
                "scrollSpeedDpPerSecond",
                defaults.scrollSpeedDpPerSecond.toDouble()
            ).toFloat(),
            scrollDirection = json.optInt(
                "scrollDirection",
                defaults.scrollDirection
            ),
            quickSwipeOpensQuickText = json.optBoolean(
                "quickSwipeOpensQuickText",
                defaults.quickSwipeOpensQuickText
            ),
            loopGapDp = json.optDouble("loopGapDp", defaults.loopGapDp.toDouble()).toFloat(),
            shortTextAlignment = json.optInt("shortTextAlignment", defaults.shortTextAlignment),
            keepScreenOn = json.optBoolean("keepScreenOn", defaults.keepScreenOn),
            followSystemBrightness = json.optBoolean(
                "followSystemBrightness",
                defaults.followSystemBrightness
            ),
            screenBrightness = json.optDouble(
                "screenBrightness",
                defaults.screenBrightness.toDouble()
            ).toFloat()
        ).normalized()
    }.getOrDefault(defaults)
}
