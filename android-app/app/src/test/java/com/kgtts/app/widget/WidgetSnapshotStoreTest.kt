package com.lhtstudio.kigtts.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotStoreTest {
    @Test
    fun quickSubtitleKeepsAllGroupsSelectedGroupAndItemColors() {
        val snapshot = WidgetSnapshotStore.parseQuickSubtitle(
            """
            {
              "selectedGroupId": 2,
              "groups": [
                {"id": 1, "title": "通用", "items": ["你好"]},
                {
                  "id": 2,
                  "title": "拍照",
                  "items": ["可以合影", "拍好了吗"],
                  "itemColors": [-65536, null]
                }
              ]
            }
            """.trimIndent()
        )

        requireNotNull(snapshot)
        assertEquals(2L, snapshot.selectedGroupId)
        assertEquals(listOf("通用", "拍照"), snapshot.groups.map { it.title })
        val photo = snapshot.groups[1]
        assertEquals(listOf("可以合影", "拍好了吗"), photo.items.map { it.text })
        assertEquals(-65536, photo.items[0].colorArgb)
        assertNull(photo.items[1].colorArgb)
    }

    @Test
    fun quickCardsPreserveSelectedIndexAndRoutingId() {
        val snapshot = WidgetSnapshotStore.parseQuickCards(
            """
            {
              "selectedIndex": 1,
              "cards": [
                {"id": 7, "type": "text", "title": "第一张"},
                {"id": 9, "type": "qr", "title": "第二张", "link": "https://kigtts.lhtstudio.com"}
              ]
            }
            """.trimIndent()
        )

        requireNotNull(snapshot)
        assertEquals(1, snapshot.selectedIndex)
        assertEquals(listOf(7L, 9L), snapshot.cards.map { it.id })
        assertEquals("qr", snapshot.cards[1].type)
    }

    @Test
    fun malformedSnapshotDoesNotCrashWidgetProcess() {
        assertNull(WidgetSnapshotStore.parseQuickSubtitle("not-json"))
        assertNull(WidgetSnapshotStore.parseQuickCards("not-json"))
    }
}
