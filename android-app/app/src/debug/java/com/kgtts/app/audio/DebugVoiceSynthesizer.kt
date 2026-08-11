package com.lhtstudio.kigtts.app.audio

import android.content.Context
import com.lhtstudio.kigtts.app.data.KOKORO_VOICE_NAME
import com.lhtstudio.kigtts.app.data.ModelRepository
import com.lhtstudio.kigtts.app.data.SYSTEM_TTS_VOICE_NAME
import com.lhtstudio.kigtts.app.data.UserPrefs
import java.io.File

internal data class DebugVoiceCandidate(
    val id: String,
    val displayName: String,
    val directory: File
)

internal object DebugVoiceSynthesizer {
    fun listInstalled(repository: ModelRepository): List<DebugVoiceCandidate> {
        val candidates = linkedMapOf<String, DebugVoiceCandidate>()
        repository.listVoicePacks().forEach { info ->
            val candidate = DebugVoiceCandidate(
                id = info.dir.name,
                displayName = info.meta.name.ifBlank { info.dir.name },
                directory = info.dir
            )
            candidates[info.dir.absolutePath] = candidate
        }
        repository.kokoroVoiceDir()
            .takeIf { repository.kokoroVoiceStatus().installed }
            ?.let { directory ->
                candidates[directory.absolutePath] = DebugVoiceCandidate(
                    id = KOKORO_VOICE_NAME,
                    displayName = "Kokoro",
                    directory = directory
                )
            }
        candidates[repository.systemTtsVirtualDir().absolutePath] = DebugVoiceCandidate(
            id = SYSTEM_TTS_VOICE_NAME,
            displayName = "系统语音合成",
            directory = repository.systemTtsVirtualDir()
        )
        return candidates.values.toList()
    }

    fun find(
        candidates: List<DebugVoiceCandidate>,
        query: String
    ): DebugVoiceCandidate? {
        val normalized = query.trim()
        return candidates.firstOrNull {
            it.displayName.equals(normalized, ignoreCase = true) ||
                it.id.equals(normalized, ignoreCase = true) ||
                it.directory.name.equals(normalized, ignoreCase = true)
        }
    }

    fun createEngine(
        context: Context,
        candidate: DebugVoiceCandidate,
        settings: UserPrefs.AppSettings
    ): TtsModule {
        return DefaultSpeechModuleFactory.createTts(context, candidate.directory).also { engine ->
            engine.setKokoroVoice(settings.kokoroSpeakerId)
            engine.setSynthesisTuning(
                settings.piperNoiseScale,
                settings.piperLengthScale,
                settings.piperNoiseW,
                settings.piperSentenceSilence
            )
        }
    }

    fun synthesize(
        engine: TtsModule,
        candidate: DebugVoiceCandidate,
        text: String
    ): DebugAudioClip {
        val samples = engine.synthesize(text, 0f)
        require(samples.isNotEmpty()) { "Voice ${candidate.displayName} returned empty audio" }
        return DebugAudioClip(
            samples = samples,
            sampleRate = engine.sampleRate,
            sourceLabel = "silent_tts:${candidate.displayName}"
        )
    }
}
