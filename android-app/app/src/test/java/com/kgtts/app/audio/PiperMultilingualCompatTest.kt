package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiperMultilingualCompatTest {
    private val limitedIdMap = listOf(
        " ", "a", "e", "f", "h", "i", "j", "k", "l", "n", "o", "p", "r", "s", "t", "u", "w", "z",
        "ɑ", "ə", "ɛ", "ɜ", "ɕ", "ŋ", "ʂ", "ʐ", "ˈ"
    ).associateWith { listOf(1) }

    private val fullPiperIdMap = (
        limitedIdMap.keys + listOf("d", "ɐ", "ʊ", "ʌ", "ˌ", "ː", "̞", "̈")
    ).associateWith { listOf(1) }

    @Test
    fun segmentsChineseEnglishJapaneseAndKorean() {
        val segments = PiperMultilingualCompat.segment("你好 hello 日本語こんにちは 안녕하세요")

        assertEquals(
            listOf(
                PiperTextLanguage.BASE,
                PiperTextLanguage.ENGLISH,
                PiperTextLanguage.JAPANESE,
                PiperTextLanguage.KOREAN
            ),
            segments.map(PiperTextSegment::language)
        )
    }

    @Test
    fun convertsJapaneseKanjiToKanaBeforeEspeak() {
        val index = JapaneseReadingIndex.fromBytes(
            "#KIGTTS-JA-READING-1\t2\t3\n日本\tニホン\n日本語\tニホンゴ\n".toByteArray()
        )
        val prepared = PiperMultilingualCompat.prepareText(
            PiperTextSegment(PiperTextLanguage.JAPANESE, "日本語こんにちは"),
            index::toKana
        )

        assertFalse(prepared.codePoints().anyMatch {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        })
        assertTrue(prepared.contains("ニホンゴ"))
    }

    @Test
    fun preservesEnglishPhonesAlreadySupportedByPiper() {
        val raw = "həlˈoʊ wˈɜːld".codePoints().toArray().map { String(Character.toChars(it)) }

        val adapted = PiperMultilingualCompat.adaptPhones(raw, PiperTextLanguage.ENGLISH, fullPiperIdMap)

        assertEquals("həlˈoʊ wˈɜːld", adapted.joinToString(""))
    }

    @Test
    fun approximatesJapaneseAndRemovesUnsupportedDiacritics() {
        val raw = "kˌo̞nnitɕˈihä".codePoints().toArray().map { String(Character.toChars(it)) }

        val adapted = PiperMultilingualCompat.adaptPhones(raw, PiperTextLanguage.JAPANESE, fullPiperIdMap)

        assertEquals("kˌo̞nnitɕˈihä", adapted.joinToString(""))
    }

    @Test
    fun approximatesKoreanPhonesForMandarinModel() {
        val raw = "ˈɐnnjʌŋhˌɐsejo".codePoints().toArray().map { String(Character.toChars(it)) }

        val adapted = PiperMultilingualCompat.adaptPhones(raw, PiperTextLanguage.KOREAN, fullPiperIdMap)

        assertEquals("ˈɐnnjʌŋhˌɐsejo", adapted.joinToString(""))
    }

    @Test
    fun onlyApproximatesPhonesMissingFromTheVoiceMap() {
        val raw = "bʊd".codePoints().toArray().map { String(Character.toChars(it)) }

        val adapted = PiperMultilingualCompat.adaptPhones(
            raw,
            PiperTextLanguage.ENGLISH,
            limitedIdMap
        )

        assertEquals("put", adapted.joinToString(""))
    }

    @Test
    fun onlyEnablesAutomaticRoutingForChineseVoices() {
        assertTrue(PiperMultilingualCompat.supports("cmn"))
        assertTrue(PiperMultilingualCompat.supports("zh_CN"))
        assertFalse(PiperMultilingualCompat.supports("en-us"))
    }

    @Test
    fun keepsChineseTextOnLegacyPhonemizerPath() {
        assertFalse(PiperMultilingualCompat.requiresRouting("你好，今天是二〇二六年八月十日。"))
        assertFalse(PiperMultilingualCompat.requiresRouting("两万五千四百九十九亿元"))
        assertFalse(PiperMultilingualCompat.requiresRouting("你好 12345！"))
        assertFalse(
            PiperMultilingualCompat.requiresRouting(
                "太阳当空照，花儿对我笑，鸟儿说，早早早，你为什么背着炸药包"
            )
        )
        assertTrue(PiperMultilingualCompat.requiresRouting("你好 KIGTTS"))
        assertTrue(PiperMultilingualCompat.requiresRouting("日本語こんにちは"))
        assertTrue(PiperMultilingualCompat.requiresRouting("안녕하세요"))
    }
}
