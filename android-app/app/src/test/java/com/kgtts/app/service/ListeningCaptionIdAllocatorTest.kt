package com.lhtstudio.kigtts.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ListeningCaptionIdAllocatorTest {
    @Test
    fun keepsUnusedEngineId() {
        val allocator = ListeningCaptionIdAllocator()

        assertEquals(3L, allocator.allocate(3L, listOf(1L, 2L)))
    }

    @Test
    fun fallbackAndDuplicateEngineIdsUseDistinctNegativeIds() {
        val allocator = ListeningCaptionIdAllocator()
        val fallback = allocator.allocate(0L, listOf(1L))
        val duplicateEngine = allocator.allocate(1L, listOf(1L, fallback))

        assertEquals(-1L, fallback)
        assertEquals(-2L, duplicateEngine)
        assertNotEquals(fallback, duplicateEngine)
    }
}
