package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

class SimulatedAudioFixturesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scenariosAreDeterministicAndKeepEdgeSilence() {
        val sampleRate = 16_000
        val source = DebugAudioClip(
            samples = FloatArray(sampleRate) { index ->
                (sin(2.0 * PI * 220.0 * index / sampleRate) * 0.2).toFloat()
            },
            sampleRate = sampleRate,
            sourceLabel = "test"
        )

        val first = SimulatedAudioFixtures.prepareScenario(
            source,
            SimulatedAudioScenario.HEADSHELL_NOISY
        )
        val second = SimulatedAudioFixtures.prepareScenario(
            source,
            SimulatedAudioScenario.HEADSHELL_NOISY
        )

        assertArrayEquals(first.samples, second.samples, 0f)
        assertEquals(sampleRate * 245 / 100, first.samples.size)
        assertTrue(first.samples.take(sampleRate * 350 / 1000).all { it == 0f })
        assertTrue(first.samples.takeLast(sampleRate * 1100 / 1000).all { it == 0f })
        assertTrue(first.samples.all(Float::isFinite))
    }

    @Test
    fun quietScenarioReducesSpeechEnergy() {
        val source = DebugAudioClip(FloatArray(1000) { 0.25f }, 16_000, "test")
        val clean = SimulatedAudioFixtures.prepareScenario(source, SimulatedAudioScenario.CLEAN, 0, 0)
        val quiet = SimulatedAudioFixtures.prepareScenario(source, SimulatedAudioScenario.QUIET, 0, 0)

        assertTrue(
            SimulatedAudioFixtures.rms(quiet.samples) <
                SimulatedAudioFixtures.rms(clean.samples) * 0.3
        )
    }

    @Test
    fun pcm16StereoWaveIsDownmixed() {
        val file = temporaryFolder.newFile("stereo.wav")
        val left = shortArrayOf(Short.MAX_VALUE, 0, Short.MIN_VALUE)
        val right = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE)
        FileOutputStream(file).use { output ->
            output.write("RIFF".toByteArray(Charsets.US_ASCII))
            output.writeLittleEndianInt(36 + left.size * 4)
            output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianInt(16_000)
            output.writeLittleEndianInt(16_000 * 4)
            output.writeLittleEndianShort(4)
            output.writeLittleEndianShort(16)
            output.write("data".toByteArray(Charsets.US_ASCII))
            output.writeLittleEndianInt(left.size * 4)
            left.indices.forEach { index ->
                output.writeLittleEndianShort(left[index].toInt())
                output.writeLittleEndianShort(right[index].toInt())
            }
        }

        val clip = SimulatedAudioFixtures.readPcm16Wave(file)

        assertEquals(16_000, clip.sampleRate)
        assertEquals(3, clip.samples.size)
        assertTrue(clip.samples[0] in 0.49f..0.51f)
        assertTrue(clip.samples[1] in 0.49f..0.51f)
        assertTrue(clip.samples[2] < -0.99f)
    }

    private fun FileOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun FileOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
