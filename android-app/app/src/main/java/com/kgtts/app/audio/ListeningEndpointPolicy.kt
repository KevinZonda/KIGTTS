package com.lhtstudio.kigtts.app.audio

internal object ListeningEndpointPolicy {
    fun shouldFinalizeAfterSilence(
        listeningEnabled: Boolean,
        speechSeen: Boolean,
        trailingSilenceMs: Int,
        requiredSilenceMs: Int
    ): Boolean =
        listeningEnabled &&
            speechSeen &&
            trailingSilenceMs >= requiredSilenceMs.coerceAtLeast(1)

    fun shouldForceBoundary(
        listeningEnabled: Boolean,
        speechDetected: Boolean,
        windowSamples: Int,
        sampleRate: Int,
        maxSpeechDurationMs: Int,
        preRollSamples: Int
    ): Boolean {
        if (!listeningEnabled || !speechDetected || windowSamples <= 0 || sampleRate <= 0) {
            return false
        }
        val speechLimit = sampleRate.toLong() * maxSpeechDurationMs.coerceAtLeast(1) / 1000L
        val totalLimit = speechLimit + preRollSamples.coerceAtLeast(0)
        return windowSamples.toLong() >= totalLimit
    }
}
