package com.lhtstudio.kigtts.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.lhtstudio.kigtts.app.data.KOKORO_VOICE_NAME
import com.lhtstudio.kigtts.app.data.ModelRepository
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.SYSTEM_TTS_VOICE_NAME
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * Debug-only entry point for deterministic, silent end-to-end recognition checks.
 * It never starts AudioRecord and never sends synthesized audio to AudioTrack.
 */
class RealtimePipelineSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN) return
        val pendingResult = goAsync()
        thread(name = "realtime-pipeline-smoke") {
            val report = Collections.synchronizedList(mutableListOf<String>())
            try {
                runBlocking {
                    runSmoke(context.applicationContext, intent, report)
                }
            } catch (t: Throwable) {
                report += "RESULT=FAIL"
                report += "ERROR=${t.stackTraceToString()}"
                AppLogger.e("REALTIME_PIPELINE_SMOKE failed", t)
            } finally {
                File(context.cacheDir, REPORT_FILE_NAME).writeText(
                    report.joinToString(System.lineSeparator()),
                    Charsets.UTF_8
                )
                report.forEach { AppLogger.i("REALTIME_PIPELINE_SMOKE $it") }
                pendingResult.finish()
            }
        }
    }

    private suspend fun runSmoke(
        context: Context,
        intent: Intent,
        report: MutableList<String>
    ) {
        val settings = UserPrefs.getSettings(context)
        val repository = ModelRepository(context)
        val asrDir = requireNotNull(RecognitionResourceRepository(context).installedAsrDir()) {
            "ASR resource is missing"
        }
        val phrase = intent.getStringExtra(EXTRA_PHRASE)?.trim().orEmpty().ifBlank {
            DEFAULT_PHRASE
        }
        val source = loadSourceClip(context, intent, repository, settings, phrase, report)
        require(source.samples.isNotEmpty()) { "Simulated source audio is empty" }
        val scenarios = SimulatedAudioScenario.parse(intent.getStringExtra(EXTRA_SCENARIOS))
        val modes = listOf(UserPrefs.RECOGNITION_MODULE_MODE_EXPERIMENTAL)
        val listeningMode = intent.getBooleanExtra(EXTRA_LISTENING_MODE, false)
        val listeningRepeats = intent.getIntExtra(EXTRA_LISTENING_REPEATS, 2).coerceIn(1, 4)
        val listeningGapMs = intent.getIntExtra(EXTRA_LISTENING_GAP_MS, 1200)
            .coerceIn(200, 3000)
        val useSpeakerVerification = intent.getBooleanExtra(EXTRA_USE_SPEAKER_VERIFICATION, false)
        val profiles = UserPrefs.parseSpeakerVerifyProfiles(settings.speakerVerifyProfileCsv)
        val speakerVerificationActive = useSpeakerVerification && profiles.isNotEmpty()
        if (useSpeakerVerification && profiles.isEmpty()) {
            report += "SPEAKER_VERIFY=SKIPPED(no enrolled profile)"
        } else {
            report += "SPEAKER_VERIFY=${if (speakerVerificationActive) "ENABLED" else "DISABLED"}"
        }
        report += "SOURCE=${source.sourceLabel}"
        report += "SOURCE_RATE=${source.sampleRate}"
        report += "SOURCE_SAMPLES=${source.samples.size}"
        report += "SOURCE_RMS=${format(SimulatedAudioFixtures.rms(source.samples))}"
        report += "PHRASE=$phrase"
        report += "SCENARIOS=${scenarios.joinToString(",") { it.id }}"
        report += "MODES=${modes.joinToString(",") { modeLabel(it) }}"
        report += "LISTENING_MODE=$listeningMode"
        if (listeningMode) {
            report += "LISTENING_REPEATS=$listeningRepeats"
            report += "LISTENING_GAP_MS=$listeningGapMs"
        }

        val currentCase = AtomicReference<CaseObservation?>(null)
        val eventJournal = EventJournal(File(context.cacheDir, EVENT_FILE_NAME))
        eventJournal.reset()
        eventJournal.append("START source=${source.sourceLabel} listening=$listeningMode")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DebugRealtimeControllerFactory.create(
            context = context,
            scope = scope,
            settings = settings,
            speakerProfiles = if (speakerVerificationActive) {
                profiles.map {
                    DebugSpeakerVectors(
                        primary = it.vector,
                        neural = it.neuralVector,
                        confirmation = it.confirmationVector
                    )
                }
            } else {
                emptyList()
            },
            callbacks = DebugRealtimeCallbacks(
                onResult = { _, text -> currentCase.get()?.results?.add(text) },
                onStreamingResult = { text -> currentCase.get()?.streamingResults?.add(text) },
                onListeningResult = { id, text ->
                    currentCase.get()?.recordListeningFinal(id, text)
                    eventJournal.append("FINAL id=$id text=${text.ifBlank { "<empty>" }}")
                },
                onListeningStreamingResult = { text ->
                    currentCase.get()?.recordListeningStream(text)
                    eventJournal.append("STREAM text=${text.ifBlank { "<empty>" }}")
                },
                onLevel = { level ->
                    currentCase.get()?.let { observation ->
                        observation.maxLevel.updateAndGet { previous -> max(previous, level) }
                    }
                },
                onSpeakerVerify = { similarity, passed ->
                    currentCase.get()?.speakerEvents?.add("${format(similarity)}:$passed")
                },
                onStatus = { status -> currentCase.get()?.statuses?.add(status) },
                onError = { error ->
                    currentCase.get()?.errors?.add(error)
                    eventJournal.append("ERROR $error")
                }
            )
        )
        var cleanCasesPassed = true
        try {
            controller.setSuppressAsrAutoSpeak(true)
            eventJournal.append("LOAD_ASR_BEGIN")
            require(controller.loadAsr(asrDir)) { "ASR resource failed to load" }
            eventJournal.append("LOAD_ASR_DONE")
            if (listeningMode) {
                val listening = settings.listeningModeSettings.normalized()
                controller.setMinVolumePercent(listening.minVolumePercent)
                controller.setDenoiserMode(listening.denoiserMode)
                controller.setSpeechEnhancementMode(listening.speechEnhancementMode)
                controller.setClassicVadEnabled(listening.classicVadEnabled)
                controller.setSileroVadEnabled(listening.sileroVadEnabled)
                controller.setSileroVadThreshold(listening.sileroVadThreshold)
                controller.setSileroVadPreRollMs(listening.sileroVadPreRollMs)
                controller.setMainRecognitionEnabled(false)
                eventJournal.append("ENABLE_LISTENING_BEGIN")
                controller.setListeningRecognitionEnabled(true, listening.recognitionLanguage)
                eventJournal.append("ENABLE_LISTENING_DONE language=${listening.recognitionLanguage}")
            }
            for (mode in modes) {
                for (scenario in scenarios) {
                    val clip = SimulatedAudioFixtures.prepareScenario(source, scenario)
                    val observation = CaseObservation()
                    currentCase.set(observation)
                    val startedAt = SystemClock.elapsedRealtime()
                    observation.startedAtMs = startedAt
                    val inputSamples = if (listeningMode) {
                        buildListeningSequence(
                            samples = clip.samples,
                            sampleRate = clip.sampleRate,
                            repeats = listeningRepeats,
                            gapMs = listeningGapMs
                        )
                    } else {
                        clip.samples
                    }
                    eventJournal.append(
                        "RUN_BEGIN samples=${inputSamples.size} rate=${clip.sampleRate}"
                    )
                    val stats = withTimeout(120_000L) {
                        controller.runSimulatedAudio(
                            samples = inputSamples,
                            sourceSampleRate = clip.sampleRate,
                            // Broadcast receivers have a short system timeout. Long, paced
                            // listening checks run through ListeningPipelineDeviceTest instead.
                            paceAsRealtime = false
                        )
                    }.also {
                        eventJournal.append(
                            "RUN_DONE chunks=${it.chunkCount} elapsedMs=${it.elapsedMs}"
                        )
                    }
                    val wallMs = SystemClock.elapsedRealtime() - startedAt
                    currentCase.compareAndSet(observation, null)
                    val texts = observation.results.toList()
                    val nonEmptyListeningFinals = observation.listeningFinals.count {
                        it.text.isNotBlank()
                    }
                    val casePassed = if (listeningMode) {
                        nonEmptyListeningFinals >= listeningRepeats && observation.errors.isEmpty()
                    } else {
                        texts.any(String::isNotBlank) && observation.errors.isEmpty()
                    }
                    if (scenario == SimulatedAudioScenario.CLEAN && !casePassed) {
                        cleanCasesPassed = false
                    }
                    report += buildString {
                        append("CASE=")
                        append(modeLabel(mode))
                        append('/')
                        append(scenario.id)
                        append(" pass=")
                        append(casePassed)
                        append(" rms=")
                        append(format(SimulatedAudioFixtures.rms(clip.samples)))
                        append(" level=")
                        append(format(observation.maxLevel.get()))
                        append(" chunks=")
                        append(stats.chunkCount)
                        append(" pipelineMs=")
                        append(stats.elapsedMs)
                        append(" wallMs=")
                        append(wallMs)
                    }
                    report += "TEXT_${modeLabel(mode)}_${scenario.id}=${texts.joinToString(" | ").ifBlank { "<empty>" }}"
                    if (observation.streamingResults.isNotEmpty()) {
                        report += "STREAM_${modeLabel(mode)}_${scenario.id}=" +
                            observation.streamingResults.joinToString(" | ")
                    }
                    if (observation.listeningStreams.isNotEmpty()) {
                        report += "LISTENING_STREAM_${modeLabel(mode)}_${scenario.id}=" +
                            observation.listeningStreams.joinToString(" | ") { it.reportValue() }
                    }
                    report += "LISTENING_FINAL_${modeLabel(mode)}_${scenario.id}=" +
                        observation.listeningFinals.joinToString(" | ") { it.reportValue() }
                            .ifBlank { "<none>" }
                    if (observation.speakerEvents.isNotEmpty()) {
                        report += "SPEAKER_${modeLabel(mode)}_${scenario.id}=" +
                            observation.speakerEvents.joinToString(",")
                    }
                    if (observation.errors.isNotEmpty()) {
                        report += "ERROR_${modeLabel(mode)}_${scenario.id}=" +
                            observation.errors.joinToString(" | ")
                    }
                }
            }
            report += "RESULT=${if (cleanCasesPassed) "PASS" else "FAIL"}"
        } finally {
            currentCase.set(null)
            eventJournal.append("RELEASE_BEGIN")
            runCatching { controller.releaseAfterSimulatedAudio() }
                .onFailure { eventJournal.append("RELEASE_ERROR ${it.message}") }
            eventJournal.append("RELEASE_DONE")
            scope.cancel()
        }
    }

    private suspend fun loadSourceClip(
        context: Context,
        intent: Intent,
        repository: ModelRepository,
        settings: UserPrefs.AppSettings,
        phrase: String,
        report: MutableList<String>
    ): DebugAudioClip {
        val requestedName = intent.getStringExtra(EXTRA_AUDIO_FILE)
            ?.let { File(it).name }
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_AUDIO_FILE_NAME
        val requested = File(context.cacheDir, requestedName)
        if (requested.isFile) return SimulatedAudioFixtures.readPcm16Wave(requested)
        val previousSmokeAudio = File(context.cacheDir, PREVIOUS_SMOKE_AUDIO_FILE_NAME)
        if (previousSmokeAudio.isFile) {
            report += "SOURCE_FALLBACK=${previousSmokeAudio.name}"
            return SimulatedAudioFixtures.readPcm16Wave(previousSmokeAudio)
        }

        val candidates = resolveVoiceCandidates(context, repository)
        val failures = mutableListOf<String>()
        for (voiceDir in candidates) {
            val engine = runCatching { DefaultSpeechModuleFactory.createTts(context, voiceDir) }
                .onFailure { failures += "${voiceDir.name}:init:${it.message}" }
                .getOrNull() ?: continue
            try {
                engine.setKokoroVoice(settings.kokoroSpeakerId)
                engine.setSynthesisTuning(
                    settings.piperNoiseScale,
                    settings.piperLengthScale,
                    settings.piperNoiseW,
                    settings.piperSentenceSilence
                )
                val samples = runCatching { engine.synthesize(phrase, 0f) }
                    .onFailure { failures += "${voiceDir.name}:synthesize:${it.message}" }
                    .getOrNull()
                if (samples != null && samples.isNotEmpty()) {
                    report += "SYNTH_VOICE=${voiceDir.name}"
                    return DebugAudioClip(samples, engine.sampleRate, "silent_tts:${voiceDir.name}")
                }
            } finally {
                runCatching { engine.close() }
            }
        }
        error(
            "No WAV was provided and silent TTS synthesis failed: " +
                failures.joinToString("; ").ifBlank { "no voice source" }
        )
    }

    private suspend fun resolveVoiceCandidates(
        context: Context,
        repository: ModelRepository
    ): List<File> {
        val candidates = linkedSetOf<File>()
        when (val lastName = UserPrefs.getLastVoiceName(context)) {
            SYSTEM_TTS_VOICE_NAME -> candidates += repository.systemTtsVirtualDir()
            KOKORO_VOICE_NAME -> repository.kokoroVoiceDir()
                .takeIf { repository.kokoroVoiceStatus().installed }
                ?.let(candidates::add)
            null -> Unit
            else -> repository.resolveVoicePack(lastName)?.let(candidates::add)
        }
        repository.listVoicePacks().mapTo(candidates) { it.dir }
        repository.kokoroVoiceDir()
            .takeIf { repository.kokoroVoiceStatus().installed }
            ?.let(candidates::add)
        candidates += repository.systemTtsVirtualDir()
        return candidates.toList()
    }

    private fun modeLabel(@Suppress("UNUSED_PARAMETER") mode: Int): String = "current"

    private fun buildListeningSequence(
        samples: FloatArray,
        sampleRate: Int,
        repeats: Int,
        gapMs: Int
    ): FloatArray {
        val edgeSilenceSamples = sampleRate / 2
        val gapSamples = sampleRate * gapMs / 1000
        val totalSamples = edgeSilenceSamples * 2 +
            samples.size * repeats + gapSamples * (repeats - 1)
        val output = FloatArray(totalSamples)
        var offset = edgeSilenceSamples
        repeat(repeats) { index ->
            samples.copyInto(output, destinationOffset = offset)
            offset += samples.size
            if (index < repeats - 1) offset += gapSamples
        }
        return output
    }

    private fun format(value: Number): String = String.format(Locale.US, "%.4f", value.toDouble())

    private class CaseObservation {
        @Volatile
        var startedAtMs: Long = 0L
        val results = Collections.synchronizedList(mutableListOf<String>())
        val streamingResults = Collections.synchronizedList(mutableListOf<String>())
        val listeningFinals = Collections.synchronizedList(mutableListOf<TimedTextEvent>())
        val listeningStreams = Collections.synchronizedList(mutableListOf<TimedTextEvent>())
        val speakerEvents = Collections.synchronizedList(mutableListOf<String>())
        val statuses = Collections.synchronizedList(mutableListOf<String>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val maxLevel = AtomicReference(0f)

        fun recordListeningFinal(id: Long, text: String) {
            listeningFinals += TimedTextEvent(elapsedMs(), text, id)
        }

        fun recordListeningStream(text: String) {
            listeningStreams += TimedTextEvent(elapsedMs(), text)
        }

        private fun elapsedMs(): Long =
            (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
    }

    private data class TimedTextEvent(
        val elapsedMs: Long,
        val text: String,
        val id: Long? = null
    ) {
        fun reportValue(): String = buildString {
            append(elapsedMs)
            append("ms")
            id?.let {
                append('#')
                append(it)
            }
            append(':')
            append(text.ifBlank { "<empty>" })
        }
    }

    private class EventJournal(private val file: File) {
        private val lock = Any()

        fun reset() {
            synchronized(lock) {
                file.writeText("", Charsets.UTF_8)
            }
        }

        fun append(message: String) {
            synchronized(lock) {
                file.appendText(
                    "${SystemClock.elapsedRealtime()} $message${System.lineSeparator()}",
                    Charsets.UTF_8
                )
            }
        }
    }

    companion object {
        const val ACTION_RUN = "com.lhtstudio.kigtts.app.action.RUN_REALTIME_PIPELINE_SMOKE"
        const val EXTRA_AUDIO_FILE = "audio_file"
        const val EXTRA_PHRASE = "phrase"
        const val EXTRA_SCENARIOS = "scenarios"
        const val EXTRA_MODES = "modes"
        const val EXTRA_USE_SPEAKER_VERIFICATION = "use_speaker_verification"
        const val EXTRA_LISTENING_MODE = "listening_mode"
        const val EXTRA_LISTENING_REPEATS = "listening_repeats"
        const val EXTRA_LISTENING_GAP_MS = "listening_gap_ms"
        const val REPORT_FILE_NAME = "realtime-pipeline-smoke.txt"
        const val EVENT_FILE_NAME = "realtime-pipeline-smoke-events.txt"
        const val DEFAULT_AUDIO_FILE_NAME = "realtime-pipeline-test.wav"
        private const val PREVIOUS_SMOKE_AUDIO_FILE_NAME = "sensevoice-v4-device-test.wav"
        private const val DEFAULT_PHRASE = "今天是二零二六年八月九日，下午三点十五分。"
    }
}
