package com.lhtstudio.kigtts.app.data

import org.json.JSONArray
import org.json.JSONObject

data class DrawingPaletteEntry(
    val id: Long,
    val lightColorArgb: Int,
    val darkColorArgb: Int
) {
    fun normalized(idOverride: Long = id): DrawingPaletteEntry = copy(
        id = idOverride,
        lightColorArgb = opaqueColor(lightColorArgb),
        darkColorArgb = opaqueColor(darkColorArgb)
    )
}

data class DrawingPalette(
    val entries: List<DrawingPaletteEntry> = defaultDrawingPaletteEntries()
) {
    fun normalized(): DrawingPalette {
        val usedIds = mutableSetOf<Long>()
        var nextId = 1L
        val normalizedEntries = entries.take(MAX_ENTRIES).map { entry ->
            val requestedId = entry.id.takeIf { it in 1..MAX_ENTRY_ID && it !in usedIds }
            val resolvedId = requestedId ?: run {
                while (nextId in usedIds && nextId < MAX_ENTRY_ID) nextId += 1L
                nextId
            }
            usedIds += resolvedId
            nextId = (resolvedId + 1L).coerceAtMost(MAX_ENTRY_ID)
            entry.normalized(resolvedId)
        }
        return copy(entries = normalizedEntries)
    }

    companion object {
        const val MAX_ENTRIES = 24
        private const val MAX_ENTRY_ID = 1_000_000_000L
    }
}

internal fun defaultDrawingPaletteEntries(): List<DrawingPaletteEntry> = listOf(
    DrawingPaletteEntry(1L, 0xFF038387.toInt(), 0xFF7DE8EA.toInt()),
    DrawingPaletteEntry(2L, 0xFF1E88E5.toInt(), 0xFF90CAF9.toInt()),
    DrawingPaletteEntry(3L, 0xFFE53935.toInt(), 0xFFFF9E9E.toInt()),
    DrawingPaletteEntry(4L, 0xFF43A047.toInt(), 0xFFAEE5B3.toInt()),
    DrawingPaletteEntry(5L, 0xFFFFA000.toInt(), 0xFFFFE08A.toInt()),
    DrawingPaletteEntry(6L, 0xFF212121.toInt(), 0xFFECEFF1.toInt())
)

internal fun nextDrawingPaletteEntryId(entries: List<DrawingPaletteEntry>): Long {
    val usedIds = entries.mapTo(mutableSetOf()) { it.id }
    return generateSequence(1L) { current -> current + 1L }
        .first { it !in usedIds }
}

internal fun resolveDefaultDrawingColorArgb(
    palette: DrawingPalette,
    darkTheme: Boolean,
    themeColorArgb: Int
): Int {
    val first = palette.entries.firstOrNull()
    val color = when {
        first == null -> themeColorArgb
        darkTheme -> first.darkColorArgb
        else -> first.lightColorArgb
    }
    return opaqueColor(color)
}

internal fun encodeDrawingPalette(palette: DrawingPalette): String {
    val normalized = palette.normalized()
    return JSONObject().apply {
        put("version", 1)
        put("entries", JSONArray().apply {
            normalized.entries.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("lightColorArgb", entry.lightColorArgb)
                    put("darkColorArgb", entry.darkColorArgb)
                })
            }
        })
    }.toString()
}

internal fun decodeDrawingPalette(raw: String?): DrawingPalette {
    if (raw.isNullOrBlank()) return DrawingPalette()
    return runCatching {
        val json = JSONObject(raw)
        val entriesJson = json.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until entriesJson.length()) {
                val entry = entriesJson.optJSONObject(index) ?: continue
                add(
                    DrawingPaletteEntry(
                        id = entry.optLong("id", (index + 1).toLong()),
                        lightColorArgb = entry.optInt("lightColorArgb", 0xFF038387.toInt()),
                        darkColorArgb = entry.optInt("darkColorArgb", 0xFF7DE8EA.toInt())
                    )
                )
            }
        }
        DrawingPalette(entries).normalized()
    }.getOrDefault(DrawingPalette())
}

private fun opaqueColor(color: Int): Int = color or (0xFF shl 24)
