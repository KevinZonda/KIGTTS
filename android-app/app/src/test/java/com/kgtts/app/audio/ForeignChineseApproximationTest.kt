package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignChineseApproximationTest {
    @Test
    fun approximatesEnglishIpaAsMandarinSyllables() {
        val pinyin = ForeignChineseApproximation.englishIpaToPinyin(
            "həlˈoʊ wˈɜːld, ðˈɪs ˈɪz kˈɪɡts"
        )

        assertTrue(pinyin, pinyin.contains("he5 lou1"))
        assertTrue(pinyin, pinyin.contains("wo1 er5 de5"))
        assertTrue(pinyin, pinyin.contains("di1 si5"))
        assertTrue(pinyin, pinyin.contains("ki1 ge5 te5 si5"))
        assertFalse(pinyin.any { it in "əɜɪʊʌæɔ" })
    }

    @Test
    fun approximatesJapaneseKanaAsMandarinSyllables() {
        val pinyin = ForeignChineseApproximation.japaneseKanaToPinyin(
            "ニホンゴこんにちは、ありがとうございます"
        )

        assertTrue(pinyin.startsWith("ni1 hong1 guo1 kong1 ni1 qi1 wa1"))
        assertTrue(pinyin.contains("a1 li1 ga1 tuo1"))
        assertFalse(pinyin.any { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HIRAGANA ||
                Character.UnicodeScript.of(char.code) == Character.UnicodeScript.KATAKANA
        })
    }

    @Test
    fun approximatesKoreanHangulAsMandarinSyllables() {
        val pinyin = ForeignChineseApproximation.koreanHangulToPinyin(
            "안녕하세요 한국어입니다. 사랑해요"
        )

        assertTrue(pinyin, pinyin.startsWith("a1 ni1 ha1 sai1 you1 han2 gu1 ge1 yi1 mi1 da1"))
        assertTrue(pinyin, pinyin.contains("sa1 lang1 hei1 you1"))
        assertFalse(pinyin.any { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HANGUL
        })
    }

    @Test
    fun decomposesUnlistedKoreanSyllablesWithoutLeavingHangul() {
        val pinyin = ForeignChineseApproximation.koreanHangulToPinyin("봄날 좋아요")

        assertTrue(pinyin, pinyin.isNotBlank())
        assertFalse(pinyin.any { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HANGUL
        })
    }
}
