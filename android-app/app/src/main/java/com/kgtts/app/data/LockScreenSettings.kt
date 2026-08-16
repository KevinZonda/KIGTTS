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

enum class LockScreenBatteryStyle(val wireName: String) {
    Compact("compact"),
    Detailed("detailed");

    companion object {
        fun fromWireName(value: String): LockScreenBatteryStyle =
            entries.firstOrNull { it.wireName == value } ?: Compact
    }
}

data class LockScreenBatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
    val isFull: Boolean
)

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
    val showLunarDate: Boolean = false,
    val showBatteryStatus: Boolean = true,
    val batteryStyle: LockScreenBatteryStyle = LockScreenBatteryStyle.Compact,
    val batteryOnlyWhenChargingOrLow: Boolean = false,
    val lowBatteryThreshold: Int = DEFAULT_LOW_BATTERY_THRESHOLD
)

internal fun LockScreenSettings.normalized(): LockScreenSettings = copy(
    wallpaperBlurRadius = wallpaperBlurRadius.coerceIn(0f, 30f),
    scrimColorArgb = scrimColorArgb or 0xFF000000.toInt(),
    scrimOpacity = scrimOpacity.coerceIn(0f, 1f),
    clockFontId = UserPrefs.normalizeAppFontId(clockFontId),
    clockFontWeight = UserPrefs.normalizeAppFontWeight(clockFontWeight),
    lowBatteryThreshold = lowBatteryThreshold.coerceIn(
        MIN_LOW_BATTERY_THRESHOLD,
        MAX_LOW_BATTERY_THRESHOLD
    )
)

internal fun LockScreenSettings.shouldShowBatteryStatus(
    status: LockScreenBatteryStatus
): Boolean {
    if (!showBatteryStatus || status.percentage !in 0..100) return false
    return !batteryOnlyWhenChargingOrLow ||
        status.isCharging ||
        status.percentage < lowBatteryThreshold
}

internal fun LockScreenSettings.formatBatteryStatus(
    status: LockScreenBatteryStatus
): String {
    val stateLabel = when {
        status.isFull -> "已充满"
        status.isCharging -> "正在充电"
        else -> "未充电"
    }
    return when (batteryStyle) {
        LockScreenBatteryStyle.Compact -> {
            if (status.isCharging) "${status.percentage}% · $stateLabel" else "${status.percentage}%"
        }
        LockScreenBatteryStyle.Detailed -> "电量 ${status.percentage}% · $stateLabel"
    }
}

internal fun encodeLockScreenSettings(settings: LockScreenSettings): String {
    val normalized = settings.normalized()
    return JSONObject()
        .put("version", 3)
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
        .put("showBatteryStatus", normalized.showBatteryStatus)
        .put("batteryStyle", normalized.batteryStyle.wireName)
        .put(
            "batteryOnlyWhenChargingOrLow",
            normalized.batteryOnlyWhenChargingOrLow
        )
        .put("lowBatteryThreshold", normalized.lowBatteryThreshold)
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
            showLunarDate = json.optBoolean("showLunarDate", false),
            showBatteryStatus = json.optBoolean("showBatteryStatus", true),
            batteryStyle = LockScreenBatteryStyle.fromWireName(
                json.optString("batteryStyle", LockScreenBatteryStyle.Compact.wireName)
            ),
            batteryOnlyWhenChargingOrLow = json.optBoolean(
                "batteryOnlyWhenChargingOrLow",
                false
            ),
            lowBatteryThreshold = json.optInt(
                "lowBatteryThreshold",
                DEFAULT_LOW_BATTERY_THRESHOLD
            )
        ).normalized()
    }.getOrDefault(LockScreenSettings())
}

internal const val DEFAULT_LOW_BATTERY_THRESHOLD = 30
internal const val MIN_LOW_BATTERY_THRESHOLD = 1
internal const val MAX_LOW_BATTERY_THRESHOLD = 100
