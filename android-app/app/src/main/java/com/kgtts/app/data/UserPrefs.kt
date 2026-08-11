package com.lhtstudio.kigtts.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhtstudio.kigtts.app.audio.AudioDenoiserMode
import com.lhtstudio.kigtts.app.audio.AudioRoutePreference
import com.lhtstudio.kigtts.app.audio.SpeechEnhancementMode
import com.lhtstudio.kigtts.app.audio.SpeakerVerificationTolerance
import com.lhtstudio.kigtts.app.util.VolumeHotkeyActionSpec
import com.lhtstudio.kigtts.app.util.VolumeHotkeyActions
import com.lhtstudio.kigtts.app.util.VolumeHotkeySequence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

internal fun resolveQuickSubtitleFirstRunGuideCompleted(
    stored: Boolean?,
    onboardingCompleted: Boolean
): Boolean = stored ?: onboardingCompleted

internal fun resolveQuickSubtitleStartupText(
    savedText: String?,
    clearedPlaceholderText: String?,
    restoreLastTextOnLaunch: Boolean
): String {
    val placeholder = UserPrefs.normalizeQuickSubtitleClearedPlaceholder(clearedPlaceholderText)
    val saved = savedText?.trim().orEmpty()
    return if (restoreLastTextOnLaunch && saved.isNotEmpty()) saved else placeholder
}

object UserPrefs {
    const val DEFAULT_QUICK_SUBTITLE_CLEARED_PLACEHOLDER =
        "我不太方便说话，请等我一下……"
    const val QUICK_SUBTITLE_CLEARED_PLACEHOLDER_MAX_LENGTH = 200
    const val DRAWER_MODE_HIDDEN = 0
    const val DRAWER_MODE_PERMANENT = 1
    const val THEME_MODE_FOLLOW_SYSTEM = 0
    const val THEME_MODE_LIGHT = 1
    const val THEME_MODE_DARK = 2
    const val DEFAULT_THEME_COLOR_ARGB = -16546937 // #038387
    const val FONT_SCALE_BLOCK_NONE = 0
    const val FONT_SCALE_BLOCK_ICONS_ONLY = 1
    const val FONT_SCALE_BLOCK_ALL = 2
    const val AUDIO_FOCUS_AVOID_DUCK = 0
    const val AUDIO_FOCUS_AVOID_MUTE = 1
    const val AUDIO_FOCUS_AVOID_PAUSE = 2
    const val AUDIO_FOCUS_AVOID_NONE = 3
    const val LAN_CAST_AUDIO_LOCAL = 0
    const val LAN_CAST_AUDIO_WEB = 1
    const val LAN_CAST_AUDIO_BOTH = 2
    const val DEFAULT_DRAWING_SAVE_RELATIVE_PATH = "Pictures/KGTTS/Drawings"
    const val SILERO_VAD_MIN_THRESHOLD = 0.05f
    const val SILERO_VAD_MAX_THRESHOLD = 0.95f
    const val SILERO_VAD_DEFAULT_THRESHOLD = 0.5f
    const val SILERO_VAD_MIN_PRE_ROLL_MS = 0
    const val SILERO_VAD_MAX_PRE_ROLL_MS = 800
    private const val SILERO_VAD_PREVIOUS_DEFAULT_PRE_ROLL_MS = 100
    const val SILERO_VAD_DEFAULT_PRE_ROLL_MS = 240
    const val VOLUME_HOTKEY_MIN_WINDOW_MS = 500
    const val VOLUME_HOTKEY_MAX_WINDOW_MS = 3000
    const val VOLUME_HOTKEY_DEFAULT_WINDOW_MS = 1500
    const val RECOGNITION_RESOURCE_SOURCE_MODELSCOPE = 0
    const val RECOGNITION_RESOURCE_SOURCE_HUGGINGFACE = 1
    const val APP_FONT_SOURCE_MODELSCOPE = 0
    const val APP_FONT_SOURCE_HUGGINGFACE = 1
    const val KOKORO_SOURCE_HF = 0
    const val KOKORO_SOURCE_HFMIRROR = 1
    const val KOKORO_SOURCE_MODELSCOPE = 2
    const val KOKORO_MIN_SPEAKER_ID = 0
    const val KOKORO_MAX_SPEAKER_ID = 102
    const val KOKORO_DEFAULT_SPEAKER_ID = 3
    const val DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-v6-20260810.7z"
    const val DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-v6-20260810.7z"
    private const val LEGACY_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-20260505.7z"
    private const val LEGACY_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-20260505.7z"
    private const val PREVIOUS_EXPERIMENTAL_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-experimental-20260808.7z"
    private const val PREVIOUS_EXPERIMENTAL_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-experimental-20260808.7z"
    private const val PREVIOUS_V4_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-experimental-v4-20260808.7z"
    private const val PREVIOUS_V4_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-experimental-v4-20260808.7z"
    private const val PREVIOUS_V5_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-experimental-v5-20260809.7z"
    private const val PREVIOUS_V5_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-experimental-v5-20260809.7z"
    private const val PREVIOUS_V6_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
            "kigtts-recognition-resources-experimental-v6-20260809.7z"
    private const val PREVIOUS_V6_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
            "kigtts-recognition-resources-experimental-v6-20260809.7z"
    const val DEFAULT_KOKORO_HF_URL =
        "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_1"
    const val DEFAULT_KOKORO_HFMIRROR_URL =
        "https://hf-mirror.com/csukuangfj/kokoro-multi-lang-v1_1"
    const val DEFAULT_KOKORO_MODELSCOPE_URL =
        "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_KOKORO_Resource"

