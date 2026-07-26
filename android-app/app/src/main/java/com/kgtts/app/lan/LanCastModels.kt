package com.lhtstudio.kigtts.app.lan

import org.json.JSONArray
import org.json.JSONObject

internal enum class LanCastAudioOutputMode(
    val preferenceValue: Int,
    val label: String
) {
    Local(0, "仅本机"),
    Web(1, "仅投屏端"),
    Both(2, "本机与投屏端");

    companion object {
        fun fromPreferenceValue(value: Int): LanCastAudioOutputMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Local
    }
}

internal data class LanCastPlaybackPlan(
    val local: Boolean,
    val web: Boolean
)

internal fun resolveLanCastPlaybackPlan(
    mode: LanCastAudioOutputMode,
    webAvailable: Boolean
): LanCastPlaybackPlan = when (mode) {
    LanCastAudioOutputMode.Local -> LanCastPlaybackPlan(local = true, web = false)
    LanCastAudioOutputMode.Web -> LanCastPlaybackPlan(
        local = !webAvailable,
        web = webAvailable
    )
    LanCastAudioOutputMode.Both -> LanCastPlaybackPlan(
        local = true,
        web = webAvailable
    )
}

internal fun isLanCastAudioClientAvailable(
    running: Boolean,
    audioClients: Int,
    lastSeenAtMs: Long,
    nowMs: Long,
    reconnectGraceMs: Long
): Boolean {
    if (!running) return false
    if (audioClients > 0) return true
    return lastSeenAtMs > 0L &&
        nowMs >= lastSeenAtMs &&
        nowMs - lastSeenAtMs <= reconnectGraceMs
}

internal data class LanCastAddress(
    val id: String,
    val interfaceName: String,
    val address: String
) {
    val label: String
        get() = interfaceName.ifBlank { address }
}

internal data class LanCastStatus(
    val running: Boolean = false,
    val port: Int = LanCastRuntime.DEFAULT_PORT,
    val addresses: List<LanCastAddress> = emptyList(),
    val selectedAddressId: String? = null,
    val displayClients: Int = 0,
    val remoteClients: Int = 0,
    val audioClients: Int = 0,
    val error: String? = null
) {
    val selectedAddress: LanCastAddress?
        get() = addresses.firstOrNull { it.id == selectedAddressId } ?: addresses.firstOrNull()

    fun url(path: String): String? = selectedAddress?.let {
        "http://${it.address}:$port/${path.trimStart('/')}"
    }
}

internal data class LanCastQuickTextGroup(
    val id: Long,
    val title: String,
    val icon: String,
    val items: List<String>
)

internal data class LanCastLedStyle(
    val colorArgb: Int = -1,
    val backgroundArgb: Int = 0xFF000000.toInt(),
    val dotMatrix: Boolean = false,
    val dotShape: Int = 0,
    val dotDensity: Float = 0.58f,
    val dotSize: Float = 8f,
    val dotGap: Float = 2f,
    val glowEnabled: Boolean = true,
    val glowStrength: Float = 0.42f,
    val displayHeightFraction: Float = 0.72f,
    val adaptiveMultiLine: Boolean = true,
    val speed: Float = 72f,
    val direction: Int = 0,
    val quickSwipeOpensQuickText: Boolean = true,
    val loopGap: Float = 96f,
    val shortTextAlignment: Int = 1,
    val keepScreenOn: Boolean = true,
    val followSystemBrightness: Boolean = false,
    val screenBrightness: Float = 1f
)

internal data class LanCastThemeRoles(
    val primaryArgb: Int = 0xFF038387.toInt(),
    val onPrimaryArgb: Int = 0xFFFFFFFF.toInt(),
    val accentTextArgb: Int = 0xFF038387.toInt()
)

internal sealed interface LanCastUiCommand {
    data class UpdateDisplaySettings(val settings: LanCastLedStyle) : LanCastUiCommand
    data class SelectQuickTextGroup(val groupId: Long) : LanCastUiCommand
    data class AddCurrentText(val groupId: Long) : LanCastUiCommand
    data class SetPlayOnSend(val enabled: Boolean) : LanCastUiCommand
    data class UpdateQuickSubtitleStyle(
        val bold: Boolean,
        val centered: Boolean,
        val rotated180: Boolean,
        val fontSizeSp: Float
    ) : LanCastUiCommand
    data class SetAudioOutputMode(val mode: Int) : LanCastUiCommand
}

