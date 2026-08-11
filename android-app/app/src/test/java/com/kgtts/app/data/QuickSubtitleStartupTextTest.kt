package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuickSubtitleStartupTextTest {
    @Test
    fun settingsDefaultDoesNotRestoreLastText() {
        val settings = UserPrefs.AppSettings()

        assertFalse(settings.quickSubtitleRestoreLastTextOnLaunch)
        assertEquals(
            UserPrefs.DEFAULT_QUICK_SUBTITLE_CLEARED_PLACEHOLDER,
            settings.quickSubtitleClearedPlaceholderText
        )
    }

    @Test
    fun startupUsesPlaceholderWhenRestoreIsDisabled() {
        assertEquals(
            "请稍等\n我正在输入",
            resolveQuickSubtitleStartupText(
                savedText = "上一条快捷文本",
                clearedPlaceholderText = "  请稍等\r\n我正在输入  ",
                restoreLastTextOnLaunch = false
            )
        )
    }

    @Test
    fun startupRestoresSavedTextWhenEnabled() {
        assertEquals(
            "上一条快捷文本",
            resolveQuickSubtitleStartupText(
                savedText = "  上一条快捷文本  ",
                clearedPlaceholderText = "请稍等",
                restoreLastTextOnLaunch = true
            )
        )
    }

    @Test
    fun blankValuesFallBackToDefaultPlaceholder() {
        assertEquals(
            UserPrefs.DEFAULT_QUICK_SUBTITLE_CLEARED_PLACEHOLDER,
            resolveQuickSubtitleStartupText(
                savedText = " ",
                clearedPlaceholderText = " ",
                restoreLastTextOnLaunch = true
            )
        )
    }

    @Test
    fun placeholderIsLimitedToSupportedLength() {
        val normalized = UserPrefs.normalizeQuickSubtitleClearedPlaceholder(
            "字".repeat(UserPrefs.QUICK_SUBTITLE_CLEARED_PLACEHOLDER_MAX_LENGTH + 20)
        )

        assertEquals(UserPrefs.QUICK_SUBTITLE_CLEARED_PLACEHOLDER_MAX_LENGTH, normalized.length)
    }
}
