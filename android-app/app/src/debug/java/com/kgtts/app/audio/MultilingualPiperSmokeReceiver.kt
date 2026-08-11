package com.lhtstudio.kigtts.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.lhtstudio.kigtts.app.util.AppLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** Debug-only, silent synthesis check for the multilingual compatibility path. */
class MultilingualPiperSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN) return
        val pendingResult = goAsync()
        thread(name = "multilingual-piper-smoke") {
            val report = mutableListOf<String>()
            try {
                runSmoke(context.applicationContext, report)
                report += "RESULT=PASS"
            } catch (t: Throwable) {
                report += "RESULT=FAIL"
                report += "ERROR=${t.stackTraceToString()}"
                AppLogger.e("MULTILINGUAL_PIPER_SMOKE failed", t)
            } finally {
                File(context.cacheDir, REPORT_FILE_NAME).writeText(
                    report.joinToString(System.lineSeparator()),
                    Charsets.UTF_8
                )
                report.forEach { AppLogger.i("MULTILINGUAL_PIPER_SMOKE $it") }
                pendingResult.finish()
            }
        }
    }

    private fun runSmoke(context: Context, report: MutableList<String>) {
        val voiceRoot = File(context.filesDir, "models/voice")
        val selected = voiceRoot.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                runCatching { directory to PiperVoicePack(directory).pack }.getOrNull()
            }
            ?.firstOrNull { (_, pack) ->
                pack.phonemeType.contains("espeak") && PiperMultilingualCompat.supports(
                    pack.espeakVoice.ifBlank { pack.languageCode }
                )
            }
            ?: error("No installed Chinese eSpeak Piper voice was found")

        report += "VOICE=${selected.first.name}"
        val engine = PiperTtsEngine(context, selected.first)
        try {
            val chinesePinyin = ChinesePinyinDictionary(context).toPinyin(CHINESE_REGRESSION_TEXT)
                ?: error("Chinese pinyin preparation failed")
            report += "chinese-pinyin=$chinesePinyin"
            report += "chinese-phonemes=" +
                EspeakNative.phonemize(chinesePinyin, "cmn-Latn-pinyin")
            val englishIpa = EspeakNative.phonemize(CASES.first { it.first == "english" }.second, "en-us")
            report += "english-ipa=$englishIpa"
            report += "english-pinyin=" + ForeignChineseApproximation.englishIpaToPinyin(englishIpa)
            val japaneseKana = JapaneseReadingDictionary(context).toKana(
                CASES.first { it.first == "japanese" }.second
            )
            report += "japanese-kana=$japaneseKana"
            report += "japanese-pinyin=" + ForeignChineseApproximation.japaneseKanaToPinyin(japaneseKana)
            val koreanText = CASES.first { it.first == "korean" }.second
            val koreanIpa = EspeakNative.phonemize(koreanText, "ko")
            report += "korean-ipa=$koreanIpa"
            report += "korean-pinyin=" +
                ForeignChineseApproximation.koreanHangulToPinyin(koreanText)
            CASES.forEach { (name, text) ->
                val startedAt = SystemClock.elapsedRealtime()
                val samples = engine.synthesize(text, 0f)
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                require(samples.isNotEmpty() && samples.all(Float::isFinite)) {
                    "Invalid audio generated for $name"
                }
                writeWave(
                    File(context.cacheDir, "multilingual-piper-$name.wav"),
                    samples,
                    engine.sampleRate
                )
                report += "$name samples=${samples.size} rate=${engine.sampleRate} " +
                    "durationMs=${samples.size * 1000L / engine.sampleRate} " +
                    "rms=${rms(samples)} inferenceMs=$elapsedMs"
            }
            val clauseStartedAt = SystemClock.elapsedRealtime()
            val clauseSamples = synthesizeClauses(engine, CHINESE_REGRESSION_TEXT)
            writeWave(
                File(context.cacheDir, "multilingual-piper-chinese-regression-clause-split.wav"),
                clauseSamples,
                engine.sampleRate
            )
            report += "chinese-regression-clause-split samples=${clauseSamples.size} " +
                "rate=${engine.sampleRate} durationMs=${clauseSamples.size * 1000L / engine.sampleRate} " +
                "rms=${rms(clauseSamples)} inferenceMs=${SystemClock.elapsedRealtime() - clauseStartedAt}"
        } finally {
            engine.close()
        }
    }

    private fun synthesizeClauses(engine: PiperTtsEngine, text: String): FloatArray {
        val clauses = text.split('，').filter(String::isNotBlank)
        val silence = FloatArray((engine.sampleRate * 0.08f).toInt())
        val parts = clauses.mapIndexed { index, clause ->
            val audio = engine.synthesize(clause, 0f)
            if (index == clauses.lastIndex) audio else audio + silence
        }
        val output = FloatArray(parts.sumOf(FloatArray::size))
        var offset = 0
        parts.forEach { part ->
            part.copyInto(output, destinationOffset = offset)
            offset += part.size
        }
        return output
    }

    private fun writeWave(file: File, samples: FloatArray, sampleRate: Int) {
        val pcmBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmBytes)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes)
        samples.forEach { sample ->
            buffer.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        }
        file.writeBytes(buffer.array())
    }

    private fun rms(samples: FloatArray): Float {
        val meanSquare = samples.sumOf { it.toDouble() * it.toDouble() } / samples.size
        return sqrt(meanSquare).toFloat()
    }

    private companion object {
        const val ACTION_RUN = "com.lhtstudio.kigtts.app.action.RUN_MULTILINGUAL_PIPER_SMOKE"
        const val REPORT_FILE_NAME = "multilingual-piper-smoke.txt"
        const val CHINESE_REGRESSION_TEXT =
            "太阳当空照，花儿对我笑，鸟儿说，早早早，你为什么背着炸药包"
        val CASES = listOf(
            "chinese-regression" to CHINESE_REGRESSION_TEXT,
            "english" to "hello world, this is KIGTTS",
            "japanese" to "日本語こんにちは、ありがとうございます",
            "korean" to "안녕하세요 한국어입니다"
        )
    }
}
