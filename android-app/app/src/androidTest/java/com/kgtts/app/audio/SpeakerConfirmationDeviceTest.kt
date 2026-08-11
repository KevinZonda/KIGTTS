package com.lhtstudio.kigtts.app.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class SpeakerConfirmationDeviceTest {
    @Test
    fun eres2NetV2ProducesFiniteEmbeddingOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = File(context.cacheDir, MODEL_FILE_NAME)
        val audioFile = File(context.cacheDir, TEST_WAV_NAME)
        assumeTrue("ERes2NetV2 model is not provisioned", model.isFile)
        assumeTrue("Speaker test WAV is not provisioned", audioFile.isFile)

        val extractor = SpeakerEmbeddingExtractor(
            null,
            SpeakerEmbeddingExtractorConfig(model.absolutePath, 2, false, "cpu")
        )
        try {
            val (samples, sampleRate) = readPcm16Wave(audioFile)
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                stream.inputFinished()
                assertTrue(extractor.isReady(stream))
                val embedding = extractor.compute(stream)
                assertEquals(192, embedding.size)
                assertTrue(embedding.all(Float::isFinite))
                assertTrue(sqrt(embedding.sumOf { it.toDouble() * it }) > 1.0)
            } finally {
                stream.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun readPcm16Wave(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF")
            input.readLittleEndianInt()
            require(input.readAscii(4) == "WAVE")
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
                        require(input.readLittleEndianShort() == 1)
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
            require(channels == 1 && bitsPerSample == 16 && dataOffset >= 0)
            input.seek(dataOffset)
            return FloatArray(dataSize / 2) {
                input.readLittleEndianShort().toShort() / 32768f
            } to sampleRate
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
        const val MODEL_FILE_NAME = "speaker-confirmation-device-test.onnx"
        const val TEST_WAV_NAME = "speaker-confirmation-device-test.wav"
    }
}
