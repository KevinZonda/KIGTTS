package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SpeechBrainFbankExtractorTest {
    @Test
    fun fbankMatchesSpeechBrainFixture() {
        val filterbank = readFloats("fbank_filterbank.bin")
        val input = readFloats("fbank_input.bin")
        val expected = readFloats("fbank_expected.bin")

        val actual = SpeechBrainFbankExtractor.fromFilterbank(filterbank).compute(input)

        assertEquals(expected.size, actual.size)
        var maxDelta = 0f
        for (index in actual.indices) {
            maxDelta = maxOf(maxDelta, abs(actual[index] - expected[index]))
        }
        assertTrue("max delta $maxDelta exceeds 0.005 dB", maxDelta <= 0.005f)
    }

    private fun readFloats(name: String): FloatArray {
        val path = "/neural_speaker_filter/$name"
        val bytes = requireNotNull(javaClass.getResourceAsStream(path)) { "Missing fixture $path" }
            .use { it.readBytes() }
        require(bytes.size % Float.SIZE_BYTES == 0)
        return FloatArray(bytes.size / Float.SIZE_BYTES).also { output ->
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(output)
        }
    }
}
