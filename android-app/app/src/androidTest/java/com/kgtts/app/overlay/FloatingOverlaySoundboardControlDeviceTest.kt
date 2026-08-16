package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.lhtstudio.kigtts.app.audio.SoundboardManager
import com.lhtstudio.kigtts.app.data.SoundboardItem
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingOverlaySoundboardControlDeviceTest {
    @Test
    fun playingSoundShowsStopControlAndStopsPlayback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Overlay permission is required for this device test", Settings.canDrawOverlays(context))
        val audioFile = File(context.cacheDir, "overlay-stop-control-test.wav")
        writeSilentWave(audioFile, durationSeconds = 8)
        val item = SoundboardItem(
            id = TEST_ITEM_ID,
            title = "Overlay stop control test",
            audioPath = audioFile.absolutePath,
            durationMs = 8_000L
        )

        try {
            SoundboardManager.setPlaybackGainPercent(0)
            FloatingOverlayService.openMiniQuickSubtitle(context)
            assertTrue(SoundboardManager.play(item))

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val stopControl = device.wait(
                Until.findObject(By.desc("停止当前音效")),
                UI_TIMEOUT_MS
            )
            assertNotNull("Stop soundboard control did not appear", stopControl)
            stopControl.click()

            val stopped = waitUntil(UI_TIMEOUT_MS) {
                SoundboardManager.playbackState().value.values.none { it.playing }
            }
            assertTrue("Soundboard playback did not stop", stopped)
            device.wait(Until.gone(By.desc("停止当前音效")), UI_TIMEOUT_MS)
            assertFalse(SoundboardManager.playbackState().value.values.any { it.playing })
        } finally {
            SoundboardManager.stopAll()
            SoundboardManager.setPlaybackGainPercent(100)
            FloatingOverlayService.stop(context)
            audioFile.delete()
        }
    }

    private suspend fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (predicate()) return true
            delay(50L)
        }
        return predicate()
    }

    private fun writeSilentWave(target: File, durationSeconds: Int) {
        val sampleCount = SAMPLE_RATE * durationSeconds
        val pcmSize = sampleCount * Short.SIZE_BYTES
        DataOutputStream(FileOutputStream(target)).use { output ->
            output.writeBytes("RIFF")
            output.writeIntLe(36 + pcmSize)
            output.writeBytes("WAVEfmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(SAMPLE_RATE)
            output.writeIntLe(SAMPLE_RATE * Short.SIZE_BYTES)
            output.writeShortLe(Short.SIZE_BYTES)
            output.writeShortLe(16)
            output.writeBytes("data")
            output.writeIntLe(pcmSize)
            repeat(sampleCount) { output.writeShortLe(0) }
        }
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    private companion object {
        const val TEST_ITEM_ID = Long.MIN_VALUE + 7
        const val SAMPLE_RATE = 16_000
        const val UI_TIMEOUT_MS = 5_000L
    }
}
