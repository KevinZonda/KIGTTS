package com.lhtstudio.kigtts.app.data

import org.json.JSONObject

enum class LockScreenScrimStyle(val wireName: String) {
    EdgeGradient("edge_gradient"),
    Full("full");

    companion object {
        fun fromWireName(value: String): LockScreenScrimStyle =
            entries.firstOrNull { it.wireName == value } ?: EdgeGradient
    }
}

data class LockScreenSettings(
    val wallpaperPath: String = "",
    val wallpaperBlurRadius: Float = 0f,
    val scrimColorArgb: Int = 0xFF000000.toInt(),
    val scrimOpacity: Float = 0.28f,
    val scrimStyle: LockScreenScrimStyle = LockScreenScrimStyle.EdgeGradient,
    val timeAndDateAlignedStart: Boolean = false,
    val useSystemFont: Boolean = false,
    val useSeparateClockFont: Boolean = false,
    val clockFontId: String = AppFontDefaults.SystemFontId,
    val clockFontWeight: Int = AppFontDefaults.DefaultWeight,
    val showLunarDate: Boolean = false
)

internal fun LockScreenSettings.normalized(): LockScreenSettings = copy(
    wallpaperBlurRadius = wallpaperBlurRadius.coerceIn(0f, 30f),
    scrimColorArgb = scrimColorArgb or 0xFF000000.toInt(),
    scrimOpacity = scrimOpacity.coerceIn(0f, 1f),
    clockFontId = UserPrefs.normalizeAppFontId(clockFontId),
    clockFontWeight = UserPrefs.normalizeAppFontWeight(clockFontWeight)
)

internal fun encodeLockScreenSettings(settings: LockScreenSettings): String {
    val normalized = settings.normalized()
    return JSONObject()
        .put("version", 2)
        .put("wallpaperPath", normalized.wallpaperPath)
        .put("wallpaperBlurRadius", normalized.wallpaperBlurRadius.toDouble())
        .put("scrimColorArgb", normalized.scrimColorArgb.toLong())
        .put("scrimOpacity", normalized.scrimOpacity.toDouble())
        .put("scrimStyle", normalized.scrimStyle.wireName)
        .put("timeAndDateAlignedStart", normalized.timeAndDateAlignedStart)
        .put("useSystemFont", normalized.useSystemFont)
        .put("useSeparateClockFont", normalized.useSeparateClockFont)
        .put("clockFontId", normalized.clockFontId)
        .put("clockFontWeight", normalized.clockFontWeight)
        .put("showLunarDate", normalized.showLunarDate)
        .toString()
}

internal fun decodeLockScreenSettings(raw: String?): LockScreenSettings {
    if (raw.isNullOrBlank()) return LockScreenSettings()
    return runCatching {
        val json = JSONObject(raw)
        LockScreenSettings(
            wallpaperPath = json.optString("wallpaperPath").trim(),
            wallpaperBlurRadius = json.optDouble("wallpaperBlurRadius", 0.0)
                .toFloat()
                .coerceIn(0f, 30f),
            scrimColorArgb = json.optLong("scrimColorArgb", 0xFF000000L).toInt(),
            scrimOpacity = json.optDouble("scrimOpacity", 0.28)
                .toFloat()
                .coerceIn(0f, 1f),
            scrimStyle = LockScreenScrimStyle.fromWireName(
                json.optString("scrimStyle", LockScreenScrimStyle.EdgeGradient.wireName)
            ),
            timeAndDateAlignedStart = json.optBoolean("timeAndDateAlignedStart", false),
            useSystemFont = json.optBoolean("useSystemFont", false),
            useSeparateClockFont = json.optBoolean("useSeparateClockFont", false),
            clockFontId = UserPrefs.normalizeAppFontId(
                json.optString("clockFontId", AppFontDefaults.SystemFontId)
            ),
            clockFontWeight = UserPrefs.normalizeAppFontWeight(
                json.optInt("clockFontWeight", AppFontDefaults.DefaultWeight)
            ),
            showLunarDate = json.optBoolean("showLunarDate", false)
        ).normalized()
    }.getOrDefault(LockScreenSettings())
}
