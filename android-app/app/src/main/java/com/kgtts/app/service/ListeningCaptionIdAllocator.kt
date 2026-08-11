package com.lhtstudio.kigtts.app.service

internal class ListeningCaptionIdAllocator {
    private var nextFallbackId = -1L

    fun allocate(preferredId: Long, existingIds: Collection<Long>): Long {
        if (preferredId > 0L && preferredId !in existingIds) return preferredId
        while (nextFallbackId in existingIds || nextFallbackId == Long.MAX_VALUE) {
            nextFallbackId--
        }
        return nextFallbackId--
    }
}
