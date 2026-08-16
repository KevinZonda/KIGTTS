package com.lhtstudio.kigtts.app.data

import com.lhtstudio.kigtts.app.audio.AudioDenoiserMode
import com.lhtstudio.kigtts.app.audio.AudioRoutePreference
import com.lhtstudio.kigtts.app.audio.SpeechEnhancementMode
import org.json.JSONObject

data class ListeningModeSettings(
    val enabled: Boolean = false,
    val modePromptDismissed: Boolean = false,
    val preferredSpeechButtonMode: Int = SpeechButtonActionMode.HOLD,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val rotated180: Boolean = false,
    val portraitPanelsSwapped: Boolean = false,
    val landscapePanelsSwapped: Boolean = false,
    val floatingOverlayLandscapePanelsSwapped: Boolean = false,
    val hideDuringTextInput: Boolean = true,
    val recognitionLanguage: String = AsrRecognitionLanguage.DEFAULT,
    val preferredInputType: Int = AudioRoutePreference.INPUT_BUILTIN_MIC,
    val minVolumePercent: Int = 2,
    val denoiserMode: Int = AudioDenoiserMode.RNNOISE,
    val speechEnhancementMode: Int = SpeechEnhancementMode.DPDFNET4_STREAMING,
    val classicVadEnabled: Boolean = false,
    val sileroVadEnabled: Boolean = true,
    val sileroVadThreshold: Float = UserPrefs.SILERO_VAD_DEFAULT_THRESHOLD,
    val sileroVadPreRollMs: Int = UserPrefs.SILERO_VAD_DEFAULT_PRE_ROLL_MS
) {
    fun normalized(): ListeningModeSettings = copy(
        preferredSpeechButtonMode = when (
            SpeechButtonActionMode.normalize(preferredSpeechButtonMode)
        ) {
            SpeechButtonActionMode.HOLD_CONFIRM -> SpeechButtonActionMode.HOLD_CONFIRM
            else -> SpeechButtonActionMode.HOLD
        },
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        recognitionLanguage = AsrRecognitionLanguage.normalize(recognitionLanguage),
        preferredInputType = preferredInputType.coerceIn(
            AudioRoutePreference.INPUT_AUTO,
            AudioRoutePreference.INPUT_WIRED
        ),
        minVolumePercent = minVolumePercent.coerceIn(0, 100),
        denoiserMode = denoiserMode.coerceIn(AudioDenoiserMode.OFF, AudioDenoiserMode.SPEEX),
        speechEnhancementMode = SpeechEnhancementMode.clamp(speechEnhancementMode),
        sileroVadThreshold = sileroVadThreshold.coerceIn(
            UserPrefs.SILERO_VAD_MIN_THRESHOLD,
            UserPrefs.SILERO_VAD_MAX_THRESHOLD
        ),
        sileroVadPreRollMs = sileroVadPreRollMs.coerceIn(
            UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS,
            UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS
        )
    )

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("modePromptDismissed", modePromptDismissed)
        put("preferredSpeechButtonMode", preferredSpeechButtonMode)
        put("fontSizeSp", fontSizeSp.toDouble())
        put("rotated180", rotated180)
        put("portraitPanelsSwapped", portraitPanelsSwapped)
        put("landscapePanelsSwapped", landscapePanelsSwapped)
        put("floatingOverlayLandscapePanelsSwapped", floatingOverlayLandscapePanelsSwapped)
        put("hideDuringTextInput", hideDuringTextInput)
        put("recognitionLanguage", recognitionLanguage)
        put("preferredInputType", preferredInputType)
        put("minVolumePercent", minVolumePercent)
        put("denoiserMode", denoiserMode)
        put("speechEnhancementMode", speechEnhancementMode)
        put("classicVadEnabled", classicVadEnabled)
        put("sileroVadEnabled", sileroVadEnabled)
        put("sileroVadThreshold", sileroVadThreshold.toDouble())
        put("sileroVadPreRollMs", sileroVadPreRollMs)
    }.toString()

    companion object {
        const val MIN_FONT_SIZE_SP = 20f
        const val MAX_FONT_SIZE_SP = 160f
        const val DEFAULT_FONT_SIZE_SP = MIN_FONT_SIZE_SP

        fun fromJson(raw: String?): ListeningModeSettings {
            if (raw.isNullOrBlank()) return ListeningModeSettings()
            return runCatching {
                val json = JSONObject(raw)
                val landscapePanelsSwapped = json.optBoolean("landscapePanelsSwapped", false)
                ListeningModeSettings(
                    enabled = json.optBoolean("enabled", false),
                    modePromptDismissed = json.optBoolean("modePromptDismissed", false),
                    preferredSpeechButtonMode = json.optInt(
                        "preferredSpeechButtonMode",
                        SpeechButtonActionMode.HOLD
                    ),
                    fontSizeSp = json.optDouble(
                        "fontSizeSp",
                        DEFAULT_FONT_SIZE_SP.toDouble()
                    ).toFloat(),
                    rotated180 = json.optBoolean("rotated180", false),
                    portraitPanelsSwapped = json.optBoolean("portraitPanelsSwapped", false),
                    landscapePanelsSwapped = landscapePanelsSwapped,
                    floatingOverlayLandscapePanelsSwapped = json.optBoolean(
                        "floatingOverlayLandscapePanelsSwapped",
                        landscapePanelsSwapped
                    ),
                    hideDuringTextInput = json.optBoolean("hideDuringTextInput", true),
                    recognitionLanguage = json.optString(
                        "recognitionLanguage",
                        AsrRecognitionLanguage.DEFAULT
                    ),
                    preferredInputType = json.optInt(
                        "preferredInputType",
                        AudioRoutePreference.INPUT_BUILTIN_MIC
                    ),
                    minVolumePercent = json.optInt("minVolumePercent", 2),
                    denoiserMode = json.optInt("denoiserMode", AudioDenoiserMode.RNNOISE),
                    speechEnhancementMode = json.optInt(
                        "speechEnhancementMode",
                        SpeechEnhancementMode.DPDFNET4_STREAMING
                    ),
                    classicVadEnabled = json.optBoolean("classicVadEnabled", false),
                    sileroVadEnabled = json.optBoolean("sileroVadEnabled", true),
                    sileroVadThreshold = json.optDouble(
                        "sileroVadThreshold",
                        UserPrefs.SILERO_VAD_DEFAULT_THRESHOLD.toDouble()
                    ).toFloat(),
                    sileroVadPreRollMs = json.optInt(
                        "sileroVadPreRollMs",
                        UserPrefs.SILERO_VAD_DEFAULT_PRE_ROLL_MS
                    )
                ).normalized()
            }.getOrDefault(ListeningModeSettings())
        }
    }
}
