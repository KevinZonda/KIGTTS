package com.lhtstudio.kigtts.app.data

import org.json.JSONObject

data class QuickSubtitleUsageStats(
    private val countsByGroup: Map<Long, Map<String, Int>> = emptyMap()
) {
    fun count(groupId: Long, text: String): Int =
        countsByGroup[groupId]?.get(normalizeText(text)) ?: 0

    fun increment(groupId: Long, text: String): QuickSubtitleUsageStats {
        val normalized = normalizeText(text)
        if (normalized.isEmpty()) return this
        val nextGroup = countsByGroup[groupId].orEmpty().toMutableMap()
        nextGroup[normalized] = (nextGroup[normalized] ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1
        return copy(countsByGroup = countsByGroup + (groupId to nextGroup))
    }

    fun sortedIndices(groupId: Long, items: List<String>): List<Int> =
        items.indices.sortedWith(
            compareByDescending<Int> { index -> count(groupId, items[index]) }
                .thenBy { index -> index }
        )

    internal fun toJson(): String {
        val root = JSONObject()
        countsByGroup.forEach { (groupId, counts) ->
            val groupObject = JSONObject()
            counts.forEach { (text, count) ->
                if (text.isNotEmpty() && count > 0) {
                    groupObject.put(text, count)
                }
            }
            if (groupObject.length() > 0) {
                root.put(groupId.toString(), groupObject)
            }
        }
        return root.toString()
    }

    companion object {
        internal fun fromJson(raw: String?): QuickSubtitleUsageStats {
            if (raw.isNullOrBlank()) return QuickSubtitleUsageStats()
            return runCatching {
                val root = JSONObject(raw)
                val groups = mutableMapOf<Long, Map<String, Int>>()
                val groupKeys = root.keys()
                while (groupKeys.hasNext()) {
                    val groupKey = groupKeys.next()
                    val groupId = groupKey.toLongOrNull() ?: continue
                    val groupObject = root.optJSONObject(groupKey) ?: continue
                    val counts = mutableMapOf<String, Int>()
                    val textKeys = groupObject.keys()
                    while (textKeys.hasNext()) {
                        val rawText = textKeys.next()
                        val text = normalizeText(rawText)
                        val count = groupObject.optInt(rawText, 0)
                        if (text.isNotEmpty() && count > 0) {
                            counts[text] = count
                        }
                    }
                    if (counts.isNotEmpty()) {
                        groups[groupId] = counts
                    }
                }
                QuickSubtitleUsageStats(groups)
            }.getOrDefault(QuickSubtitleUsageStats())
        }

        private fun normalizeText(text: String): String = text.trim()
    }
}
