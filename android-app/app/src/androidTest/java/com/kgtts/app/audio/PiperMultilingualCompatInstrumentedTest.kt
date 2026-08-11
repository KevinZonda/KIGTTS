package com.lhtstudio.kigtts.app.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lhtstudio.kigtts.app.data.EspeakData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PiperMultilingualCompatInstrumentedTest {
    @Test
    fun foreignLanguageRulesProduceUsablePhonesOnAndroid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataDir = requireNotNull(EspeakData.ensure(context))
        assertTrue(EspeakNative.ensureInit(dataDir.absolutePath))

        val english = EspeakNative.phonemize("hello world", "en-us")
        val japaneseText = PiperMultilingualCompat.prepareText(
            PiperTextSegment(PiperTextLanguage.JAPANESE, "日本語こんにちは"),
            JapaneseReadingDictionary(context)::toKana
        )
        val japanese = EspeakNative.phonemize(japaneseText, "ja")
        val korean = EspeakNative.phonemize("안녕하세요", "ko")

        assertTrue(english.isNotBlank())
        assertTrue(japanese.isNotBlank())
        assertTrue(korean.isNotBlank())
        assertFalse(japaneseText.codePoints().anyMatch {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        })
        assertFalse(japanese.contains("dʒˈapəniːz"))
    }

    @Test
    fun chinesePinyinFrontendAvoidsUnicodeLetterFallback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataDir = requireNotNull(EspeakData.ensure(context))
        assertTrue(EspeakNative.ensureInit(dataDir.absolutePath))
        val text = "太阳当空照，花儿对我笑，鸟儿说，早早早，你为什么背着炸药包"
        val pinyin = ChinesePinyinDictionary(context).toPinyin(text)

        assertNotNull(pinyin)
        val prepared = requireNotNull(pinyin)
        assertTrue(prepared.contains("hua1 er2"))
        assertTrue(prepared.contains("niao3 er2 shuo1"))
        assertTrue(prepared.contains("bei1 zhe5"))
        assertFalse(PiperMultilingualCompat.containsHan(prepared))

        val phonemes = EspeakNative.phonemize(prepared, "cmn-Latn-pinyin")
        assertTrue(phonemes.isNotBlank())
        assertFalse(phonemes.contains("fˈaɪv"))
        assertFalse(phonemes.contains("wˈɒn"))
    }

    @Test
    fun installedChinesePiperVoiceSynthesizesEnglishJapaneseAndKorean() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val voiceRoot = File(context.filesDir, "models/voice")
        val packDir = voiceRoot.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { dir ->
                runCatching { dir to PiperVoicePack(dir).pack }.getOrNull()
            }
            ?.firstOrNull { (_, pack) ->
                pack.phonemeType.contains("espeak") && PiperMultilingualCompat.supports(
                    pack.espeakVoice.ifBlank { pack.languageCode }
                )
            }
            ?.first
        assumeNotNull(packDir)

        val engine = PiperTtsEngine(context, requireNotNull(packDir))
        try {
            listOf(
                "hello world",
                "日本語こんにちは",
                "안녕하세요 한국어입니다"
            ).forEach { text ->
                val samples = engine.synthesize(text)
                assertTrue("No samples generated for: $text", samples.isNotEmpty())
                assertTrue("Invalid samples generated for: $text", samples.all(Float::isFinite))
            }
        } finally {
            engine.close()
        }
    }
}
