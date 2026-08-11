package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class WidgetSubtitleItem(
    val text: String,
    val colorArgb: Int?
)

internal data class WidgetSubtitleGroupSnapshot(
    val id: Long,
    val title: String,
    val items: List<WidgetSubtitleItem>
)

internal data class WidgetSubtitleSnapshot(
    val selectedGroupId: Long,
    val groups: List<WidgetSubtitleGroupSnapshot>
)

internal data class WidgetCardSnapshot(
    val id: Long,
    val type: String,
    val title: String,
    val note: String,
    val themeColor: String,
    val link: String,
    val portraitImagePath: String,
    val landscapeImagePath: String
)

internal data class WidgetCardsSnapshot(
    val selectedIndex: Int,
    val cards: List<WidgetCardSnapshot>
)

/**
 * Cross-process widget data is intentionally file-backed. The isolated widget process never opens
 * UserPrefs/DataStore or starts any of the app's audio and overlay runtime.
 */
object WidgetSnapshotStore {
    private const val SNAPSHOT_DIR = "widget_snapshots"
    private const val SUBTITLE_FILE = "quick_subtitle.json"
    private const val CARDS_FILE = "quick_cards.json"

    fun publishQuickSubtitle(context: Context, rawConfig: String) {
        writeAtomically(context, SUBTITLE_FILE, rawConfig)
        requestProviderRefresh(context, QuickSubtitleWidgetProvider::class.java)
    }

    fun publishQuickCards(context: Context, rawConfig: String) {
        writeAtomically(context, CARDS_FILE, rawConfig)
        requestProviderRefresh(context, QuickCardWidgetProvider::class.java)
    }

    internal fun readQuickSubtitle(context: Context): WidgetSubtitleSnapshot? {
        val raw = read(context, SUBTITLE_FILE) ?: return null
        return parseQuickSubtitle(raw)
    }

    internal fun readQuickCards(context: Context): WidgetCardsSnapshot? {
        val raw = read(context, CARDS_FILE) ?: return null
        return parseQuickCards(raw)
    }

    internal fun parseQuickSubtitle(raw: String): WidgetSubtitleSnapshot? = runCatching {
        val root = JSONObject(raw)
        val groups = root.optJSONArray("groups") ?: JSONArray()
        if (groups.length() == 0) return@runCatching null
        val selectedId = root.optLong("selectedGroupId", Long.MIN_VALUE)
        val parsedGroups = buildList {
            for (index in 0 until groups.length()) {
                val group = groups.optJSONObject(index) ?: continue
                val itemArray = group.optJSONArray("items") ?: JSONArray()
                val colorArray = group.optJSONArray("itemColors")
                val items = buildList {
                    for (itemIndex in 0 until itemArray.length()) {
                        val text = itemArray.optString(itemIndex, "").trim()
                        if (text.isNotEmpty()) {
                            val rawColor = colorArray?.opt(itemIndex)
                            val color = when (rawColor) {
                                is Number -> rawColor.toLong().toInt()
                                is String -> parseColorOrNull(rawColor)
                                else -> null
                            }
                            add(WidgetSubtitleItem(text, color))
                        }
                    }
                }
                add(
                    WidgetSubtitleGroupSnapshot(
                        id = group.optLong("id", index + 1L),
                        title = group.optString("title", "快捷文本").ifBlank { "快捷文本" },
                        items = items
                    )
                )
            }
        }
        if (parsedGroups.isEmpty()) return@runCatching null
        WidgetSubtitleSnapshot(
            selectedGroupId = parsedGroups.firstOrNull { it.id == selectedId }?.id
                ?: parsedGroups.first().id,
            groups = parsedGroups
        )
    }.getOrNull()

    internal fun parseQuickCards(raw: String): WidgetCardsSnapshot? = runCatching {
        val root = JSONObject(raw)
        val cardsArray = root.optJSONArray("cards") ?: JSONArray()
        val cards = buildList {
            for (index in 0 until cardsArray.length()) {
                val card = cardsArray.optJSONObject(index) ?: continue
                add(
                    WidgetCardSnapshot(
                        id = card.optLong("id", index + 1L),
                        type = card.optString("type", "text"),
                        title = card.optString("title", "").trim(),
                        note = card.optString("note", "").trim(),
                        themeColor = card.optString("themeColor", "#038387"),
                        link = card.optString("link", "").trim(),
                        portraitImagePath = card.optString("portraitImagePath", ""),
                        landscapeImagePath = card.optString("landscapeImagePath", "")
                    )
                )
            }
        }
        WidgetCardsSnapshot(
            selectedIndex = root.optInt("selectedIndex", 0).coerceIn(0, cards.lastIndex.coerceAtLeast(0)),
            cards = cards
        )
    }.getOrNull()

    private fun writeAtomically(context: Context, name: String, value: String) {
        val directory = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
        val atomicFile = AtomicFile(File(directory, name))
        val stream = atomicFile.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun read(context: Context, name: String): String? = runCatching {
        val file = File(File(context.filesDir, SNAPSHOT_DIR), name)
        if (!file.isFile) return@runCatching null
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun requestProviderRefresh(context: Context, providerClass: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                setComponent(component)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }

    private fun parseColorOrNull(raw: String): Int? = runCatching {
        android.graphics.Color.parseColor(raw)
    }.getOrNull()
}
