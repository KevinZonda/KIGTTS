package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitleUsageStatsTest {
    @Test
    fun higherUsageMovesEarlierAndTiesKeepManualOrder() {
        val stats = QuickSubtitleUsageStats()
            .increment(7L, "第三条")
            .increment(7L, "第二条")
            .increment(7L, "第二条")

        assertEquals(
            listOf(1, 2, 0),
            stats.sortedIndices(7L, listOf("第一条", "第二条", "第三条"))
        )
    }

    @Test
    fun usageIsIsolatedByGroup() {
        val stats = QuickSubtitleUsageStats()
            .increment(1L, "相同文本")
            .increment(1L, "相同文本")
            .increment(2L, "另一条")

        assertEquals(2, stats.count(1L, "相同文本"))
        assertEquals(0, stats.count(2L, "相同文本"))
        assertEquals(listOf(1, 0), stats.sortedIndices(2L, listOf("相同文本", "另一条")))
    }
}
