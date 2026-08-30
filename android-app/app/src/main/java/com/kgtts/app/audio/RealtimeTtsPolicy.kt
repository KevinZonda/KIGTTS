package com.lhtstudio.kigtts.app.audio

internal object RealtimeTtsPolicy {
    fun requiresLoadedTts(
        ttsDisabled: Boolean,
        listeningModeEnabled: Boolean
    ): Boolean = !ttsDisabled && !listeningModeEnabled

    fun shouldSuppressAsrAutoSpeak(
        ttsDisabled: Boolean,
        pushToTalkMode: Boolean,
        pushToTalkConfirmInput: Boolean,
        ttsReady: Boolean = true
    ): Boolean =
        ttsDisabled ||
            (pushToTalkMode && pushToTalkConfirmInput) ||
            !ttsReady
}
