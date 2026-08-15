package com.lhtstudio.kigtts.app.data

import kotlin.math.abs

internal const val SoundboardMinimumClipDurationMs = 100L

internal data class SoundboardClipRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

internal fun initialSoundboardClipRange(durationMs: Long): SoundboardClipRange {
    val safeDuration = durationMs.coerceAtLeast(0L)
    return SoundboardClipRange(startMs = 0L, endMs = safeDuration)
}

internal fun adjustSoundboardClipRange(
    previous: SoundboardClipRange,
    requestedStartMs: Long,
    requestedEndMs: Long,
    durationMs: Long,
    minimumDurationMs: Long = SoundboardMinimumClipDurationMs
): SoundboardClipRange {
    val safeDuration = durationMs.coerceAtLeast(0L)
    if (safeDuration == 0L) return initialSoundboardClipRange(0L)

    val minimumDuration = minimumDurationMs.coerceAtLeast(1L).coerceAtMost(safeDuration)
    val safePreviousStart = previous.startMs.coerceIn(0L, safeDuration - minimumDuration)
    val safePreviousEnd = previous.endMs.coerceIn(safePreviousStart + minimumDuration, safeDuration)
    val requestedStart = requestedStartMs.coerceIn(0L, safeDuration)
    val requestedEnd = requestedEndMs.coerceIn(0L, safeDuration)
    val startMoved = abs(requestedStart - safePreviousStart) >=
        abs(requestedEnd - safePreviousEnd)

    return if (startMoved) {
        val end = requestedEnd.coerceIn(minimumDuration, safeDuration)
        SoundboardClipRange(
            startMs = requestedStart.coerceIn(0L, end - minimumDuration),
            endMs = end
        )
    } else {
        val start = requestedStart.coerceIn(0L, safeDuration - minimumDuration)
        SoundboardClipRange(
            startMs = start,
            endMs = requestedEnd.coerceIn(start + minimumDuration, safeDuration)
        )
    }
}
