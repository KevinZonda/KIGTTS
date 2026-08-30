package com.lhtstudio.kigtts.app.audio

import com.lhtstudio.kigtts.app.data.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeSynthesisConfigTest {
    @Test
    fun mapsPersistedPiperSettingsWithoutReplacingNoiseW() {
        val settings = UserPrefs.AppSettings(
            piperNoiseScale = 0.42f,
            piperLengthScale = 1.25f,
            piperNoiseW = 1.37f,
            piperSentenceSilence = 0.35f
        )

        val config = settings.toRealtimeSynthesisConfig()

        assertEquals(0.42f, config.noiseScale, 0f)
        assertEquals(1.25f, config.lengthScale, 0f)
        assertEquals(1.37f, config.noiseW, 0f)
        assertEquals(0.35f, config.sentenceSilenceSec, 0f)
    }
}
