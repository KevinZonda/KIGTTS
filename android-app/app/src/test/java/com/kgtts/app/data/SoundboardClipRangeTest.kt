package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundboardClipRangeTest {
    @Test
    fun `initial range uses the real duration including sub-second clips`() {
        assertEquals(
            SoundboardClipRange(startMs = 0L, endMs = 420L),
            initialSoundboardClipRange(420L)
        )
    }

    @Test
    fun `moving start thumb cannot cross the minimum clip boundary`() {
        val adjusted = adjustSoundboardClipRange(
            previous = SoundboardClipRange(0L, 1_000L),
            requestedStartMs = 980L,
            requestedEndMs = 1_000L,
            durationMs = 1_000L
        )

        assertEquals(SoundboardClipRange(900L, 1_000L), adjusted)
    }

    @Test
    fun `moving end thumb cannot cross the minimum clip boundary`() {
        val adjusted = adjustSoundboardClipRange(
            previous = SoundboardClipRange(400L, 1_000L),
            requestedStartMs = 400L,
            requestedEndMs = 420L,
            durationMs = 1_000L
        )

        assertEquals(SoundboardClipRange(400L, 500L), adjusted)
    }

    @Test
    fun `clips shorter than minimum duration remain fully selectable`() {
        val adjusted = adjustSoundboardClipRange(
            previous = SoundboardClipRange(0L, 60L),
            requestedStartMs = 40L,
            requestedEndMs = 60L,
            durationMs = 60L
        )

        assertEquals(SoundboardClipRange(0L, 60L), adjusted)
    }

    @Test
    fun `unknown duration returns an empty disabled range`() {
        val adjusted = adjustSoundboardClipRange(
            previous = SoundboardClipRange(0L, 1_000L),
            requestedStartMs = 500L,
            requestedEndMs = 1_000L,
            durationMs = 0L
        )

        assertEquals(SoundboardClipRange(0L, 0L), adjusted)
    }
}
