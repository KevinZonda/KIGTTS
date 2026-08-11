package com.lhtstudio.kigtts.app.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class SenseVoiceV4DeviceTest {
    @Test
    fun recognizesSpokenFormDateWithBuiltInPunctuation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val modelDir = RecognitionResourceRepository(context).installedAsrDir()
        val audioFile = File(context.cacheDir, TEST_WAV_NAME)
        assumeTrue("V4 recognition resource is not installed", modelDir?.isDirectory == true)
        assumeTrue("V4 device test WAV is not provisioned", audioFile.isFile)

        val engine = AsrEngine(
            context,
            requireNotNull(modelDir),
            AsrRecognitionLanguage.MANDARIN
        )
        try {
            val (samples, sampleRate) = readPcm16Wave(audioFile)
            val result = engine.transcribe(samples, sampleRate)
            assertEquals("今天是二零二六年八月八日，下午三点十五分。", result)
        } finally {
            engine.close()
        }
    }

    private fun readPcm16Wave(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF") { "Not a RIFF file" }
            input.readLittleEndianInt()
            require(input.readAscii(4) == "WAVE") { "Not a WAVE file" }
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = 0
            while (input.filePointer + 8 <= input.length()) {
                val chunk = input.readAscii(4)
                val size = input.readLittleEndianInt()
                val next = input.filePointer + size + (size and 1)
                when (chunk) {
                    "fmt " -> {
                        require(input.readLittleEndianShort() == 1) { "Only PCM WAV is supported" }
                        channels = input.readLittleEndianShort()
                        sampleRate = input.readLittleEndianInt()
                        input.seek(input.filePointer + 6)
                        bitsPerSample = input.readLittleEndianShort()
                    }
                    "data" -> {
                        dataOffset = input.filePointer
                        dataSize = size
                    }
                }
                input.seek(next)
                if (dataOffset >= 0 && sampleRate > 0) break
            }
            require(channels == 1 && bitsPerSample == 16 && dataOffset >= 0) {
                "Expected mono PCM16 WAV"
            }
            input.seek(dataOffset)
            val samples = FloatArray(dataSize / 2)
            for (index in samples.indices) {
                samples[index] = input.readLittleEndianShort().toShort() / 32768f
            }
            return samples to sampleRate
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int {
        return readUnsignedByte() or (readUnsignedByte() shl 8)
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int {
        return readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)
    }

    private companion object {
        const val TEST_WAV_NAME = "sensevoice-v4-device-test.wav"
    }
}
