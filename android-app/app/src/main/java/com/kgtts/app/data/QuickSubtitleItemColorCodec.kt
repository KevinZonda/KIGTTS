package com.lhtstudio.kigtts.app.data

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

internal data class QuickSubtitleItemsPayload(
    val items: List<String>,
    val colors: List<Int?>
)

internal fun readQuickSubtitleItems(
    source: JSONObject,
    fallbackText: String = "请输入常用短句"
): QuickSubtitleItemsPayload {
    val itemsArray = source.optJSONArray("items") ?: JSONArray()
    val colorsArray = source.optJSONArray("itemColors")
    val items = mutableListOf<String>()
    val colors = mutableListOf<Int?>()
    for (index in 0 until itemsArray.length()) {
        val text = itemsArray.optString(index, "").trim()
        if (text.isEmpty()) continue
        items += text
        colors += colorsArray?.opt(index).toQuickSubtitleColorOrNull()
    }
    if (items.isEmpty()) {
        items += fallbackText
    }
    return QuickSubtitleItemsPayload(
        items = items,
        colors = colors.compactQuickSubtitleItemColors()
    )
}

internal fun writeQuickSubtitleItems(
    target: JSONObject,
    items: List<String>,
    colors: List<Int?>
) {
    target.put("items", JSONArray().apply { items.forEach(::put) })
    val compactColors = colors.alignedQuickSubtitleItemColors(items.size)
        .compactQuickSubtitleItemColors()
    if (compactColors.isNotEmpty()) {
        target.put(
            "itemColors",
            JSONArray().apply {
                compactColors.forEach { color -> put(color ?: JSONObject.NULL) }
            }
        )
    }
}

internal fun List<Int?>.alignedQuickSubtitleItemColors(itemCount: Int): List<Int?> =
    List(itemCount.coerceAtLeast(0)) { index -> getOrNull(index) }

internal fun List<Int?>.compactQuickSubtitleItemColors(): List<Int?> =
    dropLastWhile { it == null }

private fun Any?.toQuickSubtitleColorOrNull(): Int? = when (this) {
    null,
    JSONObject.NULL -> null
    is Number -> toLong().toInt()
    is String -> runCatching { Color.parseColor(trim()) }.getOrNull()
    else -> null
}
