package com.lhtstudio.kigtts.app.audio

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class ListeningPipelineDeviceTest {
    @Test(timeout = 180_000L)
    fun noisyMicrosoftDialogueProducesMultipleFinalCaptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioFile = File(context.cacheDir, TEST_WAV_NAME)
        val asrDir = RecognitionResourceRepository(context).installedAsrDir()
        assumeTrue("Listening test WAV is not provisioned", audioFile.isFile)
        assumeTrue("Recognition resource is not installed", asrDir?.isDirectory == true)

        val clip = SimulatedAudioFixtures.readPcm16Wave(audioFile)
        val settings = UserPrefs.getSettings(context)
        val listening = settings.listeningModeSettings.normalized()
        val finalEvents = Collections.synchronizedList(mutableListOf<TimedText>())
        val streamingEvents = Collections.synchronizedList(mutableListOf<TimedText>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val startedAt = SystemClock.elapsedRealtime()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DebugRealtimeControllerFactory.create(
            context = context,
            scope = scope,
            settings = settings,
            speakerProfiles = emptyList(),
            callbacks = DebugRealtimeCallbacks(
                onListeningResult = { _, text ->
                    finalEvents += TimedText(SystemClock.elapsedRealtime() - startedAt, text)
                },
                onListeningStreamingResult = { text ->
                    streamingEvents += TimedText(SystemClock.elapsedRealtime() - startedAt, text)
                },
                onError = errors::add
            )
        )
        var failure: Throwable? = null
        try {
            controller.setSuppressAsrAutoSpeak(true)
            require(controller.loadAsr(requireNotNull(asrDir))) { "ASR resource failed to load" }
            controller.setMinVolumePercent(listening.minVolumePercent)
            controller.setDenoiserMode(listening.denoiserMode)
            controller.setSpeechEnhancementMode(listening.speechEnhancementMode)
            controller.setClassicVadEnabled(listening.classicVadEnabled)
            controller.setSileroVadEnabled(listening.sileroVadEnabled)
            controller.setSileroVadThreshold(listening.sileroVadThreshold)
            controller.setSileroVadPreRollMs(listening.sileroVadPreRollMs)
            controller.setMainRecognitionEnabled(false)
            controller.setListeningRecognitionEnabled(true, listening.recognitionLanguage)
            withTimeout(140_000L) {
                controller.runSimulatedAudio(
                    samples = clip.samples,
                    sourceSampleRate = clip.sampleRate,
                    paceAsRealtime = true
                )
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            runCatching { controller.releaseAfterSimulatedAudio() }
            scope.cancel()
            writeReport(
                context = context,
                clip = clip,
                finalEvents = finalEvents,
                streamingEvents = streamingEvents,
                errors = errors,
                failure = failure
            )
        }

        val nonEmptyFinals = finalEvents.count { it.text.isNotBlank() }
        assertTrue("Expected at least four finalized captions, got $nonEmptyFinals", nonEmptyFinals >= 4)
        assertTrue("Recognition errors: ${errors.joinToString()}", errors.isEmpty())
    }

    private fun writeReport(
        context: Context,
        clip: DebugAudioClip,
        finalEvents: List<TimedText>,
        streamingEvents: List<TimedText>,
        errors: List<String>,
        failure: Throwable?
    ) {
        File(context.cacheDir, REPORT_FILE_NAME).writeText(
            buildString {
                appendLine("SOURCE=${clip.sourceLabel}")
                appendLine("DURATION_MS=${clip.samples.size * 1000L / clip.sampleRate}")
                appendLine("FINAL_COUNT=${finalEvents.count { it.text.isNotBlank() }}")
                finalEvents.forEach { appendLine("FINAL ${it.elapsedMs}ms ${it.text.ifBlank { "<empty>" }}") }
                appendLine("STREAM_COUNT=${streamingEvents.size}")
                streamingEvents.forEach {
                    appendLine("STREAM ${it.elapsedMs}ms ${it.text.ifBlank { "<empty>" }}")
                }
                errors.forEach { appendLine("ERROR $it") }
                failure?.let { appendLine("FAILURE=${it.stackTraceToString()}") }
            },
            Charsets.UTF_8
        )
    }

    private data class TimedText(val elapsedMs: Long, val text: String)

    private companion object {
        const val TEST_WAV_NAME = "listening-pipeline-device-test.wav"
        const val REPORT_FILE_NAME = "listening-pipeline-device-test.txt"
    }
}
