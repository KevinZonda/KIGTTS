package com.lhtstudio.kigtts.app.service

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lhtstudio.kigtts.app.audio.SimulatedAudioFixtures
import com.lhtstudio.kigtts.app.data.SpeechButtonActionMode
import com.lhtstudio.kigtts.app.overlay.RealtimeRuntimeBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealtimeHostListeningDeviceTest {
    @Test(timeout = 120_000L)
    fun listeningFinalizesRowsAndFabReclaimsMainRecognition(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioFile = File(context.cacheDir, TEST_WAV_NAME)
        assumeTrue("Listening test WAV is not provisioned", audioFile.isFile)
        val clip = SimulatedAudioFixtures.readPcm16Wave(audioFile)
        val serviceDeferred = CompletableDeferred<RealtimeHostService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val localBinder = binder as? RealtimeHostService.LocalBinder
                if (localBinder == null) {
                    serviceDeferred.completeExceptionally(
                        IllegalStateException("Unexpected realtime host binder")
                    )
                } else {
                    serviceDeferred.complete(localBinder.getService())
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent(context, RealtimeHostService::class.java)
        context.startService(intent)
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            "Unable to bind realtime host service"
        }
        val service = withTimeout(15_000L) { serviceDeferred.await() }

        try {
            Log.i(TAG, "Preparing host listening simulation")
            service.prepareListeningSimulationForTest()
            service.setSpeakerVerifyEnabled(false)
            val listeningSamples = clip.samples.copyOfRange(
                0,
                minOf(clip.samples.size, clip.sampleRate * LISTENING_TEST_SECONDS)
            )
            Log.i(TAG, "Running listening segment samples=${listeningSamples.size}")
            service.runSimulatedRecognitionForTest(
                samples = listeningSamples,
                sourceSampleRate = clip.sampleRate,
                paceAsRealtime = true
            )
            withTimeout(10_000L) {
                while (
                    service.stateFlow().value.listeningItems.isEmpty() ||
                    service.stateFlow().value.listeningStreamingText.isNotBlank()
                ) {
                    delay(100L)
                }
            }
            assertTrue(
                "Expected listening preview to be finalized",
                service.stateFlow().value.listeningStreamingText.isBlank()
            )
            val listeningIds = service.stateFlow().value.listeningItems.map { it.id }
            assertTrue(
                "Listening rows must use unique stable IDs",
                listeningIds.size == listeningIds.toSet().size
            )

            Log.i(TAG, "Switching recognition ownership to FAB")
            service.setSpeechButtonActionMode(SpeechButtonActionMode.HOLD_CONFIRM)
            service.beginPushToTalkSession()
            service.setPushToTalkPressed(true)
            val fabSamples = clip.samples.copyOfRange(
                0,
                minOf(clip.samples.size, clip.sampleRate * FAB_TEST_SECONDS)
            )
            service.runSimulatedRecognitionForTest(
                samples = fabSamples,
                sourceSampleRate = clip.sampleRate,
                paceAsRealtime = false,
                callbacksSynchronous = false
            )
            Log.i(TAG, "FAB simulation completed")
            withTimeout(10_000L) {
                while (service.stateFlow().value.pushToTalkStreamingText.isBlank()) delay(50L)
            }
            val requestIdBeforeCommit = service.stateFlow().value.quickSubtitleRequestId
            service.commitPushToTalkSession(RealtimeRuntimeBridge.PttCommitAction.SendToSubtitle)
            service.setPushToTalkPressed(false)
            withTimeout(10_000L) {
                while (service.stateFlow().value.quickSubtitleRequestId == requestIdBeforeCommit) {
                    delay(50L)
                }
            }
            assertTrue(
                "PTT commit did not include the asynchronously delivered final result",
                service.stateFlow().value.quickSubtitleText.isNotBlank()
            )
            assertTrue(
                "Listening mode was not restored after FAB release",
                service.stateFlow().value.listeningEnabled
            )
            Log.i(TAG, "Host listening/FAB smoke completed")
        } finally {
            context.unbindService(connection)
            context.stopService(intent)
        }
        Unit
    }

    private companion object {
        const val TEST_WAV_NAME = "listening-pipeline-device-test.wav"
        const val LISTENING_TEST_SECONDS = 14
        const val FAB_TEST_SECONDS = 5
        const val TAG = "HostListenTest"
    }
}
