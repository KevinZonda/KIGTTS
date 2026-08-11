package com.lhtstudio.kigtts.app.audio

internal object RecognitionAudioBoundary {
    private const val MIN_OVERLAP_SAMPLES = 32

    fun prependWithoutDuplicate(prefix: FloatArray, segment: FloatArray): FloatArray {
        if (prefix.isEmpty()) return segment
        if (segment.isEmpty()) return prefix
        val overlap = longestExactSuffixPrefixOverlap(prefix, segment)
            .takeIf { it >= MIN_OVERLAP_SAMPLES }
            ?: 0
        val uniquePrefixSize = prefix.size - overlap
        val out = FloatArray(uniquePrefixSize + segment.size)
        if (uniquePrefixSize > 0) {
            System.arraycopy(prefix, 0, out, 0, uniquePrefixSize)
        }
        System.arraycopy(segment, 0, out, uniquePrefixSize, segment.size)
        return out
    }

    private fun longestExactSuffixPrefixOverlap(prefix: FloatArray, segment: FloatArray): Int {
        val maximum = minOf(prefix.size, segment.size)
        val pattern = IntArray(maximum) { index -> segment[index].toBits() }
        val failure = IntArray(maximum)
        var matched = 0
        for (index in 1 until maximum) {
            while (matched > 0 && pattern[index] != pattern[matched]) {
                matched = failure[matched - 1]
            }
            if (pattern[index] == pattern[matched]) matched++
            failure[index] = matched
        }

        matched = 0
        val start = prefix.size - maximum
        for (index in start until prefix.size) {
            val value = prefix[index].toBits()
            while (matched > 0 && value != pattern[matched]) {
                matched = failure[matched - 1]
            }
            if (value == pattern[matched]) matched++
            if (matched == maximum && index != prefix.lastIndex) {
                matched = failure[matched - 1]
            }
        }
        return matched
    }
}
