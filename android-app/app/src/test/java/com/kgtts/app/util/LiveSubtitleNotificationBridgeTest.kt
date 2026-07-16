package com.lhtstudio.kigtts.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSubtitleNotificationBridgeTest {
    @Test
    fun criticalTextIsLimitedToSevenCodePoints() {
        assertEquals("谢谢！太感谢了", liveSubtitleCriticalText("谢谢！太感谢了！"))
    }

    @Test
    fun criticalTextDoesNotSplitSupplementaryCharacters() {
        assertEquals("😀😀😀😀😀😀😀", liveSubtitleCriticalText("😀😀😀😀😀😀😀😀"))
    }

    @Test
    fun blankCriticalTextUsesFallback() {
        assertEquals("暂无字幕", liveSubtitleCriticalText("  "))
    }
}
