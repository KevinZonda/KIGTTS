package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RecognitionAudioBoundaryTest {
    @Test
    fun duplicatedVadOnsetIsKeptOnlyOnce() {
        val sharedOnset = FloatArray(64) { index -> (index + 1) / 100f }
        val prefix = floatArrayOf(-0.2f, -0.1f) + sharedOnset
        val segment = sharedOnset + floatArrayOf(0.8f, 0.9f)

        assertArrayEquals(
            floatArrayOf(-0.2f, -0.1f) + segment,
            RecognitionAudioBoundary.prependWithoutDuplicate(prefix, segment),
            0f
        )
    }

    @Test
    fun unrelatedPreRollIsPreserved() {
        val prefix = FloatArray(64) { index -> -0.5f + index / 1000f }
        val segment = FloatArray(64) { index -> 0.2f + index / 1000f }

        assertArrayEquals(
            prefix + segment,
            RecognitionAudioBoundary.prependWithoutDuplicate(prefix, segment),
            0f
        )
    }

    @Test
    fun tinyCoincidentalOverlapIsNotRemoved() {
        val shared = FloatArray(8) { index -> index / 10f }
        val prefix = floatArrayOf(-1f) + shared
        val segment = shared + floatArrayOf(1f)

        assertArrayEquals(
            prefix + segment,
            RecognitionAudioBoundary.prependWithoutDuplicate(prefix, segment),
            0f
        )
    }
}