internal data class LanCastPresentationState(
    val revision: Long = 0L,
    val text: String = "点击下方快捷文本或输入文字",
    val inputText: String = "",
    val keepInputPreview: Boolean = true,
    val bold: Boolean = true,
    val centered: Boolean = false,
    val autoFit: Boolean = true,
    val fontSizeSp: Float = 56f,
    val fontWeight: Int = 400,
    val fontRevision: Long = 0L,
    val rotated180: Boolean = false,
    val themeColorArgb: Int = 0xFF038387.toInt(),
    val darkTheme: Boolean = true,
    val themeToneCorrectionEnabled: Boolean = false,
    val lightThemeRoles: LanCastThemeRoles = LanCastThemeRoles(),
    val darkThemeRoles: LanCastThemeRoles = LanCastThemeRoles(),
    val running: Boolean = false,
    val playbackProgress: Float = 0f,
    val selectedGroupId: Long = 0L,
    val groups: List<LanCastQuickTextGroup> = emptyList(),
    val compactQuickText: Boolean = false,
    val led: LanCastLedStyle = LanCastLedStyle(),
    val playOnSend: Boolean = true,
    val audioOutputMode: Int = LanCastAudioOutputMode.Local.preferenceValue
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "state")
        put("revision", revision)
        put("text", text)
        put("inputText", inputText)
        put("previewActive", keepInputPreview && inputText.isNotBlank())
        put("bold", bold)
        put("centered", centered)
        put("autoFit", autoFit)
        put("fontSizeSp", fontSizeSp.toDouble())
        put("fontWeight", fontWeight)
        put("fontUrl", if (fontRevision > 0L) "/font/current?v=$fontRevision" else "")
        put("rotated180", rotated180)
        put("themeColor", themeColorArgb.toCssColor())
        put("darkTheme", darkTheme)
        put("themeToneCorrectionEnabled", themeToneCorrectionEnabled)
        put("themeLight", lightThemeRoles.toJson())
        put("themeDark", darkThemeRoles.toJson())
        put("running", running)
        put("playbackProgress", playbackProgress.toDouble())
        put("selectedGroupId", selectedGroupId)
        put("compactQuickText", compactQuickText)
        put("playOnSend", playOnSend)
        put("audioOutputMode", audioOutputMode)
        put("groups", JSONArray().apply {
            groups.forEach { group ->
                put(JSONObject().apply {
                    put("id", group.id)
                    put("title", group.title)
                    put("icon", group.icon)
                    put("items", JSONArray(group.items))
                })
            }
        })
        put("led", JSONObject().apply {
            put("color", led.colorArgb.toCssColor())
            put("background", led.backgroundArgb.toCssColor())
            put("dotMatrix", led.dotMatrix)
            put("dotShape", led.dotShape)
            put("dotDensity", led.dotDensity.toDouble())
            put("dotSize", led.dotSize.toDouble())
            put("dotGap", led.dotGap.toDouble())
            put("glowEnabled", led.glowEnabled)
            put("glowStrength", led.glowStrength.toDouble())
            put("displayHeightFraction", led.displayHeightFraction.toDouble())
            put("adaptiveMultiLine", led.adaptiveMultiLine)
            put("speed", led.speed.toDouble())
            put("direction", led.direction)
            put("quickSwipeOpensQuickText", led.quickSwipeOpensQuickText)
            put("loopGap", led.loopGap.toDouble())
            put("shortTextAlignment", led.shortTextAlignment)
            put("keepScreenOn", led.keepScreenOn)
            put("followSystemBrightness", led.followSystemBrightness)
            put("screenBrightness", led.screenBrightness.toDouble())
        })
    }
}

private fun LanCastThemeRoles.toJson(): JSONObject = JSONObject().apply {
    put("primary", primaryArgb.toCssColor())
    put("onPrimary", onPrimaryArgb.toCssColor())
    put("accentText", accentTextArgb.toCssColor())
}

private fun Int.toCssColor(): String = "#%06X".format(this and 0x00FFFFFF)
