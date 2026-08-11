package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultQuickSubtitlePresetsTest {
    @Test
    fun latinPlaceholderLettersAreSeparated() {
        val sentences = defaultQuickSubtitlePresetGroups().flatMap { it.items }

        assertFalse(sentences.any { "XX" in it })
        assertTrue(sentences.any { "你出的是 X X 吗？" in it })
        assertTrue(sentences.any { it.startsWith("X X 的摊位") })
    }
}
