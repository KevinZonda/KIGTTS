package com.lhtstudio.kigtts.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lhtstudio.kigtts.app.data.ModelRepository
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Debug-only synthetic speaker-verification matrix. It never changes saved profiles. */
class SyntheticVoiceprintMatrixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN) return
        val pendingResult = goAsync()
        thread(name = "synthetic-voiceprint-matrix") {
            val report = Collections.synchronizedList(mutableListOf<String>())
            try {
                runBlocking { runMatrix(context.applicationContext, intent, report) }
            } catch (t: Throwable) {
                report += "RESULT=FAIL"
                report += "ERROR=${t.stackTraceToString()}"
                AppLogger.e("SYNTHETIC_VOICEPRINT_MATRIX failed", t)
            } finally {
                File(context.cacheDir, REPORT_FILE_NAME).writeText(
                    report.joinToString(System.lineSeparator()),
                    Charsets.UTF_8
                )
                report.forEach { AppLogger.i("SYNTHETIC_VOICEPRINT_MATRIX $it") }
                pendingResult.finish()
            }
        }
    }

    private suspend fun runMatrix(
        context: Context,
        intent: Intent,
        report: MutableList<String>
    ) {
        val settings = UserPrefs.getSettings(context)
        val repository = ModelRepository(context)
        val asrDir = requireNotNull(RecognitionResourceRepository(context).installedAsrDir()) {
            "ASR resource is missing"
        }
        val candidates = DebugVoiceSynthesizer.listInstalled(repository)
        require(candidates.isNotEmpty()) { "No installed voice is available" }
        val externalDirectory = intent.getStringExtra(EXTRA_EXTERNAL_DIRECTORY)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::File)
            ?: File(context.externalCacheDir ?: context.cacheDir, ExternalVoiceprintFixtures.DIRECTORY_NAME)
        val externalFixtures = ExternalVoiceprintFixtures.load(externalDirectory)
        val enrollmentQuery = intent.getStringExtra(EXTRA_ENROLLMENT_VOICE)
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_ENROLLMENT_VOICE }
        val appEnrollmentVoice = DebugVoiceSynthesizer.find(candidates, enrollmentQuery)
        val externalEnrollmentVoice = findExternalVoice(externalFixtures.sources, enrollmentQuery)
        require(appEnrollmentVoice != null || externalEnrollmentVoice != null) {
            "Enrollment voice '$enrollmentQuery' was not found"
        }
        require(externalEnrollmentVoice == null || externalEnrollmentVoice.enrollmentClips.size == ENROLLMENT_PHRASES.size) {
            "External enrollment voice '$enrollmentQuery' needs ${ENROLLMENT_PHRASES.size} enrollment clips"
        }
        val enrollmentDisplayName = appEnrollmentVoice?.displayName
            ?: requireNotNull(externalEnrollmentVoice).displayName
        val enrollmentOrigin = if (appEnrollmentVoice != null) "app_tts" else {
            requireNotNull(externalEnrollmentVoice).origin
        }
        val enrollmentKey = sourceKey(
            appEnrollmentVoice?.id ?: requireNotNull(externalEnrollmentVoice).id,
            enrollmentOrigin
        )
        val requestedPhrase = intent.getStringExtra(EXTRA_TEST_PHRASE)
            ?.trim()
            .orEmpty()
        val testPhrase = requestedPhrase.ifBlank { externalFixtures.phrase ?: DEFAULT_TEST_PHRASE }
        val modes = parseModes(intent.getStringExtra(EXTRA_MODES))
        report += "ENROLLMENT_VOICE=$enrollmentDisplayName"
        report += "ENROLLMENT_ORIGIN=$enrollmentOrigin"
        report += "INSTALLED_VOICES=${candidates.joinToString(" | ") { it.displayName }}"
        report += "EXTERNAL_DIRECTORY=${externalDirectory.absolutePath}"
        report += "EXTERNAL_VOICES=${externalFixtures.sources.joinToString(" | ") { "${it.displayName} (${it.origin})" }.ifBlank { "<none>" }}"
        externalFixtures.issues.forEach { report += "EXTERNAL_FIXTURE_SKIPPED=$it" }
        report += "TEST_PHRASE=$testPhrase"
        if (externalFixtures.phrase != null && externalFixtures.phrase != testPhrase) {
            report += "EXTERNAL_PHRASE_WARNING=manifest phrase differs from requested phrase"
        }
        report += "MODES=${modes.joinToString(",") { modeLabel(it) }}"

        val enrollmentClips = appEnrollmentVoice?.let { voice ->
            synthesizeEnrollmentClips(context, settings, voice)
        } ?: requireNotNull(externalEnrollmentVoice).enrollmentClips
        val profiles = createSyntheticEnrollmentProfiles(
            context = context,
            clips = enrollmentClips,
            report = report
        )
        require(profiles.size == ENROLLMENT_PHRASES.size) {
            "Synthetic enrollment did not produce all profiles"
        }
        reportEnrollmentSimilarity(profiles, report)

        val synthesizedVoices = (synthesizeTestVoices(
            context = context,
            settings = settings,
            candidates = candidates,
            phrase = testPhrase,
            report = report
        ) + externalFixtures.sources).map { source ->
            val isOwner = sourceKey(source.id, source.origin) == enrollmentKey
            source.copy(
                isOwner = isOwner,
                scenarios = if (isOwner) SimulatedAudioScenario.entries else source.scenarios
            )
        }
        require(synthesizedVoices.any(MatrixVoiceSource::isOwner)) {
            "Enrollment voice could not synthesize the unseen test phrase"
        }

        val currentCase = AtomicReference<CaseObservation?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DebugRealtimeControllerFactory.create(
            context = context,
            scope = scope,
            settings = settings,
            speakerProfiles = profiles,
            callbacks = DebugRealtimeCallbacks(
                onResult = { _, text -> currentCase.get()?.results?.add(text) },
                onSpeakerVerify = { similarity, passed ->
                    currentCase.get()?.speakerEvents?.add(SpeakerEvent(similarity, passed))
                },
                onError = { error -> currentCase.get()?.errors?.add(error) }
            )
        )
        var ownerCases = 0
        var ownerAccepted = 0
        var impostorCases = 0
        var impostorRejected = 0
        var falseAccepts = 0
        var noDecisions = 0
        try {
            controller.setSuppressAsrAutoSpeak(true)
            require(controller.loadAsr(asrDir)) { "ASR resource failed to load" }
            for (mode in modes) {
                for (source in synthesizedVoices) {
                    for (scenario in source.scenarios) {
                        val clip = SimulatedAudioFixtures.prepareScenario(source.clip, scenario)
                        val observation = CaseObservation()
                        controller.resetSpeakerVerificationSession()
                        currentCase.set(observation)
                        val stats = controller.runSimulatedAudio(
                            samples = clip.samples,
                            sourceSampleRate = clip.sampleRate,
                            paceAsRealtime = false
                        )
                        currentCase.compareAndSet(observation, null)
                        val speakerEvents = observation.speakerEvents.toList()
                        val accepted = speakerEvents.any { it.passed } ||
                            observation.results.any(String::isNotBlank)
                        val decisionObserved = speakerEvents.isNotEmpty() ||
                            observation.results.any(String::isNotBlank)
                        val expected = if (source.isOwner) "ACCEPT" else "REJECT"
                        val actual = when {
                            accepted -> "ACCEPT"
                            decisionObserved -> "REJECT"
                            else -> "NO_DECISION"
                        }
                        if (source.isOwner) {
                            ownerCases++
                            if (accepted) ownerAccepted++
                        } else {
                            impostorCases++
                            if (decisionObserved && !accepted) impostorRejected++
                            if (accepted) falseAccepts++
                        }
                        if (!decisionObserved) noDecisions++
                        val bestSimilarity = speakerEvents.maxOfOrNull { it.similarity } ?: -1f
                        report += buildString {
                            append("CASE=")
                            append(modeLabel(mode))
                            append('/')
                            append(source.displayName)
                            append('/')
                            append(scenario.id)
                            append(" origin=")
                            append(source.origin)
                            append(" expected=")
                            append(expected)
                            append(" actual=")
                            append(actual)
                            append(" correct=")
                            append(expected == actual)
                            append(" similarity=")
                            append(format(bestSimilarity))
                            append(" decisions=")
                            append(
                                speakerEvents.joinToString(",") {
                                    "${format(it.similarity)}:${if (it.passed) "pass" else "reject"}"
                                }.ifBlank { "<none>" }
                            )
                            append(" chunks=")
                            append(stats.chunkCount)
                            append(" pipelineMs=")
                            append(stats.elapsedMs)
                            append(" text=")
                            append(observation.results.joinToString(" | ").ifBlank { "<empty>" })
                        }
                        if (observation.errors.isNotEmpty()) {
                            report += "CASE_ERROR_${modeLabel(mode)}_${source.id}_${scenario.id}=" +
                                observation.errors.joinToString(" | ")
                        }
                    }
                }
            }
            val ownerRate = ownerAccepted.toDouble() / ownerCases.coerceAtLeast(1)
            val impostorRate = impostorRejected.toDouble() / impostorCases.coerceAtLeast(1)
            report += "SUMMARY_OWNER_ACCEPTED=$ownerAccepted/$ownerCases (${format(ownerRate)})"
            report += "SUMMARY_IMPOSTOR_REJECTED=$impostorRejected/$impostorCases (${format(impostorRate)})"
            report += "SUMMARY_FALSE_ACCEPTS=$falseAccepts"
            report += "SUMMARY_NO_DECISIONS=$noDecisions"
            report += "RESULT=${if (falseAccepts == 0 && ownerAccepted > 0) "PASS" else "FAIL"}"
        } finally {
            currentCase.set(null)
            runCatching { controller.releaseAfterSimulatedAudio() }
            scope.cancel()
        }
    }

    private fun createSyntheticEnrollmentProfiles(
        context: Context,
        clips: List<DebugAudioClip>,
        report: MutableList<String>
    ): List<DebugSpeakerVectors> {
        val neuralResources = RecognitionResourceRepository.resolveNeuralSpeakerFilterResources(context)
        val profiles = mutableListOf<DebugSpeakerVectors>()
        try {
            clips.forEachIndexed { index, synthesized ->
                val pcm16k = SimulatedAudioFixtures.resample(
                    synthesized.samples,
                    synthesized.sampleRate,
                    TARGET_SAMPLE_RATE
                )
                val padded = SimulatedAudioFixtures.prepareScenario(
                    DebugAudioClip(pcm16k, TARGET_SAMPLE_RATE, synthesized.sourceLabel),
                    SimulatedAudioScenario.CLEAN,
                    leadingSilenceMs = 300,
                    trailingSilenceMs = 300
                )
                val assessment = SpeakerEnrollmentQualityPolicy.assess(
                    padded.samples,
                    padded.sampleRate
                )
                require(assessment.accepted && assessment.audio != null) {
                    "Enrollment phrase ${index + 1} failed quality check: ${assessment.message}"
                }
                val enrollmentAudio = requireNotNull(assessment.audio)
                val primary = requireNotNull(
                    SpeakerVerifier.computeEmbedding(context, enrollmentAudio, TARGET_SAMPLE_RATE)
                ) { "CAMPPlus enrollment embedding ${index + 1} failed" }
                val confirmation = SpeakerVerifier.computeConfirmationEmbedding(
                    context,
                    enrollmentAudio,
                    TARGET_SAMPLE_RATE
                )
                val neural = neuralResources?.ecapaModel?.let { model ->
                    SpeechBrainEcapaEmbedder.compute(
                        context,
                        model,
                        enrollmentAudio,
                        TARGET_SAMPLE_RATE
                    )
                }
                profiles += DebugSpeakerVectors(primary, neural, confirmation)
                report += "ENROLLMENT_${index + 1}=durationSamples=${enrollmentAudio.size} " +
                    "activeRatio=${format(assessment.activeRatio)} " +
                    "snrDb=${format(assessment.estimatedSnrDb)} " +
                    "primaryDim=${primary.size} confirmationDim=${confirmation?.size ?: 0} " +
                    "neuralDim=${neural?.size ?: 0}"
            }
        } finally {
            SpeechBrainEcapaEmbedder.releaseModel()
        }
        return profiles
    }

    private fun synthesizeEnrollmentClips(
        context: Context,
        settings: UserPrefs.AppSettings,
        voice: DebugVoiceCandidate
    ): List<DebugAudioClip> {
        val engine = DebugVoiceSynthesizer.createEngine(context, voice, settings)
        return try {
            ENROLLMENT_PHRASES.map { phrase ->
                DebugVoiceSynthesizer.synthesize(engine, voice, phrase)
            }
        } finally {
            runCatching { engine.close() }
        }
    }

    private fun reportEnrollmentSimilarity(
        profiles: List<DebugSpeakerVectors>,
        report: MutableList<String>
    ) {
        for (first in 0 until profiles.lastIndex) {
            for (second in first + 1 until profiles.size) {
                val primary = SpeakerVerifier.cosineSimilarity(
                    profiles[first].primary,
                    profiles[second].primary
                )
                val confirmation = profiles[first].confirmation?.let { left ->
                    profiles[second].confirmation?.let { right ->
                        SpeakerVerifier.cosineSimilarity(left, right)
                    }
                }
                report += "ENROLLMENT_SIM_${first + 1}_${second + 1}=" +
                    "primary=${format(primary)} confirmation=${format(confirmation ?: -1f)}"
            }
        }
        val primaryThreshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles.map { it.primary },
            defaultThreshold = 0.5f,
            minimum = 0.38f,
            maximum = 0.68f
        )
        val confirmationThreshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles.mapNotNull { it.confirmation },
            defaultThreshold = 0.60f,
            minimum = 0.46f,
            maximum = 0.74f
        )
        report += "CALIBRATED_THRESHOLDS=primary=${format(primaryThreshold)} " +
            "confirmation=${format(confirmationThreshold)}"
    }

    private fun synthesizeTestVoices(
        context: Context,
        settings: UserPrefs.AppSettings,
        candidates: List<DebugVoiceCandidate>,
        phrase: String,
        report: MutableList<String>
    ): List<MatrixVoiceSource> {
        return candidates.mapNotNull { candidate ->
            val engine = runCatching {
                DebugVoiceSynthesizer.createEngine(context, candidate, settings)
            }.onFailure {
                report += "VOICE_SKIPPED=${candidate.displayName} init=${it.message}"
            }.getOrNull() ?: return@mapNotNull null
            try {
                runCatching {
                    MatrixVoiceSource(
                        id = candidate.id,
                        displayName = candidate.displayName,
                        origin = "app_tts",
                        clip = DebugVoiceSynthesizer.synthesize(engine, candidate, phrase),
                        isOwner = false,
                        scenarios = listOf(SimulatedAudioScenario.CLEAN)
                    )
                }.onFailure {
                    report += "VOICE_SKIPPED=${candidate.displayName} synthesize=${it.message}"
                }.getOrNull()
            } finally {
                runCatching { engine.close() }
            }
        }
    }

    private fun parseModes(@Suppress("UNUSED_PARAMETER") raw: String?): List<Int> =
        listOf(UserPrefs.RECOGNITION_MODULE_MODE_EXPERIMENTAL)

    private fun modeLabel(@Suppress("UNUSED_PARAMETER") mode: Int): String = "current"

    private fun findExternalVoice(
        sources: List<MatrixVoiceSource>,
        query: String
    ): MatrixVoiceSource? = sources.firstOrNull { source ->
        source.displayName.equals(query, ignoreCase = true) ||
            source.id.equals(query, ignoreCase = true)
    }

    private fun sourceKey(id: String, origin: String): String = "$origin::$id"

    private fun format(value: Number): String = String.format(Locale.US, "%.4f", value.toDouble())

    private data class SpeakerEvent(
        val similarity: Float,
        val passed: Boolean
    )

    private class CaseObservation {
        val results = Collections.synchronizedList(mutableListOf<String>())
        val speakerEvents = Collections.synchronizedList(mutableListOf<SpeakerEvent>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
    }

    companion object {
        const val ACTION_RUN = "com.lhtstudio.kigtts.app.action.RUN_SYNTHETIC_VOICEPRINT_MATRIX"
        const val EXTRA_ENROLLMENT_VOICE = "enrollment_voice"
        const val EXTRA_TEST_PHRASE = "test_phrase"
        const val EXTRA_MODES = "modes"
        const val EXTRA_EXTERNAL_DIRECTORY = "external_directory"
        const val REPORT_FILE_NAME = "synthetic-voiceprint-matrix.txt"
        private const val DEFAULT_ENROLLMENT_VOICE = "铃"
        private const val DEFAULT_TEST_PHRASE = "请帮我打开现场字幕，并保持持续识别。"
        private const val TARGET_SAMPLE_RATE = 16_000
        private val ENROLLMENT_PHRASES = listOf(
            "你好，我正在测试声纹识别功能。",
            "今天的天气很好，我们一起出发吧。",
            "请确认这段声音属于同一个说话人。"
        )
    }
}
