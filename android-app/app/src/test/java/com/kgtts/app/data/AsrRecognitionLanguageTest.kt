package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AsrRecognitionLanguageTest {
    @Test
    fun unknownAndEmptyValuesFallBackToMandarin() {
        assertEquals(AsrRecognitionLanguage.MANDARIN, AsrRecognitionLanguage.normalize(null))
        assertEquals(AsrRecognitionLanguage.MANDARIN, AsrRecognitionLanguage.normalize(""))
        assertEquals(AsrRecognitionLanguage.MANDARIN, AsrRecognitionLanguage.normalize("unknown"))
    }

    @Test
    fun commonAliasesAreNormalized() {
        assertEquals(AsrRecognitionLanguage.MANDARIN, AsrRecognitionLanguage.normalize("zh-CN"))
        assertEquals(AsrRecognitionLanguage.CANTONESE, AsrRecognitionLanguage.normalize("zh-yue"))
        assertEquals(AsrRecognitionLanguage.ENGLISH, AsrRecognitionLanguage.normalize("en-US"))
        assertEquals(AsrRecognitionLanguage.JAPANESE, AsrRecognitionLanguage.normalize("jp"))
        assertEquals(AsrRecognitionLanguage.KOREAN, AsrRecognitionLanguage.normalize("kr"))
    }

    @Test
    fun allVisibleEntriesRemainStable() {
        assertEquals(
            listOf("zh", "auto", "yue", "en", "ja", "ko"),
            AsrRecognitionLanguage.entries
        )
    }
}