    private val KEY_LAST_ASR = stringPreferencesKey("last_asr_name")
    private val KEY_LAST_VOICE = stringPreferencesKey("last_voice_name")
    private val KEY_SYSTEM_TTS_ORDER = longPreferencesKey("system_tts_order")
    private val KEY_SYSTEM_TTS_PINNED = booleanPreferencesKey("system_tts_pinned")
    private val KEY_MUTE_WHILE_PLAYING = booleanPreferencesKey("mute_while_playing")
    private val KEY_MUTE_DELAY_SEC = floatPreferencesKey("mute_delay_sec")
    private val KEY_ECHO_SUPPRESSION = booleanPreferencesKey("echo_suppression")
    private val KEY_COMMUNICATION_MODE = booleanPreferencesKey("communication_mode")
    private val KEY_COMMUNICATION_SPEAKER = booleanPreferencesKey("communication_speaker")
    private val KEY_PREFER_USB_MIC = booleanPreferencesKey("prefer_usb_mic")
    private val KEY_PREFERRED_INPUT_TYPE = intPreferencesKey("preferred_input_type")
    private val KEY_PREFERRED_OUTPUT_TYPE = intPreferencesKey("preferred_output_type")
    private val KEY_AEC3_ENABLED = booleanPreferencesKey("aec3_enabled")
    private val KEY_DENOISER_MODE = intPreferencesKey("denoiser_mode")
    private val KEY_SPEECH_ENHANCEMENT_ENABLED = booleanPreferencesKey("speech_enhancement_enabled")
    private val KEY_SPEECH_ENHANCEMENT_MODE = intPreferencesKey("speech_enhancement_mode")
    private val KEY_CLASSIC_VAD_ENABLED = booleanPreferencesKey("classic_vad_enabled")
    private val KEY_SILERO_VAD_ENABLED = booleanPreferencesKey("silero_vad_enabled")
    private val KEY_SILERO_VAD_THRESHOLD = floatPreferencesKey("silero_vad_threshold")
    private val KEY_SILERO_VAD_PRE_ROLL_MS = intPreferencesKey("silero_vad_pre_roll_ms")
    private val KEY_RECOGNITION_RESOURCE_MODELSCOPE_URL =
        stringPreferencesKey("recognition_resource_modelscope_url")
    private val KEY_RECOGNITION_RESOURCE_HUGGINGFACE_URL =
        stringPreferencesKey("recognition_resource_huggingface_url")
    private val KEY_RECOGNITION_RESOURCE_PREFERRED_SOURCE =
        intPreferencesKey("recognition_resource_preferred_source")
    private val KEY_KOKORO_HF_URL = stringPreferencesKey("kokoro_hf_url")
    private val KEY_KOKORO_HFMIRROR_URL = stringPreferencesKey("kokoro_hfmirror_url")
    private val KEY_KOKORO_MODELSCOPE_URL = stringPreferencesKey("kokoro_modelscope_url")
    private val KEY_KOKORO_PREFERRED_SOURCE = intPreferencesKey("kokoro_preferred_source")
    private val KEY_KOKORO_SPEAKER_ID = intPreferencesKey("kokoro_speaker_id")
    private val KEY_KOKORO_VOICE_ORDER = longPreferencesKey("kokoro_voice_order")
    private val KEY_KOKORO_VOICE_PINNED = booleanPreferencesKey("kokoro_voice_pinned")
    private val KEY_MIN_VOLUME_PERCENT = intPreferencesKey("min_volume_percent")
    private val KEY_PLAYBACK_GAIN_PERCENT = intPreferencesKey("playback_gain_percent")
    private val KEY_AUDIO_FOCUS_AVOIDANCE_MODE = intPreferencesKey("audio_focus_avoidance_mode")
    private val KEY_PIPER_NOISE_SCALE = floatPreferencesKey("piper_noise_scale")
    private val KEY_PIPER_LENGTH_SCALE = floatPreferencesKey("piper_length_scale")
    private val KEY_PIPER_NOISE_W = floatPreferencesKey("piper_noise_w")
    private val KEY_PIPER_SENTENCE_SILENCE = floatPreferencesKey("piper_sentence_silence")
    private val KEY_KEEP_ALIVE = booleanPreferencesKey("keep_alive")
    private val KEY_ASR_RECOGNITION_LANGUAGE =
        stringPreferencesKey("asr_recognition_language")
    private val KEY_LANDSCAPE_DRAWER_MODE = intPreferencesKey("landscape_drawer_mode")
    private val KEY_SOLID_TOP_BAR = booleanPreferencesKey("solid_top_bar")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_OVERLAY_THEME_MODE = intPreferencesKey("overlay_theme_mode")
    private val KEY_THEME_COLOR_ARGB = intPreferencesKey("theme_color_argb")
    private val KEY_THEME_TONE_CORRECTION_ENABLED = booleanPreferencesKey("theme_tone_correction_enabled")
    private val KEY_APP_FONT_ID = stringPreferencesKey("app_font_id")
    private val KEY_APP_FONT_WEIGHT = intPreferencesKey("app_font_weight")
    private val KEY_APP_FONT_MODELSCOPE_URL = stringPreferencesKey("app_font_modelscope_url")
    private val KEY_APP_FONT_HUGGINGFACE_URL = stringPreferencesKey("app_font_huggingface_url")
    private val KEY_APP_FONT_PREFERRED_SOURCE = intPreferencesKey("app_font_preferred_source")
    private val KEY_FLOATING_OVERLAY_USE_SYSTEM_FONT =
        booleanPreferencesKey("floating_overlay_use_system_font")
    private val KEY_USE_SYSTEM_TEXT_TOOLBAR = booleanPreferencesKey("use_system_text_toolbar")
    private val KEY_FONT_SCALE_BLOCK_MODE = intPreferencesKey("font_scale_block_mode")
    private val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
    private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val KEY_ONBOARDING_QUICK_SUBTITLE_PRESETS_INSTALLED =
        booleanPreferencesKey("onboarding_quick_subtitle_presets_installed")
    private val KEY_FORCE_FULL_WIDTH_TABS_ON_PHONE =
        booleanPreferencesKey("force_full_width_tabs_on_phone")
    private val KEY_SOUNDBOARD_GRID_FULL_WIDTH =
        booleanPreferencesKey("soundboard_grid_full_width")
    private val KEY_INTERNAL_WEBVIEW_ENABLED = booleanPreferencesKey("internal_webview_enabled")
    private val KEY_DRAWING_SAVE_RELATIVE_PATH = stringPreferencesKey("drawing_save_relative_path")
    private val KEY_QUICK_CARD_AUTO_SAVE_ON_EXIT = booleanPreferencesKey("quick_card_auto_save_on_exit")
    private val KEY_USE_BUILTIN_FILE_MANAGER = booleanPreferencesKey("use_builtin_file_manager")
    private val KEY_USE_BUILTIN_GALLERY = booleanPreferencesKey("use_builtin_gallery")
    private val KEY_ASR_SEND_TO_QUICK_SUBTITLE = booleanPreferencesKey("asr_send_to_quick_subtitle")
    private val KEY_PUSH_TO_TALK_MODE = booleanPreferencesKey("push_to_talk_mode")
    private val KEY_PUSH_TO_TALK_CONFIRM_INPUT = booleanPreferencesKey("push_to_talk_confirm_input")
    private val KEY_SPEECH_BUTTON_ACTION_MODE = intPreferencesKey("speech_button_action_mode")
    private val KEY_FLOATING_OVERLAY_ENABLED = booleanPreferencesKey("floating_overlay_enabled")
    private val KEY_FLOATING_OVERLAY_AUTO_DOCK = booleanPreferencesKey("floating_overlay_auto_dock")
    private val KEY_FLOATING_OVERLAY_SHOW_ON_LOCK_SCREEN =
        booleanPreferencesKey("floating_overlay_show_on_lock_screen")
    private val KEY_LOCK_SCREEN_BACKGROUND_PERMISSION_GUIDE_SHOWN =
        booleanPreferencesKey("lock_screen_background_permission_guide_shown")
    private val KEY_LOCK_SCREEN_SETTINGS = stringPreferencesKey("lock_screen_settings")
    private val KEY_FLOATING_OVERLAY_FAB_PREFERS_KEYBOARD =
        booleanPreferencesKey("floating_overlay_fab_prefers_keyboard")
    private val KEY_FLOATING_OVERLAY_FAB_INPUT_GUIDE_SHOWN =
        booleanPreferencesKey("floating_overlay_fab_input_guide_shown")
    private val KEY_FLOATING_OVERLAY_HARDCODED_SHORTCUT_SUPPLEMENT =
        booleanPreferencesKey("floating_overlay_hardcoded_shortcut_supplement")
    private val KEY_QUICK_TEXT_GESTURE_SETTINGS =
        stringPreferencesKey("quick_text_gesture_settings")
    private val KEY_VOLUME_HOTKEY_UP_DOWN_ENABLED = booleanPreferencesKey("volume_hotkey_up_down_enabled")
    private val KEY_VOLUME_HOTKEY_DOWN_UP_ENABLED = booleanPreferencesKey("volume_hotkey_down_up_enabled")
    private val KEY_VOLUME_HOTKEY_UP_DOWN_ACTION = stringPreferencesKey("volume_hotkey_up_down_action")
    private val KEY_VOLUME_HOTKEY_DOWN_UP_ACTION = stringPreferencesKey("volume_hotkey_down_up_action")
    private val KEY_VOLUME_HOTKEY_WINDOW_MS = intPreferencesKey("volume_hotkey_window_ms")
    private val KEY_VOLUME_HOTKEY_ACCESSIBILITY_ENABLED =
        booleanPreferencesKey("volume_hotkey_accessibility_enabled")
    private val KEY_VOLUME_HOTKEY_ENABLE_WARNING_DISMISSED =
        booleanPreferencesKey("volume_hotkey_enable_warning_dismissed")
    private val KEY_FLOATING_OVERLAY_SHORTCUTS = stringPreferencesKey("floating_overlay_shortcuts")
    private val KEY_FLOATING_OVERLAY_DEFAULT_SHORTCUTS_SEEDED =
        booleanPreferencesKey("floating_overlay_default_shortcuts_seeded")
    private val KEY_FLOATING_OVERLAY_LAYOUT = stringPreferencesKey("floating_overlay_layout")
    private val KEY_FLOATING_OVERLAY_QUICK_SUBTITLE_FONT_SIZE = floatPreferencesKey("floating_overlay_quick_subtitle_font_size")
    private val KEY_FLOATING_OVERLAY_MINI_SOUNDBOARD_LAYOUT =
        stringPreferencesKey("floating_overlay_mini_soundboard_layout")
    private val KEY_QUICK_SUBTITLE_CONFIG = stringPreferencesKey("quick_subtitle_config")
    private val KEY_SOUNDBOARD_CONFIG = stringPreferencesKey("soundboard_config")
    private val KEY_QUICK_CARD_CONFIG = stringPreferencesKey("quick_card_config")
    private val KEY_TTS_DISABLED = booleanPreferencesKey("tts_disabled")
    private val KEY_SOUNDBOARD_KEYWORD_TRIGGER_ENABLED = booleanPreferencesKey("soundboard_keyword_trigger_enabled")
    private val KEY_SOUNDBOARD_SUPPRESS_TTS_ON_KEYWORD = booleanPreferencesKey("soundboard_suppress_tts_on_keyword")
    private val KEY_ALLOW_QUICK_TEXT_TRIGGER_SOUNDBOARD = booleanPreferencesKey("allow_quick_text_trigger_soundboard")
    private val KEY_QUICK_SUBTITLE_INTERRUPT_QUEUE = booleanPreferencesKey("quick_subtitle_interrupt_queue")
    private val KEY_QUICK_SUBTITLE_AUTO_FIT = booleanPreferencesKey("quick_subtitle_auto_fit")
    private val KEY_QUICK_SUBTITLE_ALLOW_LARGE_FONT =
        booleanPreferencesKey("quick_subtitle_allow_large_font")
    private val KEY_QUICK_SUBTITLE_COMPACT_CONTROLS = booleanPreferencesKey("quick_subtitle_compact_controls")
    private val KEY_QUICK_SUBTITLE_FREQUENCY_SORT_ENABLED =
        booleanPreferencesKey("quick_subtitle_frequency_sort_enabled")
    private val KEY_QUICK_SUBTITLE_USAGE_STATS =
        stringPreferencesKey("quick_subtitle_usage_stats")
    private val KEY_QUICK_SUBTITLE_PANEL_GESTURES_ENABLED =
        booleanPreferencesKey("quick_subtitle_panel_gestures_enabled")
    private val KEY_QUICK_SUBTITLE_PANEL_GESTURES_REVERSED =
        booleanPreferencesKey("quick_subtitle_panel_gestures_reversed")
    private val KEY_QUICK_SUBTITLE_FIRST_RUN_GUIDE_COMPLETED =
        booleanPreferencesKey("quick_subtitle_first_run_guide_completed")
    private val KEY_QUICK_SUBTITLE_LIST_POPUP_GRID_MODE =
        booleanPreferencesKey("quick_subtitle_list_popup_grid_mode")
    private val KEY_QUICK_SUBTITLE_KEEP_INPUT_PREVIEW =
        booleanPreferencesKey("quick_subtitle_keep_input_preview")
    private val KEY_QUICK_SUBTITLE_CLEARED_PLACEHOLDER =
        stringPreferencesKey("quick_subtitle_cleared_placeholder")
    private val KEY_QUICK_SUBTITLE_RESTORE_LAST_TEXT_ON_LAUNCH =
        booleanPreferencesKey("quick_subtitle_restore_last_text_on_launch")
    private val KEY_LISTENING_MODE_SETTINGS = stringPreferencesKey("listening_mode_settings")
    private val KEY_LED_SUBTITLE_SETTINGS = stringPreferencesKey("led_subtitle_settings")
    private val KEY_LAN_CAST_DISPLAY_SETTINGS = stringPreferencesKey("lan_cast_display_settings")
    private val KEY_BLUETOOTH_MEDIA_TITLE_SUBTITLE =
        booleanPreferencesKey("bluetooth_media_title_subtitle")
    private val KEY_LIVE_SUBTITLE_NOTIFICATION_ENABLED =
        booleanPreferencesKey("live_subtitle_notification_enabled")
    private val KEY_LAN_CAST_AUDIO_OUTPUT_MODE =
        intPreferencesKey("lan_cast_audio_output_mode")
    private val KEY_LAN_CAST_BACKGROUND_REMINDER_DISMISSED =
        booleanPreferencesKey("lan_cast_background_reminder_dismissed")
    private val KEY_DRAWING_KEEP_CANVAS_ORIENTATION_TO_DEVICE =
        booleanPreferencesKey("drawing_keep_canvas_orientation_to_device")
    private val KEY_DRAWING_PALETTE = stringPreferencesKey("drawing_palette")
    private val KEY_SPEAKER_VERIFY_ENABLED = booleanPreferencesKey("speaker_verify_enabled")
    private val KEY_SPEAKER_VERIFY_THRESHOLD = floatPreferencesKey("speaker_verify_threshold")
    private val KEY_SPEAKER_VERIFY_TOLERANCE_LEVEL =
        intPreferencesKey("speaker_verify_tolerance_level")
    private val KEY_RECOGNITION_MODULE_MODE = intPreferencesKey("recognition_module_mode")
    private val KEY_EXPERIMENTAL_RECOGNITION_SENSITIVITY =
        intPreferencesKey("experimental_recognition_sensitivity")
    private val KEY_EXPERIMENTAL_TARGET_SPEAKER_BACKEND =
        intPreferencesKey("experimental_target_speaker_backend")
    private val KEY_SPEAKER_VERIFY_PROFILE = stringPreferencesKey("speaker_verify_profile")
    private val KEY_SPEAKER_VERIFY_BACKEND_VERSION = intPreferencesKey("speaker_verify_backend_version")

