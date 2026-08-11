package com.lhtstudio.kigtts.app.audio

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.UserPrefs
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeLanguageSwitchDeviceTest {
    @Test(timeout = 180_000L)
    fun listeningLanguageCanChangeWhileRnNoiseIsCapturing(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        val asrDir = RecognitionResourceRepository(context).installedAsrDir()
        assumeTrue("Recognition resource is not installed", asrDir?.isDirectory == true)

        val errors = Collections.synchronizedList(mutableListOf<String>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = UserPrefs.getSettings(context).copy(
            denoiserMode = AudioDenoiserMode.RNNOISE,
            speechEnhancementMode = SpeechEnhancementMode.OFF
        )
        val controller = DebugRealtimeControllerFactory.create(
            context = context,
            scope = scope,
            settings = settings,
            speakerProfiles = emptyList(),
            callbacks = DebugRealtimeCallbacks(onError = errors::add)
        )
        try {
            check(controller.loadAsr(requireNotNull(asrDir)))
            controller.setDenoiserMode(AudioDenoiserMode.RNNOISE)
            controller.setMainRecognitionEnabled(false)
            controller.setListeningRecognitionEnabled(true, AsrRecognitionLanguage.MANDARIN)
            check(controller.startMic())
            delay(500L)

            listOf(
                AsrRecognitionLanguage.ENGLISH,
                AsrRecognitionLanguage.JAPANESE,
                AsrRecognitionLanguage.KOREAN,
                AsrRecognitionLanguage.AUTO,
                AsrRecognitionLanguage.MANDARIN
            ).forEach { language ->
                controller.setListeningRecognitionEnabled(true, language)
                delay(250L)
            }
            controller.stopMic()
        } finally {
            runCatching { controller.releaseAfterSimulatedAudio() }
            scope.cancel()
        }

        assertTrue("Audio lifecycle errors: ${errors.joinToString()}", errors.isEmpty())
    }
}
