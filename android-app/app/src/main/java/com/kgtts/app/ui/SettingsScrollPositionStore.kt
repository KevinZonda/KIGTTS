package com.lhtstudio.kigtts.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

internal class SettingsScrollPositionStore(
    initialPositions: Map<String, Int> = emptyMap()
) {
    private val positions = mutableMapOf<String, Int>().apply {
        putAll(initialPositions)
    }

    fun positionFor(key: String): Int = positions[key] ?: 0

    fun update(key: String, position: Int) {
        if (positions[key] != position) {
            positions[key] = position
        }
    }

    internal fun snapshot(): Map<String, Int> = positions.toMap()

    companion object {
        val Saver: Saver<SettingsScrollPositionStore, Any> = listSaver(
            save = { store ->
                store.snapshot().entries.flatMap { (key, position) ->
                    listOf(key, position)
                }
            },
            restore = { saved ->
                val restored = saved.chunked(2).associate { values ->
                    values[0] as String to values[1] as Int
                }
                SettingsScrollPositionStore(restored)
            }
        )
    }
}

@Composable
internal fun rememberSettingsScrollPositionStore(): SettingsScrollPositionStore =
    rememberSaveable(saver = SettingsScrollPositionStore.Saver) {
        SettingsScrollPositionStore()
    }

internal fun settingsScrollPositionKey(
    detailPage: SettingsDetailPage?,
    category: SettingsCategory
): String = detailPage?.let { "detail:${it.routeId}" } ?: "category:${category.name}"