    const val SPEAKER_VERIFY_BACKEND_SHERPA_V1 = 1
    const val RECOGNITION_MODULE_MODE_LEGACY = 0
    const val RECOGNITION_MODULE_MODE_EXPERIMENTAL = 1
    const val EXPERIMENTAL_TARGET_SPEAKER_BACKEND_AUTO = 0
    const val EXPERIMENTAL_TARGET_SPEAKER_BACKEND_NEURAL = 1
    const val EXPERIMENTAL_TARGET_SPEAKER_BACKEND_LIGHTWEIGHT = 2

    data class SpeakerVerifyProfile(
        val id: String,
        val name: String,
        val vector: FloatArray,
        val confirmationVector: FloatArray? = null,
        val neuralVector: FloatArray? = null
    )

    data class AppSettings(
        val muteWhilePlaying: Boolean = true,
        val muteWhilePlayingDelaySec: Float = 0.2f,
        val echoSuppression: Boolean = false,
        val communicationMode: Boolean = false,
        val preferredInputType: Int = AudioRoutePreference.INPUT_AUTO,
        val preferredOutputType: Int = AudioRoutePreference.OUTPUT_AUTO,
        val aec3Enabled: Boolean = true,
        val denoiserMode: Int = AudioDenoiserMode.OFF,
        val speechEnhancementMode: Int = SpeechEnhancementMode.GTCRN_STREAMING,
        val classicVadEnabled: Boolean = false,
        val sileroVadEnabled: Boolean = true,
        val sileroVadThreshold: Float = SILERO_VAD_DEFAULT_THRESHOLD,
        val sileroVadPreRollMs: Int = SILERO_VAD_DEFAULT_PRE_ROLL_MS,
        val recognitionResourceModelScopeUrl: String = DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
        val recognitionResourceHuggingFaceUrl: String = DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
        val recognitionResourcePreferredSource: Int = RECOGNITION_RESOURCE_SOURCE_MODELSCOPE,
        val kokoroHfUrl: String = DEFAULT_KOKORO_HF_URL,
        val kokoroHfMirrorUrl: String = DEFAULT_KOKORO_HFMIRROR_URL,
        val kokoroModelScopeUrl: String = DEFAULT_KOKORO_MODELSCOPE_URL,
        val kokoroPreferredSource: Int = KOKORO_SOURCE_MODELSCOPE,
        val kokoroSpeakerId: Int = KOKORO_DEFAULT_SPEAKER_ID,
        val minVolumePercent: Int = 2,
        val playbackGainPercent: Int = 100,
        val audioFocusAvoidanceMode: Int = AUDIO_FOCUS_AVOID_NONE,
        val piperNoiseScale: Float = 0.667f,
        val piperLengthScale: Float = 1.0f,
        val piperNoiseW: Float = 0.8f,
        val piperSentenceSilence: Float = 0.2f,
        val keepAlive: Boolean = true,
        val asrRecognitionLanguage: String = AsrRecognitionLanguage.DEFAULT,
        val landscapeDrawerMode: Int = DRAWER_MODE_PERMANENT,
        val solidTopBar: Boolean = false,
        val themeMode: Int = THEME_MODE_FOLLOW_SYSTEM,
        val overlayThemeMode: Int = THEME_MODE_FOLLOW_SYSTEM,
        val themeColorArgb: Int = DEFAULT_THEME_COLOR_ARGB,
        val themeToneCorrectionEnabled: Boolean = false,
        val appFontId: String = AppFontDefaults.SystemFontId,
        val appFontWeight: Int = AppFontDefaults.DefaultWeight,
        val appFontModelScopeUrl: String = AppFontRemoteSource.ModelScope.defaultRepositoryBaseUrl,
        val appFontHuggingFaceUrl: String = AppFontRemoteSource.HuggingFace.defaultRepositoryBaseUrl,
        val appFontPreferredSource: Int = APP_FONT_SOURCE_MODELSCOPE,
        val floatingOverlayUseSystemFont: Boolean = false,
        val useSystemTextToolbar: Boolean = false,
        val fontScaleBlockMode: Int = FONT_SCALE_BLOCK_ICONS_ONLY,
        val hapticFeedbackEnabled: Boolean = true,
        val onboardingCompleted: Boolean = false,
        val forceFullWidthTabsOnPhone: Boolean = false,
        val soundboardGridFullWidth: Boolean = false,
        val internalWebViewEnabled: Boolean = true,
        val drawingSaveRelativePath: String = DEFAULT_DRAWING_SAVE_RELATIVE_PATH,
        val quickCardAutoSaveOnExit: Boolean = false,
        val useBuiltinFileManager: Boolean = false,
        val useBuiltinGallery: Boolean = false,
        val asrSendToQuickSubtitle: Boolean = true,
        val speechButtonActionMode: Int = SpeechButtonActionMode.TOGGLE,
        val pushToTalkMode: Boolean = false,
        val pushToTalkConfirmInput: Boolean = false,
        val floatingOverlayEnabled: Boolean = false,
        val floatingOverlayAutoDock: Boolean = true,
        val floatingOverlayShowOnLockScreen: Boolean = false,
        val lockScreenBackgroundPermissionGuideShown: Boolean = false,
        val lockScreenSettings: LockScreenSettings = LockScreenSettings(),
        val floatingOverlayFabPrefersKeyboard: Boolean = false,
        val floatingOverlayFabInputGuideShown: Boolean = false,
        val floatingOverlayHardcodedShortcutSupplement: Boolean = false,
        val quickTextGestureSettings: QuickTextGestureSettings = QuickTextGestureSettings(),
        val volumeHotkeyUpDownEnabled: Boolean = false,
        val volumeHotkeyDownUpEnabled: Boolean = false,
        val volumeHotkeyWindowMs: Int = VOLUME_HOTKEY_DEFAULT_WINDOW_MS,
        val volumeHotkeyAccessibilityEnabled: Boolean = false,
        val volumeHotkeyEnableWarningDismissed: Boolean = false,
        val volumeHotkeyUpDownAction: VolumeHotkeyActionSpec =
            VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.UpDown),
        val volumeHotkeyDownUpAction: VolumeHotkeyActionSpec =
            VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.DownUp),
        val ttsDisabled: Boolean = false,
        val soundboardKeywordTriggerEnabled: Boolean = false,
        val soundboardSuppressTtsOnKeyword: Boolean = false,
        val allowQuickTextTriggerSoundboard: Boolean = false,
        val quickSubtitleInterruptQueue: Boolean = true,
        val quickSubtitleAutoFit: Boolean = true,
        val quickSubtitleAllowLargeFont: Boolean = false,
        val quickSubtitleCompactControls: Boolean = false,
        val quickSubtitleFrequencySortEnabled: Boolean = false,
        val quickSubtitleUsageStats: QuickSubtitleUsageStats = QuickSubtitleUsageStats(),
        val quickSubtitlePanelGesturesEnabled: Boolean = true,
        val quickSubtitlePanelGesturesReversed: Boolean = false,
        val quickSubtitleFirstRunGuideCompleted: Boolean = false,
        val quickSubtitleListPopupGridMode: Boolean = true,
        val quickSubtitleKeepInputPreview: Boolean = true,
        val quickSubtitleClearedPlaceholderText: String =
            DEFAULT_QUICK_SUBTITLE_CLEARED_PLACEHOLDER,
        val quickSubtitleRestoreLastTextOnLaunch: Boolean = false,
        val listeningModeSettings: ListeningModeSettings = ListeningModeSettings(),
        val ledSubtitleSettings: LedSubtitleSettings = LedSubtitleSettings(),
        val lanCastDisplaySettings: LedSubtitleSettings = defaultLanCastDisplaySettings(),
        val bluetoothMediaTitleSubtitle: Boolean = false,
        val liveSubtitleNotificationEnabled: Boolean = false,
        val lanCastAudioOutputMode: Int = LAN_CAST_AUDIO_LOCAL,
        val lanCastBackgroundReminderDismissed: Boolean = false,
        val drawingKeepCanvasOrientationToDevice: Boolean = true,
        val drawingPalette: DrawingPalette = DrawingPalette(),
        val speakerVerifyEnabled: Boolean = false,
        val speakerVerifyThreshold: Float = SpeakerVerificationTolerance.SMART.primaryThreshold,
        val speakerVerifyToleranceLevel: Int = SpeakerVerificationTolerance.SMART.index,
        val recognitionModuleMode: Int = RECOGNITION_MODULE_MODE_EXPERIMENTAL,
        val experimentalRecognitionSensitivity: Int = 50,
        val experimentalTargetSpeakerBackend: Int = EXPERIMENTAL_TARGET_SPEAKER_BACKEND_AUTO,
        val speakerVerifyProfileCsv: String = "",
        val speakerVerifyBackendVersion: Int = 0,
        val allowSystemAecWithAec3: Boolean = true
    )

    fun normalizeThemeMode(mode: Int): Int =
        mode.coerceIn(THEME_MODE_FOLLOW_SYSTEM, THEME_MODE_DARK)

    fun normalizeQuickSubtitleClearedPlaceholder(text: String?): String {
        val normalized = text
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            .orEmpty()
            .take(QUICK_SUBTITLE_CLEARED_PLACEHOLDER_MAX_LENGTH)
        return normalized.ifBlank { DEFAULT_QUICK_SUBTITLE_CLEARED_PLACEHOLDER }
    }

    fun normalizeLanCastAudioOutputMode(mode: Int): Int =
        mode.coerceIn(LAN_CAST_AUDIO_LOCAL, LAN_CAST_AUDIO_BOTH)

    fun normalizeThemeColorArgb(colorArgb: Int): Int = colorArgb or (0xFF shl 24)

    fun normalizeAppFontId(id: String): String {
        val normalized = id.trim().lowercase()
        return if (Regex("^[a-z0-9][a-z0-9._-]{0,79}$").matches(normalized)) {
            normalized
        } else {
            AppFontDefaults.SystemFontId
        }
    }

    fun normalizeAppFontWeight(weight: Int): Int =
        weight.coerceIn(AppFontDefaults.MinWeight, AppFontDefaults.MaxWeight)

    fun normalizeFontScaleBlockMode(mode: Int): Int =
        mode.coerceIn(FONT_SCALE_BLOCK_NONE, FONT_SCALE_BLOCK_ALL)

    fun normalizeAudioFocusAvoidanceMode(mode: Int): Int =
        mode.coerceIn(AUDIO_FOCUS_AVOID_DUCK, AUDIO_FOCUS_AVOID_NONE)

    fun normalizeRecognitionResourceModelScopeUrl(url: String?): String =
        normalizeRecognitionResourceUrl(
            url = url,
            builtInDefaults = setOf(
                LEGACY_RECOGNITION_RESOURCE_MODELSCOPE_URL,
                PREVIOUS_EXPERIMENTAL_RECOGNITION_RESOURCE_MODELSCOPE_URL,
                PREVIOUS_V4_RECOGNITION_RESOURCE_MODELSCOPE_URL,
                PREVIOUS_V5_RECOGNITION_RESOURCE_MODELSCOPE_URL,
                PREVIOUS_V6_RECOGNITION_RESOURCE_MODELSCOPE_URL
            ),
            currentDefault = DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL
        )

    fun normalizeRecognitionResourceHuggingFaceUrl(url: String?): String =
        normalizeRecognitionResourceUrl(
            url = url,
            builtInDefaults = setOf(
                LEGACY_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
                PREVIOUS_EXPERIMENTAL_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
                PREVIOUS_V4_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
                PREVIOUS_V5_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
                PREVIOUS_V6_RECOGNITION_RESOURCE_HUGGINGFACE_URL
            ),
            currentDefault = DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL
        )

    private fun normalizeRecognitionResourceUrl(
        url: String?,
        builtInDefaults: Set<String>,
        currentDefault: String
    ): String {
        val normalized = url?.trim().orEmpty()
        return if (normalized.isBlank() || normalized in builtInDefaults) {
            currentDefault
        } else {
            normalized
        }
    }

    fun resolveThemeMode(mode: Int, followSystemDark: Boolean): Boolean =
        when (normalizeThemeMode(mode)) {
            THEME_MODE_LIGHT -> false
            THEME_MODE_DARK -> true
            else -> followSystemDark
        }

    suspend fun getLastAsrName(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LAST_ASR]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLastAsrName(context: Context, name: String) {
        context.dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(KEY_LAST_ASR)
            } else {
                prefs[KEY_LAST_ASR] = name
            }
        }
    }

    suspend fun clearLastAsrName(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_ASR)
        }
    }

    suspend fun getLastVoiceName(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LAST_VOICE]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLastVoiceName(context: Context, name: String) {
        context.dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(KEY_LAST_VOICE)
            } else {
                prefs[KEY_LAST_VOICE] = name
            }
        }
    }

    suspend fun clearLastVoiceName(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_VOICE)
        }
    }

    suspend fun getSystemTtsOrder(context: Context): Long? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_SYSTEM_TTS_ORDER]
    }

    suspend fun setSystemTtsOrder(context: Context, order: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYSTEM_TTS_ORDER] = order
        }
    }

    suspend fun getSystemTtsPinned(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_SYSTEM_TTS_PINNED] ?: false
    }

    suspend fun setSystemTtsPinned(context: Context, pinned: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYSTEM_TTS_PINNED] = pinned
        }
    }

    suspend fun getSettings(context: Context): AppSettings {
        val prefs = context.dataStore.data.first()
        return prefs.toAppSettings()
    }

    suspend fun exportPreferencesForBackup(
        context: Context,
        includeQuickSubtitlePresets: Boolean,
        includeSoundboard: Boolean
    ): JSONObject {
        val excludedNames = buildSet {
            if (!includeQuickSubtitlePresets) add(KEY_QUICK_SUBTITLE_CONFIG.name)
            if (!includeSoundboard) add(KEY_SOUNDBOARD_CONFIG.name)
        }
        val entries = JSONArray()
        context.dataStore.data.first().asMap()
            .entries
            .sortedBy { it.key.name }
            .forEach { (key, value) ->
                if (key.name in excludedNames) return@forEach
                val item = JSONObject().put("name", key.name)
                when (value) {
                    is Boolean -> item.put("type", "boolean").put("value", value)
                    is Int -> item.put("type", "int").put("value", value)
                    is Long -> item.put("type", "long").put("value", value)
                    is Float -> item.put("type", "float").put("value", value.toDouble())
                    is Double -> item.put("type", "double").put("value", value)
                    is String -> item.put("type", "string").put("value", value)
                    is Set<*> -> {
                        val strings = value.filterIsInstance<String>()
                        if (strings.size != value.size) return@forEach
                        item.put("type", "string_set").put("value", JSONArray(strings))
                    }
                    else -> return@forEach
                }
                entries.put(item)
            }
        return JSONObject()
            .put("version", 1)
            .put("entries", entries)
    }

    suspend fun importPreferencesFromBackup(context: Context, payload: JSONObject): Int {
        require(payload.optInt("version", 0) == 1) { "不支持的配置数据版本" }
        val entries = payload.optJSONArray("entries") ?: error("配置备份缺少设置数据")
        var restored = 0
        context.dataStore.edit { prefs ->
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty() || name.length > 160) continue
                when (item.optString("type")) {
                    "boolean" -> prefs[booleanPreferencesKey(name)] = item.optBoolean("value")
                    "int" -> prefs[intPreferencesKey(name)] = item.optInt("value")
                    "long" -> prefs[longPreferencesKey(name)] = item.optLong("value")
                    "float" -> prefs[floatPreferencesKey(name)] = item.optDouble("value").toFloat()
                    "double" -> prefs[doublePreferencesKey(name)] = item.optDouble("value")
                    "string" -> prefs[stringPreferencesKey(name)] = item.optString("value")
                    "string_set" -> {
                        val values = item.optJSONArray("value") ?: continue
                        prefs[stringSetPreferencesKey(name)] = buildSet {
                            for (valueIndex in 0 until values.length()) {
                                values.optString(valueIndex).takeIf { it.isNotEmpty() }?.let(::add)
                            }
                        }
                    }
                    else -> continue
                }
                restored += 1
            }
        }
        return restored
    }

    fun observeSettings(context: Context): Flow<AppSettings> {
        return context.dataStore.data.map { prefs -> prefs.toAppSettings() }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val legacyPreferUsb = this[KEY_PREFER_USB_MIC] ?: false
        val legacySpeaker = this[KEY_COMMUNICATION_SPEAKER] ?: false
        var classicVadEnabled = this[KEY_CLASSIC_VAD_ENABLED] ?: false
        var sileroVadEnabled = this[KEY_SILERO_VAD_ENABLED] ?: true
        val legacySpeechEnhancementEnabled = this[KEY_SPEECH_ENHANCEMENT_ENABLED] ?: false
        val speechEnhancementMode = if (contains(KEY_SPEECH_ENHANCEMENT_MODE)) {
            SpeechEnhancementMode.clamp(this[KEY_SPEECH_ENHANCEMENT_MODE] ?: SpeechEnhancementMode.OFF)
        } else if (legacySpeechEnhancementEnabled) {
            SpeechEnhancementMode.GTCRN_OFFLINE
        } else {
            SpeechEnhancementMode.GTCRN_STREAMING
        }
        if (!classicVadEnabled && !sileroVadEnabled) {
            classicVadEnabled = false
            sileroVadEnabled = true
        }
        val speakerTolerance = SpeakerVerificationTolerance.fromIndex(
            this[KEY_SPEAKER_VERIFY_TOLERANCE_LEVEL]
                ?: SpeakerVerificationTolerance.SMART.index
        )
        val speechButtonActionMode = SpeechButtonActionMode.normalize(
            this[KEY_SPEECH_BUTTON_ACTION_MODE] ?: SpeechButtonActionMode.fromLegacy(
                pushToTalkEnabled = this[KEY_PUSH_TO_TALK_MODE] ?: false,
                confirmEnabled = this[KEY_PUSH_TO_TALK_CONFIRM_INPUT] ?: false
            )
        )
        return AppSettings(
            muteWhilePlaying = this[KEY_MUTE_WHILE_PLAYING] ?: true,
            muteWhilePlayingDelaySec = this[KEY_MUTE_DELAY_SEC] ?: 0.2f,
            echoSuppression = this[KEY_ECHO_SUPPRESSION] ?: false,
            communicationMode = this[KEY_COMMUNICATION_MODE] ?: false,
            preferredInputType = this[KEY_PREFERRED_INPUT_TYPE]
                ?: if (legacyPreferUsb) AudioRoutePreference.INPUT_USB else AudioRoutePreference.INPUT_AUTO,
            preferredOutputType = this[KEY_PREFERRED_OUTPUT_TYPE]
                ?: if (legacySpeaker) AudioRoutePreference.OUTPUT_SPEAKER else AudioRoutePreference.OUTPUT_AUTO,
            aec3Enabled = this[KEY_AEC3_ENABLED] ?: true,
            denoiserMode = (this[KEY_DENOISER_MODE] ?: AudioDenoiserMode.OFF)
                .coerceIn(AudioDenoiserMode.OFF, AudioDenoiserMode.SPEEX)
                .takeUnless { SpeechEnhancementMode.isEnabled(speechEnhancementMode) }
                ?: AudioDenoiserMode.OFF,
            speechEnhancementMode = speechEnhancementMode,
            classicVadEnabled = classicVadEnabled,
            sileroVadEnabled = sileroVadEnabled,
            sileroVadThreshold = (this[KEY_SILERO_VAD_THRESHOLD] ?: SILERO_VAD_DEFAULT_THRESHOLD)
                .coerceIn(SILERO_VAD_MIN_THRESHOLD, SILERO_VAD_MAX_THRESHOLD),
            sileroVadPreRollMs = (this[KEY_SILERO_VAD_PRE_ROLL_MS] ?: SILERO_VAD_DEFAULT_PRE_ROLL_MS)
                .let { stored ->
                    if (stored == SILERO_VAD_PREVIOUS_DEFAULT_PRE_ROLL_MS) {
                        SILERO_VAD_DEFAULT_PRE_ROLL_MS
                    } else {
                        stored
                    }
                }
                .coerceIn(SILERO_VAD_MIN_PRE_ROLL_MS, SILERO_VAD_MAX_PRE_ROLL_MS),
            recognitionResourceModelScopeUrl = normalizeRecognitionResourceModelScopeUrl(
                this[KEY_RECOGNITION_RESOURCE_MODELSCOPE_URL]
            ),
            recognitionResourceHuggingFaceUrl = normalizeRecognitionResourceHuggingFaceUrl(
                this[KEY_RECOGNITION_RESOURCE_HUGGINGFACE_URL]
            ),
            recognitionResourcePreferredSource = (this[KEY_RECOGNITION_RESOURCE_PREFERRED_SOURCE]
                ?: RECOGNITION_RESOURCE_SOURCE_MODELSCOPE)
                .coerceIn(RECOGNITION_RESOURCE_SOURCE_MODELSCOPE, RECOGNITION_RESOURCE_SOURCE_HUGGINGFACE),
            kokoroHfUrl = this[KEY_KOKORO_HF_URL]?.takeIf { it.isNotBlank() && "kokoro-int8" !in it }
                ?: DEFAULT_KOKORO_HF_URL,
            kokoroHfMirrorUrl = this[KEY_KOKORO_HFMIRROR_URL]?.takeIf { it.isNotBlank() && "kokoro-int8" !in it }
                ?: DEFAULT_KOKORO_HFMIRROR_URL,
            kokoroModelScopeUrl = this[KEY_KOKORO_MODELSCOPE_URL]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_KOKORO_MODELSCOPE_URL,
            kokoroPreferredSource = (this[KEY_KOKORO_PREFERRED_SOURCE] ?: KOKORO_SOURCE_MODELSCOPE)
                .coerceIn(KOKORO_SOURCE_HF, KOKORO_SOURCE_MODELSCOPE),
            kokoroSpeakerId = (this[KEY_KOKORO_SPEAKER_ID] ?: KOKORO_DEFAULT_SPEAKER_ID)
                .coerceIn(KOKORO_MIN_SPEAKER_ID, KOKORO_MAX_SPEAKER_ID),
            minVolumePercent = this[KEY_MIN_VOLUME_PERCENT] ?: 2,
            playbackGainPercent = (this[KEY_PLAYBACK_GAIN_PERCENT] ?: 100).coerceIn(0, 1000),
            audioFocusAvoidanceMode = normalizeAudioFocusAvoidanceMode(
                this[KEY_AUDIO_FOCUS_AVOIDANCE_MODE] ?: AUDIO_FOCUS_AVOID_NONE
            ),
            piperNoiseScale = (this[KEY_PIPER_NOISE_SCALE] ?: 0.667f).coerceIn(0f, 2f),
            piperLengthScale = (this[KEY_PIPER_LENGTH_SCALE] ?: 1.0f).coerceIn(0.1f, 5f),
            piperNoiseW = (this[KEY_PIPER_NOISE_W] ?: 0.8f).coerceIn(0.3f, 1.5f),
            piperSentenceSilence = (this[KEY_PIPER_SENTENCE_SILENCE] ?: 0.2f).coerceIn(0f, 2f),
            keepAlive = true,
            asrRecognitionLanguage = AsrRecognitionLanguage.normalize(
                this[KEY_ASR_RECOGNITION_LANGUAGE]
            ),
            landscapeDrawerMode = (this[KEY_LANDSCAPE_DRAWER_MODE] ?: DRAWER_MODE_PERMANENT)
                .coerceIn(DRAWER_MODE_HIDDEN, DRAWER_MODE_PERMANENT),
            solidTopBar = this[KEY_SOLID_TOP_BAR] ?: false,
            themeMode = normalizeThemeMode(this[KEY_THEME_MODE] ?: THEME_MODE_FOLLOW_SYSTEM),
            overlayThemeMode = normalizeThemeMode(this[KEY_OVERLAY_THEME_MODE] ?: THEME_MODE_FOLLOW_SYSTEM),
            themeColorArgb = normalizeThemeColorArgb(this[KEY_THEME_COLOR_ARGB] ?: DEFAULT_THEME_COLOR_ARGB),
            themeToneCorrectionEnabled = this[KEY_THEME_TONE_CORRECTION_ENABLED] ?: false,
            appFontId = normalizeAppFontId(this[KEY_APP_FONT_ID] ?: AppFontDefaults.SystemFontId),
            appFontWeight = normalizeAppFontWeight(
                this[KEY_APP_FONT_WEIGHT] ?: AppFontDefaults.DefaultWeight
            ),
            appFontModelScopeUrl = AppFontRemoteSource.ModelScope.resolvedRepositoryBaseUrl(
                this[KEY_APP_FONT_MODELSCOPE_URL].orEmpty()
            ),
            appFontHuggingFaceUrl = AppFontRemoteSource.HuggingFace.resolvedRepositoryBaseUrl(
                this[KEY_APP_FONT_HUGGINGFACE_URL].orEmpty()
            ),
            appFontPreferredSource = AppFontRemoteSource.fromPreferenceValue(
                this[KEY_APP_FONT_PREFERRED_SOURCE] ?: APP_FONT_SOURCE_MODELSCOPE
            ).preferenceValue,
            floatingOverlayUseSystemFont = this[KEY_FLOATING_OVERLAY_USE_SYSTEM_FONT] ?: false,
            useSystemTextToolbar = this[KEY_USE_SYSTEM_TEXT_TOOLBAR] ?: false,
            fontScaleBlockMode = normalizeFontScaleBlockMode(
                this[KEY_FONT_SCALE_BLOCK_MODE] ?: FONT_SCALE_BLOCK_ICONS_ONLY
            ),
            hapticFeedbackEnabled = this[KEY_HAPTIC_FEEDBACK_ENABLED] ?: true,
            onboardingCompleted = this[KEY_ONBOARDING_COMPLETED] ?: false,
            forceFullWidthTabsOnPhone = this[KEY_FORCE_FULL_WIDTH_TABS_ON_PHONE] ?: false,
            soundboardGridFullWidth = this[KEY_SOUNDBOARD_GRID_FULL_WIDTH] ?: false,
            internalWebViewEnabled = true,
            drawingSaveRelativePath = (this[KEY_DRAWING_SAVE_RELATIVE_PATH]
                ?: DEFAULT_DRAWING_SAVE_RELATIVE_PATH).ifBlank { DEFAULT_DRAWING_SAVE_RELATIVE_PATH },
            quickCardAutoSaveOnExit = this[KEY_QUICK_CARD_AUTO_SAVE_ON_EXIT] ?: false,
            useBuiltinFileManager = this[KEY_USE_BUILTIN_FILE_MANAGER] ?: false,
            useBuiltinGallery = this[KEY_USE_BUILTIN_GALLERY] ?: false,
            asrSendToQuickSubtitle = this[KEY_ASR_SEND_TO_QUICK_SUBTITLE] ?: true,
            speechButtonActionMode = speechButtonActionMode,
            pushToTalkMode = SpeechButtonActionMode.usesPushToTalk(speechButtonActionMode),
            pushToTalkConfirmInput = SpeechButtonActionMode.usesConfirmation(speechButtonActionMode),
            floatingOverlayEnabled = this[KEY_FLOATING_OVERLAY_ENABLED] ?: false,
            floatingOverlayAutoDock = this[KEY_FLOATING_OVERLAY_AUTO_DOCK] ?: true,
            floatingOverlayShowOnLockScreen = this[KEY_FLOATING_OVERLAY_SHOW_ON_LOCK_SCREEN] ?: false,
            lockScreenBackgroundPermissionGuideShown =
                this[KEY_LOCK_SCREEN_BACKGROUND_PERMISSION_GUIDE_SHOWN] ?: false,
            lockScreenSettings = decodeLockScreenSettings(this[KEY_LOCK_SCREEN_SETTINGS]),
            floatingOverlayFabPrefersKeyboard =
                this[KEY_FLOATING_OVERLAY_FAB_PREFERS_KEYBOARD] ?: false,
            floatingOverlayFabInputGuideShown =
                this[KEY_FLOATING_OVERLAY_FAB_INPUT_GUIDE_SHOWN] ?: false,
            floatingOverlayHardcodedShortcutSupplement =
                this[KEY_FLOATING_OVERLAY_HARDCODED_SHORTCUT_SUPPLEMENT] ?: false,
            quickTextGestureSettings = decodeQuickTextGestureSettings(
                this[KEY_QUICK_TEXT_GESTURE_SETTINGS]
            ),
            volumeHotkeyUpDownEnabled = this[KEY_VOLUME_HOTKEY_UP_DOWN_ENABLED] ?: false,
            volumeHotkeyDownUpEnabled = this[KEY_VOLUME_HOTKEY_DOWN_UP_ENABLED] ?: false,
            volumeHotkeyWindowMs = (this[KEY_VOLUME_HOTKEY_WINDOW_MS] ?: VOLUME_HOTKEY_DEFAULT_WINDOW_MS)
                .coerceIn(VOLUME_HOTKEY_MIN_WINDOW_MS, VOLUME_HOTKEY_MAX_WINDOW_MS),
            volumeHotkeyAccessibilityEnabled =
                this[KEY_VOLUME_HOTKEY_ACCESSIBILITY_ENABLED] ?: false,
            volumeHotkeyEnableWarningDismissed =
                this[KEY_VOLUME_HOTKEY_ENABLE_WARNING_DISMISSED] ?: false,
            volumeHotkeyUpDownAction = VolumeHotkeyActions.decode(
                this[KEY_VOLUME_HOTKEY_UP_DOWN_ACTION],
                fallback = VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.UpDown)
            ),
            volumeHotkeyDownUpAction = VolumeHotkeyActions.decode(
                this[KEY_VOLUME_HOTKEY_DOWN_UP_ACTION],
                fallback = VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.DownUp)
            ),
            ttsDisabled = this[KEY_TTS_DISABLED] ?: false,
            soundboardKeywordTriggerEnabled = this[KEY_SOUNDBOARD_KEYWORD_TRIGGER_ENABLED] ?: false,
            soundboardSuppressTtsOnKeyword = this[KEY_SOUNDBOARD_SUPPRESS_TTS_ON_KEYWORD] ?: false,
            allowQuickTextTriggerSoundboard = this[KEY_ALLOW_QUICK_TEXT_TRIGGER_SOUNDBOARD] ?: false,
            quickSubtitleInterruptQueue = this[KEY_QUICK_SUBTITLE_INTERRUPT_QUEUE] ?: true,
            quickSubtitleAutoFit = this[KEY_QUICK_SUBTITLE_AUTO_FIT] ?: true,
            quickSubtitleAllowLargeFont = this[KEY_QUICK_SUBTITLE_ALLOW_LARGE_FONT] ?: false,
            quickSubtitleCompactControls = this[KEY_QUICK_SUBTITLE_COMPACT_CONTROLS] ?: false,
            quickSubtitleFrequencySortEnabled =
                this[KEY_QUICK_SUBTITLE_FREQUENCY_SORT_ENABLED] ?: false,
            quickSubtitleUsageStats =
                QuickSubtitleUsageStats.fromJson(this[KEY_QUICK_SUBTITLE_USAGE_STATS]),
            quickSubtitlePanelGesturesEnabled =
                this[KEY_QUICK_SUBTITLE_PANEL_GESTURES_ENABLED] ?: true,
            quickSubtitlePanelGesturesReversed =
                this[KEY_QUICK_SUBTITLE_PANEL_GESTURES_REVERSED] ?: false,
            quickSubtitleFirstRunGuideCompleted = resolveQuickSubtitleFirstRunGuideCompleted(
                stored = this[KEY_QUICK_SUBTITLE_FIRST_RUN_GUIDE_COMPLETED],
                onboardingCompleted = this[KEY_ONBOARDING_COMPLETED] ?: false
            ),
            quickSubtitleListPopupGridMode = this[KEY_QUICK_SUBTITLE_LIST_POPUP_GRID_MODE] ?: true,
            quickSubtitleKeepInputPreview = this[KEY_QUICK_SUBTITLE_KEEP_INPUT_PREVIEW] ?: true,
            quickSubtitleClearedPlaceholderText = normalizeQuickSubtitleClearedPlaceholder(
                this[KEY_QUICK_SUBTITLE_CLEARED_PLACEHOLDER]
            ),
            quickSubtitleRestoreLastTextOnLaunch =
                this[KEY_QUICK_SUBTITLE_RESTORE_LAST_TEXT_ON_LAUNCH] ?: false,
            listeningModeSettings = ListeningModeSettings.fromJson(
                this[KEY_LISTENING_MODE_SETTINGS]
            ),
            ledSubtitleSettings = decodeLedSubtitleSettings(this[KEY_LED_SUBTITLE_SETTINGS]),
            lanCastDisplaySettings = decodeLedSubtitleSettings(
                this[KEY_LAN_CAST_DISPLAY_SETTINGS],
                defaultLanCastDisplaySettings()
            ),
            bluetoothMediaTitleSubtitle = this[KEY_BLUETOOTH_MEDIA_TITLE_SUBTITLE] ?: false,
            liveSubtitleNotificationEnabled = this[KEY_LIVE_SUBTITLE_NOTIFICATION_ENABLED] ?: false,
            lanCastAudioOutputMode = normalizeLanCastAudioOutputMode(
                this[KEY_LAN_CAST_AUDIO_OUTPUT_MODE] ?: LAN_CAST_AUDIO_LOCAL
            ),
            lanCastBackgroundReminderDismissed =
                this[KEY_LAN_CAST_BACKGROUND_REMINDER_DISMISSED] ?: false,
            drawingKeepCanvasOrientationToDevice = this[KEY_DRAWING_KEEP_CANVAS_ORIENTATION_TO_DEVICE] ?: true,
            drawingPalette = decodeDrawingPalette(this[KEY_DRAWING_PALETTE]),
            speakerVerifyEnabled = this[KEY_SPEAKER_VERIFY_ENABLED] ?: false,
            speakerVerifyThreshold = speakerTolerance.primaryThreshold,
            speakerVerifyToleranceLevel = speakerTolerance.index,
            recognitionModuleMode = RECOGNITION_MODULE_MODE_EXPERIMENTAL,
            experimentalRecognitionSensitivity = 50,
            experimentalTargetSpeakerBackend = EXPERIMENTAL_TARGET_SPEAKER_BACKEND_AUTO,
            speakerVerifyProfileCsv = this[KEY_SPEAKER_VERIFY_PROFILE] ?: "",
            speakerVerifyBackendVersion = this[KEY_SPEAKER_VERIFY_BACKEND_VERSION] ?: 0,
            allowSystemAecWithAec3 = true
        )
    }

    suspend fun setMuteWhilePlaying(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MUTE_WHILE_PLAYING] = enabled
        }
    }

    suspend fun setMuteWhilePlayingDelaySec(context: Context, seconds: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MUTE_DELAY_SEC] = seconds
        }
    }

    suspend fun setEchoSuppression(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ECHO_SUPPRESSION] = enabled
        }
    }

    suspend fun setCommunicationMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COMMUNICATION_MODE] = enabled
        }
    }

    suspend fun setCommunicationSpeaker(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COMMUNICATION_SPEAKER] = enabled
        }
    }

    suspend fun setPreferredInputType(context: Context, type: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFERRED_INPUT_TYPE] = type
        }
    }

    suspend fun setPreferredOutputType(context: Context, type: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFERRED_OUTPUT_TYPE] = type
        }
    }

    suspend fun setAec3Enabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AEC3_ENABLED] = enabled
        }
    }

    suspend fun setDenoiserMode(context: Context, mode: Int) {
        val normalized = mode.coerceIn(AudioDenoiserMode.OFF, AudioDenoiserMode.SPEEX)
        context.dataStore.edit { prefs ->
            prefs[KEY_DENOISER_MODE] = normalized
            if (normalized != AudioDenoiserMode.OFF) {
                prefs[KEY_SPEECH_ENHANCEMENT_MODE] = SpeechEnhancementMode.OFF
                prefs[KEY_SPEECH_ENHANCEMENT_ENABLED] = false
            }
        }
    }

    suspend fun setSpeechEnhancementMode(context: Context, mode: Int) {
        val normalized = SpeechEnhancementMode.clamp(mode)
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEECH_ENHANCEMENT_MODE] = normalized
            prefs[KEY_SPEECH_ENHANCEMENT_ENABLED] = SpeechEnhancementMode.isEnabled(normalized)
            if (SpeechEnhancementMode.isEnabled(normalized)) {
                prefs[KEY_DENOISER_MODE] = AudioDenoiserMode.OFF
            }
        }
    }

    suspend fun resetMicrophoneSettings(context: Context) {
        val defaults = AppSettings()
        context.dataStore.edit { prefs ->
            prefs[KEY_ECHO_SUPPRESSION] = defaults.echoSuppression
            prefs[KEY_COMMUNICATION_MODE] = defaults.communicationMode
            prefs[KEY_PREFERRED_INPUT_TYPE] = defaults.preferredInputType
            prefs[KEY_AEC3_ENABLED] = defaults.aec3Enabled
            prefs[KEY_DENOISER_MODE] = defaults.denoiserMode
            prefs[KEY_SPEECH_ENHANCEMENT_MODE] = defaults.speechEnhancementMode
            prefs[KEY_SPEECH_ENHANCEMENT_ENABLED] =
                SpeechEnhancementMode.isEnabled(defaults.speechEnhancementMode)
        }
    }

    suspend fun setClassicVadEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLASSIC_VAD_ENABLED] = enabled
        }
    }

    suspend fun setSileroVadEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SILERO_VAD_ENABLED] = enabled
        }
    }

    suspend fun setVadFlags(context: Context, classicEnabled: Boolean, sileroEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLASSIC_VAD_ENABLED] = classicEnabled
            prefs[KEY_SILERO_VAD_ENABLED] = sileroEnabled
        }
    }

    suspend fun setSileroVadThreshold(context: Context, threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SILERO_VAD_THRESHOLD] = threshold.coerceIn(
                SILERO_VAD_MIN_THRESHOLD,
                SILERO_VAD_MAX_THRESHOLD
            )
        }
    }

    suspend fun setSileroVadPreRollMs(context: Context, preRollMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SILERO_VAD_PRE_ROLL_MS] = preRollMs.coerceIn(
                SILERO_VAD_MIN_PRE_ROLL_MS,
                SILERO_VAD_MAX_PRE_ROLL_MS
            )
        }
    }

    suspend fun setRecognitionResourceSources(
        context: Context,
        modelScopeUrl: String,
        huggingFaceUrl: String,
        preferredSource: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECOGNITION_RESOURCE_MODELSCOPE_URL] = modelScopeUrl.trim()
            prefs[KEY_RECOGNITION_RESOURCE_HUGGINGFACE_URL] = huggingFaceUrl.trim()
            prefs[KEY_RECOGNITION_RESOURCE_PREFERRED_SOURCE] = preferredSource.coerceIn(
                RECOGNITION_RESOURCE_SOURCE_MODELSCOPE,
                RECOGNITION_RESOURCE_SOURCE_HUGGINGFACE
            )
        }
    }

    suspend fun setKokoroSources(
        context: Context,
        hfUrl: String,
        hfMirrorUrl: String,
        modelScopeUrl: String,
        preferredSource: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KOKORO_HF_URL] = hfUrl.trim()
            prefs[KEY_KOKORO_HFMIRROR_URL] = hfMirrorUrl.trim()
            prefs[KEY_KOKORO_MODELSCOPE_URL] = modelScopeUrl.trim()
            prefs[KEY_KOKORO_PREFERRED_SOURCE] = preferredSource.coerceIn(
                KOKORO_SOURCE_HF,
                KOKORO_SOURCE_MODELSCOPE
            )
        }
    }

    suspend fun setKokoroSpeakerId(context: Context, speakerId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KOKORO_SPEAKER_ID] = speakerId.coerceIn(KOKORO_MIN_SPEAKER_ID, KOKORO_MAX_SPEAKER_ID)
        }
    }

    suspend fun getKokoroVoiceOrder(context: Context): Long? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_KOKORO_VOICE_ORDER]
    }

    suspend fun setKokoroVoiceOrder(context: Context, order: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KOKORO_VOICE_ORDER] = order
        }
    }

    suspend fun getKokoroVoicePinned(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_KOKORO_VOICE_PINNED] ?: false
    }

    suspend fun setKokoroVoicePinned(context: Context, pinned: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KOKORO_VOICE_PINNED] = pinned
        }
    }

    suspend fun setMinVolumePercent(context: Context, percent: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIN_VOLUME_PERCENT] = percent
        }
    }

    suspend fun setAudioFocusAvoidanceMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_FOCUS_AVOIDANCE_MODE] = normalizeAudioFocusAvoidanceMode(mode)
        }
    }

    suspend fun setPlaybackGainPercent(context: Context, percent: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYBACK_GAIN_PERCENT] = percent.coerceIn(0, 1000)
        }
    }

    suspend fun setPiperNoiseScale(context: Context, value: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIPER_NOISE_SCALE] = value.coerceIn(0f, 2f)
        }
    }

    suspend fun setPiperLengthScale(context: Context, value: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIPER_LENGTH_SCALE] = value.coerceIn(0.1f, 5f)
        }
    }

    suspend fun setPiperNoiseW(context: Context, value: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIPER_NOISE_W] = value.coerceIn(0f, 2f)
        }
    }

    suspend fun setPiperSentenceSilence(context: Context, value: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIPER_SENTENCE_SILENCE] = value.coerceIn(0f, 2f)
        }
    }

    suspend fun resetTtsSettings(context: Context) {
        val defaults = AppSettings()
        context.dataStore.edit { prefs ->
            prefs[KEY_TTS_DISABLED] = defaults.ttsDisabled
            prefs[KEY_PLAYBACK_GAIN_PERCENT] = defaults.playbackGainPercent
            prefs[KEY_AUDIO_FOCUS_AVOIDANCE_MODE] = defaults.audioFocusAvoidanceMode
            prefs[KEY_PIPER_NOISE_SCALE] = defaults.piperNoiseScale
            prefs[KEY_PIPER_LENGTH_SCALE] = defaults.piperLengthScale
            prefs[KEY_PIPER_NOISE_W] = defaults.piperNoiseW
            prefs[KEY_PIPER_SENTENCE_SILENCE] = defaults.piperSentenceSilence
        }
    }

    suspend fun setKeepAlive(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KEEP_ALIVE] = enabled
        }
    }

    suspend fun setAsrRecognitionLanguage(context: Context, language: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ASR_RECOGNITION_LANGUAGE] = AsrRecognitionLanguage.normalize(language)
        }
    }

    suspend fun setLandscapeDrawerMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANDSCAPE_DRAWER_MODE] =
                mode.coerceIn(DRAWER_MODE_HIDDEN, DRAWER_MODE_PERMANENT)
        }
    }

    suspend fun setSolidTopBar(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOLID_TOP_BAR] = enabled
        }
    }

    suspend fun setThemeMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = normalizeThemeMode(mode)
        }
    }

    suspend fun setOverlayThemeMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_THEME_MODE] = normalizeThemeMode(mode)
        }
    }

    suspend fun setThemeColorArgb(context: Context, colorArgb: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_COLOR_ARGB] = normalizeThemeColorArgb(colorArgb)
        }
    }

    suspend fun setThemeToneCorrectionEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_TONE_CORRECTION_ENABLED] = enabled
        }
    }

    suspend fun setAppFont(context: Context, id: String, weight: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_FONT_ID] = normalizeAppFontId(id)
            prefs[KEY_APP_FONT_WEIGHT] = normalizeAppFontWeight(weight)
        }
    }

    suspend fun setAppFontDownloadSources(
        context: Context,
        modelScopeUrl: String,
        huggingFaceUrl: String,
        preferredSource: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_FONT_MODELSCOPE_URL] =
                AppFontRemoteSource.ModelScope.resolvedRepositoryBaseUrl(modelScopeUrl)
            prefs[KEY_APP_FONT_HUGGINGFACE_URL] =
                AppFontRemoteSource.HuggingFace.resolvedRepositoryBaseUrl(huggingFaceUrl)
            prefs[KEY_APP_FONT_PREFERRED_SOURCE] =
                AppFontRemoteSource.fromPreferenceValue(preferredSource).preferenceValue
        }
    }

    suspend fun setFloatingOverlayUseSystemFont(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_USE_SYSTEM_FONT] = enabled
        }
    }

    suspend fun setUseSystemTextToolbar(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_SYSTEM_TEXT_TOOLBAR] = enabled
        }
    }

    suspend fun setFontScaleBlockMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE_BLOCK_MODE] = normalizeFontScaleBlockMode(mode)
        }
    }

    suspend fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
            if (completed && prefs[KEY_QUICK_SUBTITLE_FIRST_RUN_GUIDE_COMPLETED] == null) {
                prefs[KEY_QUICK_SUBTITLE_FIRST_RUN_GUIDE_COMPLETED] = false
            }
        }
    }

    suspend fun isOnboardingQuickSubtitlePresetsInstalled(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_ONBOARDING_QUICK_SUBTITLE_PRESETS_INSTALLED] ?: false
    }

    suspend fun setOnboardingQuickSubtitlePresetsInstalled(context: Context, installed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_QUICK_SUBTITLE_PRESETS_INSTALLED] = installed
        }
    }

    suspend fun setForceFullWidthTabsOnPhone(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FORCE_FULL_WIDTH_TABS_ON_PHONE] = enabled
        }
    }

    suspend fun setSoundboardGridFullWidth(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUNDBOARD_GRID_FULL_WIDTH] = enabled
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun setInternalWebViewEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INTERNAL_WEBVIEW_ENABLED] = true
        }
    }

    suspend fun setDrawingSaveRelativePath(context: Context, path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRAWING_SAVE_RELATIVE_PATH] =
                path.ifBlank { DEFAULT_DRAWING_SAVE_RELATIVE_PATH }
        }
    }

    suspend fun setQuickCardAutoSaveOnExit(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_CARD_AUTO_SAVE_ON_EXIT] = enabled
        }
    }

    suspend fun setUseBuiltinFileManager(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_BUILTIN_FILE_MANAGER] = enabled
        }
    }

    suspend fun setUseBuiltinGallery(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_BUILTIN_GALLERY] = enabled
        }
    }

    suspend fun setAsrSendToQuickSubtitle(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ASR_SEND_TO_QUICK_SUBTITLE] = enabled
        }
    }

    suspend fun setPushToTalkMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PUSH_TO_TALK_MODE] = enabled
            val mode = SpeechButtonActionMode.fromLegacy(
                pushToTalkEnabled = enabled,
                confirmEnabled = prefs[KEY_PUSH_TO_TALK_CONFIRM_INPUT] ?: false
            )
            prefs[KEY_SPEECH_BUTTON_ACTION_MODE] = mode
        }
    }

    suspend fun setPushToTalkConfirmInput(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PUSH_TO_TALK_CONFIRM_INPUT] = enabled
            val mode = SpeechButtonActionMode.fromLegacy(
                pushToTalkEnabled = prefs[KEY_PUSH_TO_TALK_MODE] ?: false,
                confirmEnabled = enabled
            )
            prefs[KEY_SPEECH_BUTTON_ACTION_MODE] = mode
        }
    }

    suspend fun setSpeechButtonActionMode(context: Context, value: Int) {
        val mode = SpeechButtonActionMode.normalize(value)
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEECH_BUTTON_ACTION_MODE] = mode
            prefs[KEY_PUSH_TO_TALK_MODE] = SpeechButtonActionMode.usesPushToTalk(mode)
            prefs[KEY_PUSH_TO_TALK_CONFIRM_INPUT] = SpeechButtonActionMode.usesConfirmation(mode)
        }
    }

    suspend fun setFloatingOverlayEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setFloatingOverlayAutoDock(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_AUTO_DOCK] = enabled
        }
    }

    suspend fun setFloatingOverlayShowOnLockScreen(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_SHOW_ON_LOCK_SCREEN] = enabled
        }
    }

    suspend fun setLockScreenBackgroundPermissionGuideShown(context: Context, shown: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_SCREEN_BACKGROUND_PERMISSION_GUIDE_SHOWN] = shown
        }
    }

    suspend fun setLockScreenSettings(context: Context, settings: LockScreenSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_SCREEN_SETTINGS] = encodeLockScreenSettings(settings)
        }
    }

    suspend fun setFloatingOverlayFabPrefersKeyboard(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_FAB_PREFERS_KEYBOARD] = enabled
        }
    }

    suspend fun setFloatingOverlayFabInputGuideShown(context: Context, shown: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_FAB_INPUT_GUIDE_SHOWN] = shown
        }
    }

    suspend fun setFloatingOverlayFabModeChoice(context: Context, keyboardFirst: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_FAB_PREFERS_KEYBOARD] = keyboardFirst
            prefs[KEY_FLOATING_OVERLAY_FAB_INPUT_GUIDE_SHOWN] = true
        }
    }

    suspend fun setFloatingOverlayHardcodedShortcutSupplement(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_HARDCODED_SHORTCUT_SUPPLEMENT] = enabled
        }
    }

    suspend fun setQuickTextGestureSettings(
        context: Context,
        settings: QuickTextGestureSettings
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_TEXT_GESTURE_SETTINGS] = encodeQuickTextGestureSettings(settings)
        }
    }

    suspend fun setVolumeHotkeyEnabled(
        context: Context,
        sequence: VolumeHotkeySequence,
        enabled: Boolean
    ) {
        context.dataStore.edit { prefs ->
            when (sequence) {
                VolumeHotkeySequence.UpDown -> prefs[KEY_VOLUME_HOTKEY_UP_DOWN_ENABLED] = enabled
                VolumeHotkeySequence.DownUp -> prefs[KEY_VOLUME_HOTKEY_DOWN_UP_ENABLED] = enabled
            }
        }
    }

    suspend fun setVolumeHotkeyAction(
        context: Context,
        sequence: VolumeHotkeySequence,
        action: VolumeHotkeyActionSpec
    ) {
        val payload = VolumeHotkeyActions.encode(action)
        context.dataStore.edit { prefs ->
            when (sequence) {
                VolumeHotkeySequence.UpDown -> prefs[KEY_VOLUME_HOTKEY_UP_DOWN_ACTION] = payload
                VolumeHotkeySequence.DownUp -> prefs[KEY_VOLUME_HOTKEY_DOWN_UP_ACTION] = payload
            }
        }
    }

    suspend fun setVolumeHotkeyWindowMs(context: Context, windowMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOLUME_HOTKEY_WINDOW_MS] = windowMs.coerceIn(
                VOLUME_HOTKEY_MIN_WINDOW_MS,
                VOLUME_HOTKEY_MAX_WINDOW_MS
            )
        }
    }

    suspend fun setVolumeHotkeyAccessibilityEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOLUME_HOTKEY_ACCESSIBILITY_ENABLED] = enabled
        }
    }

    suspend fun setVolumeHotkeyEnableWarningDismissed(context: Context, dismissed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOLUME_HOTKEY_ENABLE_WARNING_DISMISSED] = dismissed
        }
    }

    suspend fun setTtsDisabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TTS_DISABLED] = enabled
        }
    }

    suspend fun setSoundboardKeywordTriggerEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUNDBOARD_KEYWORD_TRIGGER_ENABLED] = enabled
        }
    }

    suspend fun setSoundboardSuppressTtsOnKeyword(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUNDBOARD_SUPPRESS_TTS_ON_KEYWORD] = enabled
        }
    }

    suspend fun setAllowQuickTextTriggerSoundboard(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALLOW_QUICK_TEXT_TRIGGER_SOUNDBOARD] = enabled
        }
    }

    suspend fun setQuickSubtitleInterruptQueue(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_INTERRUPT_QUEUE] = enabled
        }
    }

    suspend fun setQuickSubtitleAutoFit(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_AUTO_FIT] = enabled
        }
    }

    suspend fun setQuickSubtitleAllowLargeFont(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_ALLOW_LARGE_FONT] = enabled
        }
    }

    suspend fun setQuickSubtitleCompactControls(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_COMPACT_CONTROLS] = enabled
        }
    }

    suspend fun setQuickSubtitleFrequencySortEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_FREQUENCY_SORT_ENABLED] = enabled
        }
    }

    suspend fun recordQuickSubtitleUsage(context: Context, groupId: Long, text: String) {
        context.dataStore.edit { prefs ->
            val next = QuickSubtitleUsageStats
                .fromJson(prefs[KEY_QUICK_SUBTITLE_USAGE_STATS])
                .increment(groupId, text)
            prefs[KEY_QUICK_SUBTITLE_USAGE_STATS] = next.toJson()
        }
    }

    suspend fun setQuickSubtitlePanelGesturesEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_PANEL_GESTURES_ENABLED] = enabled
        }
    }

    suspend fun setQuickSubtitlePanelGesturesReversed(context: Context, reversed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_PANEL_GESTURES_REVERSED] = reversed
        }
    }

    suspend fun setQuickSubtitleFirstRunGuideCompleted(context: Context, completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_FIRST_RUN_GUIDE_COMPLETED] = completed
        }
    }

    suspend fun setQuickSubtitleListPopupGridMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_LIST_POPUP_GRID_MODE] = enabled
        }
    }

    suspend fun setQuickSubtitleKeepInputPreview(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_KEEP_INPUT_PREVIEW] = enabled
        }
    }

    suspend fun setQuickSubtitleClearedPlaceholderText(context: Context, text: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_CLEARED_PLACEHOLDER] =
                normalizeQuickSubtitleClearedPlaceholder(text)
        }
    }

    suspend fun setQuickSubtitleRestoreLastTextOnLaunch(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_RESTORE_LAST_TEXT_ON_LAUNCH] = enabled
        }
    }

    suspend fun setListeningModeSettings(
        context: Context,
        settings: ListeningModeSettings
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LISTENING_MODE_SETTINGS] = settings.normalized().toJson()
        }
    }

    suspend fun setLedSubtitleSettings(context: Context, settings: LedSubtitleSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LED_SUBTITLE_SETTINGS] = encodeLedSubtitleSettings(settings)
        }
    }

    suspend fun setLanCastDisplaySettings(context: Context, settings: LedSubtitleSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAN_CAST_DISPLAY_SETTINGS] = encodeLedSubtitleSettings(settings)
        }
    }

    suspend fun setBluetoothMediaTitleSubtitle(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_MEDIA_TITLE_SUBTITLE] = enabled
        }
    }

    suspend fun setLiveSubtitleNotificationEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_SUBTITLE_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setLanCastAudioOutputMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAN_CAST_AUDIO_OUTPUT_MODE] = normalizeLanCastAudioOutputMode(mode)
        }
    }

    suspend fun setLanCastBackgroundReminderDismissed(
        context: Context,
        dismissed: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAN_CAST_BACKGROUND_REMINDER_DISMISSED] = dismissed
        }
    }

    suspend fun setDrawingKeepCanvasOrientationToDevice(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRAWING_KEEP_CANVAS_ORIENTATION_TO_DEVICE] = enabled
        }
    }

    suspend fun setDrawingPalette(context: Context, palette: DrawingPalette) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRAWING_PALETTE] = encodeDrawingPalette(palette)
        }
    }

    suspend fun setSpeakerVerifyEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEAKER_VERIFY_ENABLED] = enabled
        }
    }

    suspend fun setSpeakerVerifyThreshold(context: Context, threshold: Float) {
        val tolerance = SpeakerVerificationTolerance.fromThreshold(threshold)
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEAKER_VERIFY_THRESHOLD] = tolerance.primaryThreshold
            prefs[KEY_SPEAKER_VERIFY_TOLERANCE_LEVEL] = tolerance.index
        }
    }

    suspend fun setSpeakerVerifyTolerance(context: Context, level: Int) {
        val tolerance = SpeakerVerificationTolerance.fromIndex(level)
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEAKER_VERIFY_THRESHOLD] = tolerance.primaryThreshold
            prefs[KEY_SPEAKER_VERIFY_TOLERANCE_LEVEL] = tolerance.index
        }
    }

    suspend fun setRecognitionModuleMode(context: Context, mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECOGNITION_MODULE_MODE] = normalizeRecognitionModuleMode(mode)
        }
    }

    suspend fun setExperimentalRecognitionSensitivity(context: Context, sensitivity: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EXPERIMENTAL_RECOGNITION_SENSITIVITY] = sensitivity.coerceIn(0, 100)
        }
    }

    suspend fun setExperimentalTargetSpeakerBackend(context: Context, backend: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EXPERIMENTAL_TARGET_SPEAKER_BACKEND] =
                normalizeExperimentalTargetSpeakerBackend(backend)
        }
    }

    fun normalizeExperimentalTargetSpeakerBackend(backend: Int): Int {
        return backend.coerceIn(
            EXPERIMENTAL_TARGET_SPEAKER_BACKEND_AUTO,
            EXPERIMENTAL_TARGET_SPEAKER_BACKEND_LIGHTWEIGHT
        )
    }

    fun normalizeRecognitionModuleMode(mode: Int): Int {
        return if (mode == RECOGNITION_MODULE_MODE_EXPERIMENTAL) {
            RECOGNITION_MODULE_MODE_EXPERIMENTAL
        } else {
            RECOGNITION_MODULE_MODE_LEGACY
        }
    }

    suspend fun setSpeakerVerifyProfile(context: Context, vector: FloatArray?) {
        setSpeakerVerifyProfiles(
            context,
            if (vector == null || vector.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    SpeakerVerifyProfile(
                        id = "legacy-1",
                        name = "说话人 1",
                        vector = vector
                    )
                )
            }
        )
    }

    suspend fun setSpeakerVerifyProfiles(context: Context, profiles: List<SpeakerVerifyProfile>) {
        context.dataStore.edit { prefs ->
            val payload = serializeSpeakerVerifyProfiles(profiles)
            prefs[KEY_SPEAKER_VERIFY_PROFILE] = payload
            prefs[KEY_SPEAKER_VERIFY_BACKEND_VERSION] = SPEAKER_VERIFY_BACKEND_SHERPA_V1
        }
    }

    suspend fun resetSpeakerVerifyBackend(context: Context, enabled: Boolean = false) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEAKER_VERIFY_PROFILE] = ""
            prefs[KEY_SPEAKER_VERIFY_ENABLED] = enabled
            prefs[KEY_SPEAKER_VERIFY_BACKEND_VERSION] = SPEAKER_VERIFY_BACKEND_SHERPA_V1
        }
    }

    fun serializeSpeakerVerifyProfiles(profiles: List<SpeakerVerifyProfile>): String {
        if (profiles.isEmpty()) return ""
        val arr = JSONArray()
        profiles.forEach { profile ->
            if (profile.vector.isEmpty()) return@forEach
            val vectorArr = JSONArray()
            profile.vector.forEach { v -> vectorArr.put(v.toDouble()) }
            arr.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("vector", vectorArr)
                    profile.confirmationVector?.takeIf { it.isNotEmpty() }?.let { confirmation ->
                        put(
                            "confirmationVector",
                            JSONArray().apply {
                                confirmation.forEach { value -> put(value.toDouble()) }
                            }
                        )
                    }
                    profile.neuralVector?.takeIf { it.isNotEmpty() }?.let { neural ->
                        put(
                            "neuralVector",
                            JSONArray().apply { neural.forEach { value -> put(value.toDouble()) } }
                        )
                    }
                }
            )
        }
        return if (arr.length() <= 0) "" else arr.toString()
    }

    fun parseSpeakerVerifyProfiles(rawPayload: String?): List<SpeakerVerifyProfile> {
        val raw = rawPayload?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        // New format: JSON array of profiles.
        if (raw.startsWith("[")) {
            return runCatching {
                val arr = JSONArray(raw)
                val out = mutableListOf<SpeakerVerifyProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val vecArr = obj.optJSONArray("vector") ?: continue
                    val vec = FloatArray(vecArr.length())
                    var ok = true
                    for (j in 0 until vecArr.length()) {
                        val d = vecArr.optDouble(j, Double.NaN)
                        if (d.isNaN()) {
                            ok = false
                            break
                        }
                        vec[j] = d.toFloat()
                    }
                    if (!ok || vec.isEmpty()) continue
                    val id = obj.optString("id").ifBlank { "profile-${i + 1}" }
                    val name = obj.optString("name").ifBlank { "说话人 ${i + 1}" }
                    val confirmation = obj.optJSONArray("confirmationVector")?.let(::parseFloatArray)
                    val neural = obj.optJSONArray("neuralVector")?.let(::parseFloatArray)
                    out.add(
                        SpeakerVerifyProfile(
                            id = id,
                            name = name,
                            vector = vec,
                            confirmationVector = confirmation?.takeIf { it.isNotEmpty() },
                            neuralVector = neural?.takeIf { it.size == 192 }
                        )
                    )
                }
                out
            }.getOrElse { emptyList() }
        }

        // Legacy format: single CSV vector.
        val legacy = parseSpeakerVerifyProfile(raw)
        return if (legacy == null || legacy.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SpeakerVerifyProfile(
                    id = "legacy-1",
                    name = "说话人 1",
                    vector = legacy
                )
            )
        }
    }

    fun parseSpeakerVerifyProfile(csv: String?): FloatArray? {
        val raw = csv?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("[")) {
            val parsed = parseSpeakerVerifyProfiles(raw)
            return parsed.firstOrNull()?.vector
        }
        val values = raw.split(",")
            .mapNotNull { token -> token.trim().toFloatOrNull() }
            .toFloatArray()
        return if (values.isEmpty()) null else values
    }

    private fun parseFloatArray(array: JSONArray): FloatArray? {
        if (array.length() <= 0) return null
        val output = FloatArray(array.length())
        for (index in 0 until array.length()) {
            val value = array.optDouble(index, Double.NaN)
            if (!value.isFinite()) return null
            output[index] = value.toFloat()
        }
        return output
    }

    @Deprecated("Use parseSpeakerVerifyProfiles instead")
    fun parseSpeakerVerifyProfileLegacy(csv: String?): FloatArray? {
        val raw = csv?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val values = raw.split(",")
            .mapNotNull { token -> token.trim().toFloatOrNull() }
            .toFloatArray()
        return if (values.isEmpty()) null else values
    }

    @Deprecated("Use setSpeakerVerifyProfiles instead")
    suspend fun setSpeakerVerifyProfileLegacy(context: Context, vector: FloatArray?) {
        context.dataStore.edit { prefs ->
            val csv = if (vector == null || vector.isEmpty()) {
                ""
            } else {
                vector.joinToString(",")
            }
            prefs[KEY_SPEAKER_VERIFY_PROFILE] = csv
        }
    }

    suspend fun getQuickSubtitleConfig(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_QUICK_SUBTITLE_CONFIG]
    }

    suspend fun setQuickSubtitleConfig(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_SUBTITLE_CONFIG] = json
        }
    }

    suspend fun getSoundboardConfig(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_SOUNDBOARD_CONFIG]
    }

    suspend fun setSoundboardConfig(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUNDBOARD_CONFIG] = json
        }
    }

    suspend fun getFloatingOverlayShortcuts(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_FLOATING_OVERLAY_SHORTCUTS]
    }

    suspend fun setFloatingOverlayShortcuts(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_SHORTCUTS] = json
        }
    }

    suspend fun isFloatingOverlayDefaultShortcutsSeeded(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_FLOATING_OVERLAY_DEFAULT_SHORTCUTS_SEEDED] ?: false
    }

    suspend fun setFloatingOverlayDefaultShortcutsSeeded(context: Context, seeded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_DEFAULT_SHORTCUTS_SEEDED] = seeded
        }
    }

    suspend fun getFloatingOverlayLayout(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_FLOATING_OVERLAY_LAYOUT]
    }

    suspend fun setFloatingOverlayLayout(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_LAYOUT] = json
        }
    }

    suspend fun getFloatingOverlayQuickSubtitleFontSize(context: Context): Float? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_FLOATING_OVERLAY_QUICK_SUBTITLE_FONT_SIZE]
    }

    suspend fun setFloatingOverlayQuickSubtitleFontSize(context: Context, sizeSp: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_QUICK_SUBTITLE_FONT_SIZE] = sizeSp
        }
    }

    suspend fun getFloatingOverlayMiniSoundboardLayout(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_FLOATING_OVERLAY_MINI_SOUNDBOARD_LAYOUT]
    }

    suspend fun setFloatingOverlayMiniSoundboardLayout(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_OVERLAY_MINI_SOUNDBOARD_LAYOUT] = json
        }
    }

    suspend fun getQuickCardConfig(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_QUICK_CARD_CONFIG]
    }

    suspend fun setQuickCardConfig(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_CARD_CONFIG] = json
        }
    }

}
