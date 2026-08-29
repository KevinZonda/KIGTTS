package com.lhtstudio.kigtts.app.audio

internal object RealtimeTtsPolicy {
    fun requiresLoadedTts(
        ttsDisabled: Boolean,
        listeningModeEnabled: Boolean
    ): Boolean = !ttsDisabled && !listeningModeEnabled
}
