package com.lhtstudio.kigtts.app.audio

import com.lhtstudio.kigtts.app.data.UserPrefs

internal data class RealtimeSynthesisConfig(
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
    val sentenceSilenceSec: Float
)

internal fun UserPrefs.AppSettings.toRealtimeSynthesisConfig(): RealtimeSynthesisConfig =
    RealtimeSynthesisConfig(
        noiseScale = piperNoiseScale,
        lengthScale = piperLengthScale,
        noiseW = piperNoiseW,
        sentenceSilenceSec = piperSentenceSilence
    )
