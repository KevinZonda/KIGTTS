package com.lhtstudio.kigtts.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ChinesePinyinDictionaryTest {
    @Test
    fun convertsChinesePhraseWithoutUnicodeFallback() {
        val index = ChinesePinyinIndex.fromBytes(
            (
                "#KIGTTS-ZH-PINYIN-1\t8\t3\n" +
                    "为什么\twei4 shen2 me5\n" +
                    "你\tni3\n" +
                    "儿\ter2\n" +
                    "太阳\ttai4 yang2\n" +
                    "对\tdui4\n" +
                    "花\thua1\n" +
                    "说\tshuo1\n" +
                    "鸟\tniao3\n"
            ).toByteArray()
        )

        assertEquals(
            "tai4 yang2, hua1 er2 dui4 ni3, niao3 er2 shuo1, wei4 shen2 me5?",
            index.toPinyin("太阳，花儿对你，鸟儿说，为什么？")
        )
    }

    @Test
    fun returnsNullWhenTextHasNoKnownChineseReading() {
        val index = ChinesePinyinIndex.fromBytes(
            "#KIGTTS-ZH-PINYIN-1\t1\t1\n你\tni3\n".toByteArray()
        )

        assertEquals(null, index.toPinyin("123, hello"))
    }
}
