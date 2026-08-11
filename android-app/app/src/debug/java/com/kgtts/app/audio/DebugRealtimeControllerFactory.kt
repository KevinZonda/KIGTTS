package com.lhtstudio.kigtts.app.audio

import android.content.Context
import com.lhtstudio.kigtts.app.data.UserPrefs
import kotlinx.coroutines.CoroutineScope

internal data class DebugSpeakerVectors(
    val primary: FloatArray,
    val neural: FloatArray? = null,
    val confirmation: FloatArray? = null
)

internal data class DebugRealtimeCallbacks(
    val onResult: (Long, String) -> Unit = { _, _ -> Unit },
    val onStreamingResult: (String) -> Unit = {},
    val onListeningResult: (Long, String) -> Unit = { _, _ -> Unit },
    val onListeningStreamingResult: (String) -> Unit = {},
    val onLevel: (Float) -> Unit = {},
    val onSpeakerVerify: (Float, Boolean) -> Unit = { _, _ -> Unit },
    val onStatus: (String) -> Unit = {},
    val onError: (String) -> Unit = {}
)

internal object DebugRealtimeControllerFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        settings: UserPrefs.AppSettings,
        speakerProfiles: List<DebugSpeakerVectors>,
        callbacks: DebugRealtimeCallbacks
    ): RealtimeController {
        return RealtimeController(
            context = context,
            scope = scope,
            onResult = callbacks.onResult,
            onStreamingResult = callbacks.onStreamingResult,
            onListeningResult = callbacks.onListeningResult,
            onListeningStreamingResult = callbacks.onListeningStreamingResult,
            onProgress = { _, _ -> Unit },
            onLevel = callbacks.onLevel,
            onInputDevice = { _ -> Unit },
            onOutputDevice = { _ -> Unit },
            onAec3Status = { _ -> Unit },
            onAec3Diag = { _ -> Unit },
            onSpeakerVerify = callbacks.onSpeakerVerify,
            onStatus = callbacks.onStatus,
            onError = callbacks.onError,
            initialSuppressWhilePlaying = false,
            initialUseVoiceCommunication = false,
            initialCommunicationMode = false,
            initialMinVolumePercent = settings.minVolumePercent,
            initialPlaybackGainPercent = settings.playbackGainPercent,
            initialAudioFocusAvoidanceMode = settings.audioFocusAvoidanceMode,
            initialDenoiserMode = settings.denoiserMode,
            initialSpeechEnhancementMode = settings.speechEnhancementMode,
            initialPiperNoiseScale = settings.piperNoiseScale,
            initialPiperLengthScale = settings.piperLengthScale,
            initialPiperNoiseW = settings.piperNoiseW,
            initialPiperSentenceSilenceSec = settings.piperSentenceSilence,
            initialKokoroSpeakerId = settings.kokoroSpeakerId,
            initialSuppressDelaySec = 0f,
            initialPreferredInputType = AudioRoutePreference.INPUT_AUTO,
            initialPreferredOutputType = AudioRoutePreference.OUTPUT_AUTO,
            initialUseAec3 = false,
            initialAsrRecognitionLanguage = settings.asrRecognitionLanguage,
            initialClassicVadEnabled = settings.classicVadEnabled,
            initialSileroVadEnabled = settings.sileroVadEnabled,
            initialSileroVadThreshold = settings.sileroVadThreshold,
            initialSileroVadPreRollMs = settings.sileroVadPreRollMs,
            initialAllowSystemAecWithAec3 = false,
            initialSpeakerVerifyEnabled = speakerProfiles.isNotEmpty(),
            initialSpeakerVerifyThreshold = settings.speakerVerifyThreshold,
            initialSpeakerVerifyToleranceLevel = settings.speakerVerifyToleranceLevel,
            initialExperimentalRecognitionSensitivity = settings.experimentalRecognitionSensitivity,
            initialExperimentalTargetSpeakerBackend = settings.experimentalTargetSpeakerBackend,
            initialSpeakerProfiles = speakerProfiles.map { it.primary.copyOf() },
            initialNeuralSpeakerProfiles = speakerProfiles.map { it.neural?.copyOf() },
            initialConfirmationSpeakerProfiles = speakerProfiles.map { it.confirmation?.copyOf() }
        )
    }
}
