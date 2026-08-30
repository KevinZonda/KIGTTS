package com.lhtstudio.kigtts.app.audio

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.k2fsa.sherpa.onnx.DenoisedAudio
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserDpdfNetModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.lhtstudio.kigtts.app.data.EspeakData
import com.lhtstudio.kigtts.app.lan.LanCastAudioBridge
import com.lhtstudio.kigtts.app.data.NeuralSpeakerFilterResourceRepository
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import com.lhtstudio.kigtts.app.data.isKokoroVoiceDir
import com.lhtstudio.kigtts.app.data.isSystemTtsVoiceDir
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale

object AudioRoutePreference {
    const val INPUT_AUTO = 0
    const val INPUT_BUILTIN_MIC = 1
    const val INPUT_USB = 2
    const val INPUT_BLUETOOTH = 3
    const val INPUT_WIRED = 4

    const val OUTPUT_AUTO = 100
    const val OUTPUT_SPEAKER = 101
    const val OUTPUT_EARPIECE = 102
    const val OUTPUT_BLUETOOTH = 103
    const val OUTPUT_USB = 104
    const val OUTPUT_WIRED = 105
}

interface AsrModule {
    val sampleRate: Int
    fun transcribe(samples: FloatArray, sr: Int): String
    fun close() {}
}

interface TtsModule {
    val sampleRate: Int
    fun synthesize(text: String): FloatArray
    fun synthesize(text: String, sentenceSilenceSec: Float): FloatArray = synthesize(text)
    fun close() {}
    fun setKokoroVoice(speakerId: Int) {}
    fun setSynthesisTuning(
        noiseScale: Float,
        lengthScale: Float,
        noiseW: Float,
        sentenceSilenceSec: Float
    ) {}
}

internal data class SimulatedAudioRunResult(
    val inputSamples: Int,
    val processedSamples: Int,
    val chunkCount: Int,
    val elapsedMs: Long
)

interface SpeechModuleFactory {
    fun createAsr(
        context: Context,
        modelDir: File,
        recognitionLanguage: String
    ): AsrModule
    fun createTts(context: Context, packDir: File): TtsModule
}

object DefaultSpeechModuleFactory : SpeechModuleFactory {
    override fun createAsr(
        context: Context,
        modelDir: File,
        recognitionLanguage: String
    ): AsrModule = AsrEngine(
        context,
        modelDir,
        recognitionLanguage
    )
    override fun createTts(context: Context, packDir: File): TtsModule {
        return when {
            isSystemTtsVoiceDir(packDir) -> SystemTtsEngine(context)
            isKokoroVoiceDir(packDir) -> SherpaKokoroTtsEngine(context, packDir)
            else -> PiperTtsEngine(context, packDir)
        }
    }
}

class AsrEngine(
    private val context: Context,
    private val modelDir: File,
    recognitionLanguage: String
) : AsrModule {
    private val recognizer: OfflineRecognizer
    override val sampleRate: Int = 16000

    init {
        val onnxFiles = modelDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "onnx" }
            .toList()
        val modelPath = chooseSenseVoiceModel(onnxFiles)
            ?: throw IllegalArgumentException("未在 ${modelDir.absolutePath} 找到 sensevoice onnx 模型")
        val lang = AsrRecognitionLanguage.normalize(recognitionLanguage)
        AppLogger.i("ASR init model=$modelPath lang=$lang builtInPunctuation=true")

        val feat = FeatureConfig().apply {
            sampleRate = this@AsrEngine.sampleRate
            featureDim = 80
            dither = 0f
        }
        val senseVoiceCfg = OfflineSenseVoiceModelConfig().apply {
            model = modelPath.absolutePath
            language = lang
            useInverseTextNormalization = true
        }
        val tokensPath = File(modelPath.parentFile, "tokens.txt")
        val modelCfg = OfflineModelConfig().apply {
            senseVoice = senseVoiceCfg
            if (tokensPath.exists()) {
                tokens = tokensPath.absolutePath
            }
            modelType = "sense_voice"
            numThreads = 2
            provider = "cpu"
        }
        val recCfg = OfflineRecognizerConfig().apply {
            featConfig = feat
            modelConfig = modelCfg
            decodingMethod = "greedy_search"
            maxActivePaths = 4
            blankPenalty = 0f
        }
        // Use filesystem paths (not assets) for extracted models.
        recognizer = OfflineRecognizer(null, recCfg)
    }

    private fun chooseSenseVoiceModel(onnxFiles: List<File>): File? {
        if (onnxFiles.isEmpty()) return null
        fun isSenseVoice(file: File): Boolean {
            val name = file.name.lowercase()
            if (name.contains("sensevoice")) return true
            val p1 = file.parentFile?.name?.lowercase().orEmpty()
            val p2 = file.parentFile?.parentFile?.name?.lowercase().orEmpty()
            return p1.contains("sensevoice") || p2.contains("sensevoice")
        }
        fun isAux(file: File): Boolean {
            val name = file.name.lowercase()
            return name.contains("punct") || name.contains("vad") || name.contains("silero")
        }
        return onnxFiles.firstOrNull { isSenseVoice(it) }
            ?: onnxFiles.firstOrNull { !isAux(it) }
            ?: onnxFiles.firstOrNull()
    }

    override fun transcribe(samples: FloatArray, sr: Int): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sr)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            runCatching { stream.release() }
        }
    }

    override fun close() {
        recognizer.release()
    }
}

data class VoicePack(
    val manifest: JSONObject,
    val modelPath: File,
    val configPath: File,
    val dictPath: File,
    val sampleRate: Int,
    val phonemeIdMap: Map<String, List<Int>>,
    val phonemeMap: Map<String, List<String>>,
    val phonemeType: String,
    val espeakVoice: String,
    val languageCode: String
)

class PiperVoicePack(private val dir: File) {
    val pack: VoicePack
    init {
        val manifestFile = File(dir, "manifest.json")
        val manifest = JSONObject(manifestFile.readText())
        val files = manifest.getJSONObject("files")
        val modelPath = File(dir, files.getString("model"))
        val configPath = File(dir, files.getString("config"))
        val dictPath = File(dir, files.getString("phonemizer"))
        val configJson = JSONObject(configPath.readText())
        val phonemeType = configJson.optString("phoneme_type", "text").lowercase()
        val espeakVoice = configJson.optJSONObject("espeak")?.optString("voice")?.trim().orEmpty()
        val languageCode = configJson.optJSONObject("language")?.optString("code")?.trim().orEmpty()
        val phonemeMap = configJson.getJSONObject("phoneme_id_map")
        val idMap = mutableMapOf<String, List<Int>>()
        phonemeMap.keys().forEach { key ->
            val raw = phonemeMap.get(key)
            val values = when (raw) {
                is org.json.JSONArray -> {
                    val list = mutableListOf<Int>()
                    for (i in 0 until raw.length()) {
                        list.add(raw.getInt(i))
                    }
                    list
                }
                is Number -> listOf(raw.toInt())
                else -> emptyList()
            }
            if (values.isNotEmpty()) {
                idMap[key] = values
            }
        }
        val rawPhoneMap = configJson.optJSONObject("phoneme_map")
        val phoneMap = mutableMapOf<String, List<String>>()
        rawPhoneMap?.keys()?.forEach { key ->
            val raw = rawPhoneMap.get(key)
            val values = when (raw) {
                is org.json.JSONArray -> {
                    val list = mutableListOf<String>()
                    for (i in 0 until raw.length()) {
                        list.add(raw.getString(i))
                    }
                    list
                }
                is String -> listOf(raw)
                else -> emptyList()
            }
            if (values.isNotEmpty()) {
                phoneMap[key] = values
            }
        }
        val sr = manifest.optInt("sample_rate", configJson.optInt("sample_rate", 22050))
        AppLogger.i("VoicePack init dir=${dir.absolutePath} model=${modelPath.absolutePath} sr=$sr")
        pack = VoicePack(
            manifest = manifest,
            modelPath = modelPath,
            configPath = configPath,
            dictPath = dictPath,
            sampleRate = sr,
            phonemeIdMap = idMap,
            phonemeMap = phoneMap,
            phonemeType = phonemeType,
            espeakVoice = espeakVoice,
            languageCode = languageCode
        )
    }
}

object EspeakNative {
    private var loaded = false
    private var initialized = false

    init {
        try {
            System.loadLibrary("espeak_jni")
            loaded = true
        } catch (e: Throwable) {
            Log.e("EspeakNative", "Failed to load espeak_jni", e)
        }
    }

    private external fun nativeInit(dataPath: String): Boolean
    private external fun nativePhonemize(text: String, voice: String): String

    fun ensureInit(dataPath: String): Boolean {
        if (!loaded) return false
        if (initialized) return true
        initialized = nativeInit(dataPath)
        return initialized
    }

    fun phonemize(text: String, voice: String): String {
        if (!loaded || !initialized) return ""
        return nativePhonemize(text, voice)
    }
}

private fun buildIds(phones: List<String>, idMap: Map<String, List<Int>>): IntArray {
    val ids = mutableListOf<Int>()
    val bos = idMap["^"] ?: emptyList()
    val eos = idMap["$"] ?: emptyList()
    val pad = idMap["_"] ?: emptyList()
    ids.addAll(bos)
    if (pad.isNotEmpty()) {
        ids.addAll(pad)
    }
    for (phone in phones) {
        val mapped = idMap[phone] ?: continue
        ids.addAll(mapped)
        if (pad.isNotEmpty()) {
            ids.addAll(pad)
        }
    }
    ids.addAll(eos)
    return ids.toIntArray()
}

class PiperPhonemizer(
    dictFile: File,
    private val idMap: Map<String, List<Int>>,
    private val phoneMap: Map<String, List<String>>
) {
    private val charToPhones: Map<String, List<String>> = loadDict(dictFile)
    private fun loadDict(file: File): Map<String, List<String>> {
        if (!file.exists()) return emptyMap()
        val map = mutableMapOf<String, List<String>>()
        file.useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    map[parts[0]] = parts.drop(1)
                }
            }
        }
        return map
    }

    private fun applyPhoneMap(phones: List<String>): List<String> {
        if (phoneMap.isEmpty()) return phones
        val out = mutableListOf<String>()
        for (phone in phones) {
            val mapped = phoneMap[phone]
            if (mapped != null && mapped.isNotEmpty()) {
                out.addAll(mapped)
            } else {
                out.add(phone)
            }
        }
        return out
    }

    fun toIds(text: String): IntArray {
        val phones = mutableListOf<String>()
        text.forEach { ch ->
            val key = ch.toString()
            val entry = charToPhones[key]
            if (entry != null) {
                phones.addAll(entry)
            } else {
                phones.add(key)
            }
        }
        val mappedPhones = applyPhoneMap(phones)
        return buildIds(mappedPhones, idMap)
    }
}

class EspeakPhonemizer(
    context: Context,
    private val dataDir: File,
    private val voice: String,
    private val idMap: Map<String, List<Int>>,
    private val phoneMap: Map<String, List<String>>
) {
    private val japaneseReadingDictionary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        JapaneseReadingDictionary(context)
    }
    private val chinesePinyinDictionary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChinesePinyinDictionary(context)
    }

    init {
        if (!EspeakNative.ensureInit(dataDir.absolutePath)) {
            throw IllegalStateException("espeak-ng 初始化失败")
        }
    }

    private fun applyPhoneMap(phones: List<String>): List<String> {
        if (phoneMap.isEmpty()) return phones
        val out = mutableListOf<String>()
        for (phone in phones) {
            val mapped = phoneMap[phone]
            if (mapped != null && mapped.isNotEmpty()) {
                out.addAll(mapped)
            } else {
                out.add(phone)
            }
        }
        return out
    }

    private fun legacyToIds(text: String): IntArray {
        val phonemes = EspeakNative.phonemize(text, voice)
        if (phonemes.isBlank()) return IntArray(0)
        val phones = phonemes.codePoints().toArray().map { codePoint ->
            String(Character.toChars(codePoint))
        }
        return buildIds(applyPhoneMap(phones), idMap)
    }

    fun toIds(text: String): IntArray {
        if (!PiperMultilingualCompat.supports(voice)) {
            return legacyToIds(text)
        }
        val requiresForeignRouting = PiperMultilingualCompat.requiresRouting(text)
        val containsChinese = PiperMultilingualCompat.containsHan(text)
        if (!requiresForeignRouting && !containsChinese) return legacyToIds(text)

        val phones = mutableListOf<String>()
        val segments = if (requiresForeignRouting) {
            PiperMultilingualCompat.segment(text)
        } else {
            listOf(PiperTextSegment(PiperTextLanguage.BASE, text))
        }
        for (segment in segments) {
            val chinesePinyin = if (
                segment.language == PiperTextLanguage.BASE &&
                PiperMultilingualCompat.containsHan(segment.text)
            ) {
                chinesePinyinDictionary.toPinyin(segment.text)
            } else {
                null
            }
            val directText = PiperMultilingualCompat.prepareText(segment) {
                japaneseReadingDictionary.toKana(it)
            }
            val directVoice = segment.language.espeakVoice ?: voice
            val foreignPinyin = when (segment.language) {
                PiperTextLanguage.ENGLISH -> {
                    val ipa = EspeakNative.phonemize(directText, directVoice)
                    ForeignChineseApproximation.englishIpaToPinyin(ipa).ifBlank { null }
                }
                PiperTextLanguage.JAPANESE -> {
                    ForeignChineseApproximation.japaneseKanaToPinyin(directText).ifBlank { null }
                }
                PiperTextLanguage.KOREAN -> {
                    ForeignChineseApproximation.koreanHangulToPinyin(directText).ifBlank { null }
                }
                else -> null
            }
            val approximatePinyin = chinesePinyin ?: foreignPinyin
            var adaptationLanguage = if (approximatePinyin != null) {
                PiperTextLanguage.BASE
            } else {
                segment.language
            }
            var phonemes = EspeakNative.phonemize(
                approximatePinyin ?: directText,
                if (approximatePinyin != null) CHINESE_PINYIN_VOICE else directVoice
            )
            if (phonemes.isBlank() && approximatePinyin != null) {
                adaptationLanguage = segment.language
                phonemes = EspeakNative.phonemize(directText, directVoice)
            }
            if (phonemes.isBlank() && segment.language != PiperTextLanguage.BASE) {
                adaptationLanguage = segment.language
                phonemes = EspeakNative.phonemize(directText, voice)
            }
            if (phonemes.isBlank()) continue
            val rawPhones = phonemes.codePoints().toArray().map { cp ->
                String(Character.toChars(cp))
            }
            val modelMappedPhones = applyPhoneMap(rawPhones)
            val adaptedPhones = PiperMultilingualCompat.adaptPhones(
                modelMappedPhones,
                adaptationLanguage,
                idMap
            )
            if (phones.isNotEmpty() && adaptedPhones.isNotEmpty() && phones.last() != " ") {
                phones.add(" ")
            }
            phones.addAll(adaptedPhones)
        }
        return buildIds(phones, idMap)
    }

    private companion object {
        const val CHINESE_PINYIN_VOICE = "cmn-Latn-pinyin"
    }
}

class PiperTtsEngine(context: Context, packDir: File) : TtsModule {
    private val voicePack = PiperVoicePack(packDir).pack
    private val toIds: (String) -> IntArray
    init {
        toIds = if (voicePack.phonemeType.contains("espeak")) {
            val dataDir = EspeakData.ensure(context)
                ?: throw IllegalStateException("未找到 espeak-ng 数据")
            val voiceName = voicePack.espeakVoice
                .ifBlank { voicePack.languageCode }
                .ifBlank { "en-us" }
            val phonemizer = EspeakPhonemizer(
                context,
                dataDir,
                voiceName,
                voicePack.phonemeIdMap,
                voicePack.phonemeMap
            )
            phonemizer::toIds
        } else {
            val phonemizer = PiperPhonemizer(
                voicePack.dictPath,
                voicePack.phonemeIdMap,
                voicePack.phonemeMap
            )
            phonemizer::toIds
        }
    }
    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1)
        setInterOpNumThreads(1)
    }
    private val session: OrtSession = env.createSession(voicePack.modelPath.absolutePath, sessionOptions)
    override val sampleRate: Int = voicePack.sampleRate
    @Volatile private var noiseScale: Float = 0.667f
    @Volatile private var lengthScale: Float = 1.0f
    @Volatile private var noiseW: Float = 0.8f
    @Volatile private var sentenceSilenceSec: Float = 0.0f

    override fun setSynthesisTuning(
        noiseScale: Float,
        lengthScale: Float,
        noiseW: Float,
        sentenceSilenceSec: Float
    ) {
        this.noiseScale = noiseScale.coerceIn(0f, 2f)
        this.lengthScale = lengthScale.coerceIn(0.1f, 5f)
        this.noiseW = noiseW.coerceIn(0.3f, 1.5f)
        this.sentenceSilenceSec = sentenceSilenceSec.coerceIn(0f, 2f)
    }

    override fun synthesize(text: String): FloatArray {
        return synthesizeInternal(text, null)
    }

    override fun synthesize(text: String, sentenceSilenceSec: Float): FloatArray {
        return synthesizeInternal(text, sentenceSilenceSec.coerceAtLeast(0f))
    }

    private fun synthesizeInternal(text: String, sentenceSilenceOverride: Float?): FloatArray {
        val ids = toIds(text)
        if (ids.isEmpty()) return FloatArray(0)
        val currentNoiseScale = noiseScale
        val currentLengthScale = lengthScale
        val currentNoiseW = noiseW
        val currentSentenceSilenceSec = sentenceSilenceOverride ?: sentenceSilenceSec
        val inputs = mutableMapOf<String, OnnxTensor>()
        val idLong = ids.map { it.toLong() }.toLongArray()
        val inputName = session.inputNames.firstOrNull { it.contains("input") } ?: session.inputNames.first()
        val lenName = session.inputNames.firstOrNull { it.contains("len") || it.contains("length") } ?: inputName + "_length"
        inputs[inputName] = OnnxTensor.createTensor(env, LongBuffer.wrap(idLong), longArrayOf(1, idLong.size.toLong()))
        inputs[lenName] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(idLong.size.toLong())), longArrayOf(1))

        val scaleName = session.inputNames.firstOrNull { it.contains("scale") }
        if (scaleName != null) {
            val scales = FloatBuffer.wrap(
                floatArrayOf(currentNoiseScale, currentLengthScale, currentNoiseW)
            )
            inputs[scaleName] = OnnxTensor.createTensor(env, scales, longArrayOf(3))
        }
        val sidName = session.inputNames.firstOrNull { it.contains("sid") }
        if (sidName != null) {
            inputs[sidName] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
        }

        try {
            session.run(inputs).use { results ->
                val raw = unwrapAudio(results[0].value)
                return appendSentenceSilence(raw, currentSentenceSilenceSec)
            }
        } finally {
            inputs.values.forEach { tensor -> runCatching { tensor.close() } }
        }
    }

    private fun unwrapAudio(value: Any?): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                if (value.isNotEmpty()) unwrapAudio(value[0]) else FloatArray(0)
            }
            else -> FloatArray(0)
        }
    }

    private fun appendSentenceSilence(samples: FloatArray, sec: Float): FloatArray {
        val silenceSec = sec.coerceAtLeast(0f)
        if (silenceSec <= 0f || samples.isEmpty()) return samples
        val silenceSamples = (sampleRate * silenceSec).roundToInt()
        if (silenceSamples <= 0) return samples
        val out = FloatArray(samples.size + silenceSamples)
        System.arraycopy(samples, 0, out, 0, samples.size)
        return out
    }

    override fun close() {
        runCatching { session.close() }
        runCatching { sessionOptions.close() }
    }
}

class SherpaKokoroTtsEngine(@Suppress("UNUSED_PARAMETER") context: Context, packDir: File) : TtsModule {
    private val baseDir = resolveKokoroBaseDir(packDir)
    private val tts: OfflineTts
    @Volatile private var speakerId: Int = UserPrefs.KOKORO_DEFAULT_SPEAKER_ID
    @Volatile private var speed: Float = 1.0f
    @Volatile private var silenceScale: Float = 0.2f

    init {
        val modelFile = File(baseDir, "model.onnx").takeIf { it.isFile }
            ?: throw IllegalStateException("Kokoro 模型文件缺失：需要 model.onnx")
        val voicesFile = File(baseDir, "voices.bin").takeIf { it.isFile }
            ?: throw IllegalStateException("Kokoro voices.bin 缺失")
        val tokensFile = File(baseDir, "tokens.txt").takeIf { it.isFile }
            ?: throw IllegalStateException("Kokoro tokens.txt 缺失")
        val dataDir = File(baseDir, "espeak-ng-data").takeIf { it.isDirectory }
            ?: throw IllegalStateException("Kokoro espeak-ng-data 缺失")
        val lexicons = listOf("lexicon-us-en.txt", "lexicon-zh.txt")
            .map { File(baseDir, it) }
            .filter { it.isFile }
        if (lexicons.size < 2) throw IllegalStateException("Kokoro 中英文词典缺失")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = modelFile.absolutePath,
                    voices = voicesFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = dataDir.absolutePath,
                    lexicon = lexicons.joinToString(",") { it.absolutePath },
                    dictDir = "",
                    lang = "",
                    lengthScale = 1.0f
                ),
                numThreads = 1,
                debug = false,
                provider = "cpu"
            ),
            ruleFsts = "",
            maxNumSentences = 1,
            silenceScale = silenceScale
        )
        // Kokoro resources are installed into app-private files, so sherpa-onnx must read absolute paths directly.
        tts = OfflineTts(null, config)
        AppLogger.i("Kokoro TTS loaded dir=${baseDir.absolutePath} sampleRate=${tts.sampleRate()} speakers=${tts.numSpeakers()}")
    }

    override val sampleRate: Int
        get() = tts.sampleRate()

    override fun setKokoroVoice(speakerId: Int) {
        val maxSpeaker = (tts.numSpeakers() - 1).coerceAtLeast(UserPrefs.KOKORO_MIN_SPEAKER_ID)
        this.speakerId = speakerId.coerceIn(UserPrefs.KOKORO_MIN_SPEAKER_ID, maxSpeaker)
    }

    override fun setSynthesisTuning(
        noiseScale: Float,
        lengthScale: Float,
        noiseW: Float,
        sentenceSilenceSec: Float
    ) {
        speed = (1f / lengthScale.coerceIn(0.1f, 5f)).coerceIn(0.2f, 4f)
        silenceScale = sentenceSilenceSec.coerceIn(0f, 2f)
    }

    override fun synthesize(text: String): FloatArray = synthesizeInternal(text, null)

    override fun synthesize(text: String, sentenceSilenceSec: Float): FloatArray {
        return synthesizeInternal(text, sentenceSilenceSec.coerceAtLeast(0f))
    }

    private fun synthesizeInternal(text: String, sentenceSilenceOverride: Float?): FloatArray {
        val content = text.trim()
        if (content.isEmpty()) return FloatArray(0)
        val selectedSpeaker = speakerId
        val effectiveSpeed = speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.0f) ?: 1.0f
        val generated = generateFiniteSamples(content, selectedSpeaker, effectiveSpeed)
        val valid = generated.isNotEmpty() && generated.maxAbs > 0.0001f
        val output = if (valid || selectedSpeaker == UserPrefs.KOKORO_DEFAULT_SPEAKER_ID) {
            generated.samples
        } else {
            AppLogger.e("Kokoro TTS invalid output speaker=$selectedSpeaker, retry speaker=${UserPrefs.KOKORO_DEFAULT_SPEAKER_ID}")
            generateFiniteSamples(content, UserPrefs.KOKORO_DEFAULT_SPEAKER_ID, 1.0f).samples
        }
        return appendSentenceSilence(output, sentenceSilenceOverride ?: silenceScale)
    }

    override fun close() {
        runCatching { tts.release() }
    }

    private fun resolveKokoroBaseDir(root: File): File {
        return root.walkTopDown()
            .filter { it.isDirectory }
            .firstOrNull { dir ->
                File(dir, "model.onnx").isFile &&
                    File(dir, "voices.bin").isFile &&
                    File(dir, "tokens.txt").isFile
            }
            ?: throw IllegalStateException("Kokoro 语音包未安装或文件不完整")
    }

    private fun generateFiniteSamples(content: String, sid: Int, effectiveSpeed: Float): KokoroGeneratedAudio {
        val raw = tts.generate(content, sid, effectiveSpeed).samples
        if (raw.isEmpty()) {
            AppLogger.i("Kokoro TTS generated samples=0 sr=$sampleRate speaker=$sid maxAbs=0.0 nonFinite=0")
            return KokoroGeneratedAudio(raw, 0f)
        }
        var maxAbs = 0f
        var nonFinite = 0
        val cleaned = FloatArray(raw.size)
        for (i in raw.indices) {
            val sample = raw[i]
            if (sample.isNaN() || sample.isInfinite()) {
                nonFinite += 1
                cleaned[i] = 0f
            } else {
                val clipped = sample.coerceIn(-1f, 1f)
                val abs = if (clipped < 0f) -clipped else clipped
                if (abs > maxAbs) maxAbs = abs
                cleaned[i] = clipped
            }
        }
        AppLogger.i("Kokoro TTS generated samples=${cleaned.size} sr=$sampleRate speaker=$sid speed=$effectiveSpeed maxAbs=$maxAbs nonFinite=$nonFinite")
        return KokoroGeneratedAudio(cleaned, maxAbs)
    }

    private fun appendSentenceSilence(samples: FloatArray, silenceSec: Float): FloatArray {
        if (samples.isEmpty()) return samples
        val silenceSamples = (sampleRate * silenceSec.coerceAtLeast(0f)).roundToInt()
        if (silenceSamples <= 0) return samples
        val out = FloatArray(samples.size + silenceSamples)
        System.arraycopy(samples, 0, out, 0, samples.size)
        return out
    }

    private data class KokoroGeneratedAudio(
        val samples: FloatArray,
        val maxAbs: Float
    ) {
        fun isNotEmpty(): Boolean = samples.isNotEmpty()
    }
}

private data class PendingSystemUtterance(
    val file: File,
    val doneLatch: CountDownLatch = CountDownLatch(1),
    @Volatile var success: Boolean = false
)

class SystemTtsEngine(context: Context) : TtsModule {
    companion object {
        private const val INIT_STATUS_PENDING = Int.MIN_VALUE
        private const val INIT_TIMEOUT_MS = 4000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initLatch = CountDownLatch(1)
    private val synthLock = Any()
    private val pendingUtterances = ConcurrentHashMap<String, PendingSystemUtterance>()
    private val initStatus = AtomicInteger(INIT_STATUS_PENDING)
    private val initFinalized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var initSuccess = false
    @Volatile private var sentenceSilenceSec: Float = 0.0f
    @Volatile private var speechRate: Float = 1.0f
    @Volatile private var currentSampleRate: Int = 22050
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var selectedEnginePackage: String? = null

    init {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initializeOnMainThread()
        } else {
            val posted = mainHandler.post {
                initializeOnMainThread()
            }
            if (!posted) {
                initLatch.countDown()
                throw IllegalStateException("系统语音合成初始化失败")
            }
        }
        waitForInit()
    }

    override val sampleRate: Int
        get() = currentSampleRate

    override fun setSynthesisTuning(
        noiseScale: Float,
        lengthScale: Float,
        noiseW: Float,
        sentenceSilenceSec: Float
    ) {
        this.sentenceSilenceSec = sentenceSilenceSec.coerceIn(0f, 2f)
        this.speechRate = (1f / lengthScale.coerceIn(0.1f, 5f)).coerceIn(0.2f, 4f)
    }

    override fun synthesize(text: String): FloatArray = synthesizeInternal(text, null)

    override fun synthesize(text: String, sentenceSilenceSec: Float): FloatArray {
        return synthesizeInternal(text, sentenceSilenceSec.coerceAtLeast(0f))
    }

    private fun synthesizeInternal(text: String, sentenceSilenceOverride: Float?): FloatArray {
        val content = text.trim()
        if (content.isEmpty()) return FloatArray(0)
        waitForInit()
        val currentTts = tts ?: throw IllegalStateException("系统语音合成不可用")
        synchronized(synthLock) {
            currentTts.setSpeechRate(speechRate)
            val outFile = File.createTempFile("system_tts_", ".wav", appContext.cacheDir)
            val utteranceId = "system-tts-${System.nanoTime()}"
            val pending = PendingSystemUtterance(outFile)
            pendingUtterances[utteranceId] = pending
            val result = currentTts.synthesizeToFile(content, Bundle(), outFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                pendingUtterances.remove(utteranceId)
                outFile.delete()
                throw IllegalStateException("系统语音合成失败")
            }
            if (!pending.doneLatch.await(20, TimeUnit.SECONDS) || !pending.success) {
                pendingUtterances.remove(utteranceId)
                outFile.delete()
                throw IllegalStateException("系统语音合成超时")
            }
            val (sr, samples) = readWavToMonoFloat(outFile)
            outFile.delete()
            currentSampleRate = sr
            val silenceSec = sentenceSilenceOverride ?: sentenceSilenceSec
            return appendSentenceSilence(samples, silenceSec, sr)
        }
    }

    private fun waitForInit() {
        if (!initLatch.await(5, TimeUnit.SECONDS) || !initSuccess || closed.get()) {
            close()
            throw IllegalStateException("系统语音合成初始化失败")
        }
    }

    private fun initializeOnMainThread() {
        if (!initFinalized.compareAndSet(false, true)) return
        if (closed.get()) {
            finishInit(false)
            return
        }
        try {
            val candidates = buildEngineCandidates()
            AppLogger.i(
                "SystemTtsEngine candidates=" +
                    candidates.joinToString(prefix = "[", postfix = "]") { it ?: "<default>" }
            )
            tryCreateEngineAsync(candidates, 0)
        } catch (e: Throwable) {
            finishInit(false)
            AppLogger.e("SystemTtsEngine create failed", e)
        }
    }

    private fun buildEngineCandidates(): List<String?> {
        val candidates = LinkedHashSet<String?>()
        val configured = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        if (configured != null) {
            candidates += configured
        }
        candidates += null
        val services = runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                0
            )
        }.getOrNull().orEmpty()
        services.mapNotNullTo(candidates) { it.serviceInfo?.packageName?.takeIf(String::isNotBlank) }
        return candidates.toList()
    }

    private fun finishInit(success: Boolean) {
        if (initLatch.count == 0L) return
        initSuccess = success && !closed.get()
        initLatch.countDown()
    }

    private fun tryCreateEngineAsync(candidates: List<String?>, index: Int) {
        if (closed.get()) {
            finishInit(false)
            return
        }
        if (index >= candidates.size) {
            finishInit(false)
            return
        }
        val enginePackage = candidates[index]
        val finished = AtomicBoolean(false)
        var instance: TextToSpeech? = null
        lateinit var timeoutRunnable: Runnable

        fun tryNextOrFinish() {
            if (closed.get()) {
                finishInit(false)
            } else {
                tryCreateEngineAsync(candidates, index + 1)
            }
        }

        fun handleResult(status: Int) {
            if (!finished.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeoutRunnable)
            val currentInstance = instance
            if (closed.get()) {
                currentInstance?.shutdown()
                finishInit(false)
                return
            }
            if (status != TextToSpeech.SUCCESS || currentInstance == null) {
                AppLogger.e("SystemTtsEngine init status=$status engine=${enginePackage ?: "<default>"}")
                currentInstance?.shutdown()
                tryNextOrFinish()
                return
            }
            selectedEnginePackage = enginePackage
            initStatus.set(status)
            tts = currentInstance
            val configured = configureInitializedEngine(currentInstance)
            if (configured) {
                AppLogger.i(
                    "SystemTtsEngine init success engine=${selectedEnginePackage ?: "<default>"}"
                )
                finishInit(true)
            } else {
                currentInstance.shutdown()
                tts = null
                tryNextOrFinish()
            }
        }

        timeoutRunnable = Runnable {
            if (!finished.compareAndSet(false, true)) return@Runnable
            AppLogger.e("SystemTtsEngine init timeout engine=${enginePackage ?: "<default>"}")
            instance?.shutdown()
            tryNextOrFinish()
        }

        val statusCallback: (Int) -> Unit = { status ->
            mainHandler.post {
                if (instance == null) {
                    mainHandler.post { handleResult(status) }
                } else {
                    handleResult(status)
                }
            }
        }

        try {
            instance = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(appContext) { status ->
                    statusCallback(status)
                }
            } else {
                TextToSpeech(appContext, { status ->
                    statusCallback(status)
                }, enginePackage)
            }
            mainHandler.postDelayed(timeoutRunnable, INIT_TIMEOUT_MS)
            if (closed.get() && finished.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                instance.shutdown()
                finishInit(false)
            }
        } catch (e: Throwable) {
            AppLogger.e(
                "SystemTtsEngine create failed engine=${enginePackage ?: "<default>"}",
                e
            )
            tryNextOrFinish()
        }
    }

    private fun configureInitializedEngine(currentTts: TextToSpeech): Boolean {
        return try {
            val targetLocale = Locale.getDefault()
            if (currentTts.isLanguageAvailable(targetLocale) >= TextToSpeech.LANG_AVAILABLE) {
                currentTts.language = targetLocale
            }
            currentTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtterances.remove(id)?.apply {
                            success = true
                            doneLatch.countDown()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtterances.remove(id)?.doneLatch?.countDown()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { id ->
                        pendingUtterances.remove(id)?.doneLatch?.countDown()
                    }
                }
            })
            true
        } catch (e: Throwable) {
            AppLogger.e("SystemTtsEngine init failed", e)
            false
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pendingUtterances.values.forEach { it.doneLatch.countDown() }
        pendingUtterances.clear()
        val current = tts
        tts = null
        finishInit(false)
        if (current != null) {
            mainHandler.post {
                runCatching { current.shutdown() }
            }
        }
    }

    private fun appendSentenceSilence(samples: FloatArray, sec: Float, sampleRate: Int): FloatArray {
        val silenceSec = sec.coerceAtLeast(0f)
        if (silenceSec <= 0f || samples.isEmpty()) return samples
        val silenceSamples = (sampleRate * silenceSec).roundToInt()
        if (silenceSamples <= 0) return samples
        val out = FloatArray(samples.size + silenceSamples)
        System.arraycopy(samples, 0, out, 0, samples.size)
        return out
    }

    private fun readWavToMonoFloat(file: File): Pair<Int, FloatArray> {
        val bytes = file.readBytes()
        fun leInt(offset: Int): Int {
            return (bytes[offset].toInt() and 0xff) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xff) shl 24)
        }
        fun leShort(offset: Int): Int {
            return (bytes[offset].toInt() and 0xff) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 8)
        }

        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            throw IllegalStateException("系统语音合成输出格式不支持")
        }

        var offset = 12
        var sampleRate = 22050
        var channels = 1
        var bitsPerSample = 16
        var format = 1
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = leInt(offset + 4)
            val chunkData = offset + 8
            if (chunkData + chunkSize > bytes.size) break
            when (chunkId) {
                "fmt " -> {
                    format = leShort(chunkData)
                    channels = leShort(chunkData + 2).coerceAtLeast(1)
                    sampleRate = leInt(chunkData + 4).coerceAtLeast(8000)
                    bitsPerSample = leShort(chunkData + 14)
                }
                "data" -> {
                    dataOffset = chunkData
                    dataSize = chunkSize
                }
            }
            offset = chunkData + chunkSize + (chunkSize and 1)
        }
        if (dataOffset < 0 || dataSize <= 0) {
            throw IllegalStateException("系统语音合成没有生成音频")
        }
        if (format != 1 || bitsPerSample != 16) {
            throw IllegalStateException("系统语音合成输出格式不支持")
        }
        val frameCount = dataSize / (channels * 2)
        val out = FloatArray(frameCount)
        var cursor = dataOffset
        for (i in 0 until frameCount) {
            var mixed = 0f
            repeat(channels) {
                val sample = leShort(cursor)
                val signed = if (sample >= 0x8000) sample - 0x10000 else sample
                mixed += (signed / 32768f)
                cursor += 2
            }
            out[i] = (mixed / channels.toFloat()).coerceIn(-1f, 1f)
        }
        return sampleRate to out
    }
}

class AudioPlayer(private val context: Context) {
    private companion object {
        private const val PLAYBACK_END_AUDIO_FOCUS_DWELL_MS = 5_000L
    }

    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile private var useCommunicationAttributes: Boolean = false
    @Volatile private var preferredOutputType: Int = AudioRoutePreference.OUTPUT_AUTO
    @Volatile private var playbackGain: Float = 1.0f
    @Volatile private var stopRequested: Boolean = false
    private val audioFocusController = PlaybackAudioFocusController(
        context,
        AudioAttributes.CONTENT_TYPE_SPEECH
    )
    private val trackLock = Any()
    private var currentTrack: AudioTrack? = null
    private var onOutputDevice: ((String) -> Unit)? = null
    private var onRender: ((FloatArray, Int, Int, Int) -> Unit)? = null

    fun setOnOutputDevice(callback: ((String) -> Unit)?) {
        onOutputDevice = callback
    }

    fun setOnRender(callback: ((FloatArray, Int, Int, Int) -> Unit)?) {
        onRender = callback
    }

    fun setUseCommunicationAttributes(enabled: Boolean) {
        useCommunicationAttributes = enabled
    }

    fun setPreferredOutputType(type: Int) {
        preferredOutputType = type
    }

    fun setPlaybackGainPercent(percent: Int) {
        playbackGain = (percent.coerceIn(0, 1000) / 100.0f).coerceAtLeast(0f)
    }

    fun setAudioFocusAvoidanceMode(mode: Int) {
        audioFocusController.setMode(mode)
    }

    fun stop() {
        stopRequested = true
        synchronized(trackLock) {
            val track = currentTrack ?: return
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
            } catch (_: Exception) {
            }
            try {
                track.flush()
            } catch (_: Exception) {
            }
        }
    }

    fun play(samples: FloatArray, sampleRate: Int, onProgress: ((Float) -> Unit)? = null) {
        if (samples.isEmpty()) return
        stopRequested = false
        val gain = playbackGain
        val scaledSamples = if (kotlin.math.abs(gain - 1.0f) < 0.0001f) {
            samples
        } else {
            FloatArray(samples.size) { idx ->
                (samples[idx] * gain).coerceIn(-1f, 1f)
            }
        }
        val lanPlaybackPlan = LanCastAudioBridge.playbackPlan()
        if (!lanPlaybackPlan.local) {
            playWebOnly(scaledSamples, sampleRate, onProgress)
            return
        }
        val shorts = ShortArray(scaledSamples.size) { idx ->
            val v = max(-1f, min(1f, scaledSamples[idx])) * Short.MAX_VALUE
            v.toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val usage = if (useCommunicationAttributes) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf, 4096))
            .build()

        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager != null && Build.VERSION.SDK_INT >= 23) {
            try {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val preferred = pickPreferredOutputDevice(outputs, preferredOutputType)
                if (preferred != null) {
                    val ok = track.setPreferredDevice(preferred)
                    AppLogger.i("Prefer output device: ${formatOutputDeviceLabel(preferred)} result=$ok")
                }
            } catch (e: Exception) {
                AppLogger.e("Prefer output device failed", e)
            }
        }

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                track.addOnRoutingChangedListener({ routing ->
                    val device = routing.routedDevice
                    onOutputDevice?.invoke(formatOutputDeviceLabel(device))
                }, null)
            } catch (_: Exception) {
            }
        }
        onOutputDevice?.invoke(formatOutputDeviceLabel(if (Build.VERSION.SDK_INT >= 24) track.routedDevice else null))
        synchronized(trackLock) {
            currentTrack = track
        }

        isPlaying = true
        val webStreamId = if (lanPlaybackPlan.web) {
            LanCastAudioBridge.beginPcm(sampleRate)
        } else {
            null
        }
        val audioFocusLease = audioFocusController.acquire()
        var normalPlaybackEnded = false
        try {
            track.play()
            onProgress?.invoke(0f)
            val total = shorts.size
            var written = 0
            var lastReport = 0f
            while (written < total && !stopRequested) {
                val count = min(2048, total - written)
                LanCastAudioBridge.publishPcm(
                    webStreamId,
                    scaledSamples,
                    written,
                    count,
                    sampleRate
                )
                onRender?.invoke(scaledSamples, written, count, sampleRate)
                val w = track.write(shorts, written, count)
                if (w <= 0) break
                written += w
                val progress = written.toFloat() / total.toFloat()
                if (progress - lastReport >= 0.02f || written == total) {
                    lastReport = progress
                    onProgress?.invoke(progress)
                }
            }
            if (written > 0 && !stopRequested) {
                waitForAudioTrackDrain(track, written, sampleRate)
            }
            normalPlaybackEnded = written >= total && !stopRequested
        } finally {
            val dwellAudioFocus = normalPlaybackEnded && !stopRequested
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
            synchronized(trackLock) {
                if (currentTrack === track) {
                    currentTrack = null
                }
            }
            stopRequested = false
            isPlaying = false
            LanCastAudioBridge.endPcm(webStreamId, interrupted = !normalPlaybackEnded)
            audioFocusLease?.releaseDelayed(
                if (dwellAudioFocus) PLAYBACK_END_AUDIO_FOCUS_DWELL_MS else 0L
            )
            onProgress?.invoke(1f)
        }
    }

    private fun playWebOnly(
        samples: FloatArray,
        sampleRate: Int,
        onProgress: ((Float) -> Unit)?
    ) {
        val streamId = LanCastAudioBridge.beginPcm(sampleRate)
        if (streamId == null) return
        isPlaying = true
        onOutputDevice?.invoke("网页音频")
        var sent = 0
        var completed = false
        try {
            onProgress?.invoke(0f)
            while (sent < samples.size && !stopRequested) {
                val count = min(2048, samples.size - sent)
                LanCastAudioBridge.publishPcm(streamId, samples, sent, count, sampleRate)
                sent += count
                onProgress?.invoke(sent.toFloat() / samples.size.toFloat())
                val chunkMs = (count * 1000L / sampleRate.coerceAtLeast(1)).coerceAtLeast(1L)
                SystemClock.sleep(chunkMs)
            }
            completed = sent >= samples.size && !stopRequested
        } finally {
            LanCastAudioBridge.endPcm(streamId, interrupted = !completed)
            stopRequested = false
            isPlaying = false
            onProgress?.invoke(1f)
        }
    }

    private fun waitForAudioTrackDrain(track: AudioTrack, writtenFrames: Int, sampleRate: Int) {
        val expectedMs = (writtenFrames * 1000L / sampleRate.coerceAtLeast(1)).coerceAtLeast(0L)
        val deadline = SystemClock.uptimeMillis() + expectedMs + 750L
        var lastHead = -1
        var stableCount = 0
        while (!stopRequested && SystemClock.uptimeMillis() < deadline) {
            val head = runCatching { track.playbackHeadPosition }.getOrDefault(writtenFrames)
            if (head >= writtenFrames) return
            if (head == lastHead) {
                stableCount += 1
                if (stableCount >= 20) return
            } else {
                stableCount = 0
                lastHead = head
            }
            Thread.sleep(20L)
        }
    }
}

class Aec3Processor(private val captureSampleRate: Int) {
    private val frameSize = max(1, captureSampleRate / 100)
    private val renderFrame = FloatArray(frameSize)
    private val captureFrame = FloatArray(frameSize)
    private val lock = Any()
    @Volatile private var handle: Long = nativeCreate(captureSampleRate, captureSampleRate, 1)

    fun isReady(): Boolean = handle != 0L

    fun processCapture(data: FloatArray, offset: Int, length: Int) {
        if (handle == 0L || length <= 0) return
        synchronized(lock) {
            val h = handle
            if (h == 0L || length <= 0) return
            var idx = 0
            while (idx < length) {
                val remaining = length - idx
                val chunk = min(frameSize, remaining)
                if (chunk == frameSize) {
                    nativeProcessCapture(h, data, offset + idx, chunk)
                } else {
                    java.util.Arrays.fill(captureFrame, 0f)
                    System.arraycopy(data, offset + idx, captureFrame, 0, chunk)
                    nativeProcessCapture(h, captureFrame, 0, frameSize)
                    System.arraycopy(captureFrame, 0, data, offset + idx, chunk)
                }
                idx += chunk
            }
        }
    }

    fun processRender(data: FloatArray, offset: Int, length: Int, inputRate: Int) {
        if (handle == 0L || length <= 0) return
        val src = if (inputRate == captureSampleRate) {
            data.copyOfRange(offset, offset + length)
        } else {
            resampleLinear(data, offset, length, inputRate, captureSampleRate)
        }
        if (src.isEmpty()) return
        synchronized(lock) {
            val h = handle
            if (h == 0L) return
            var idx = 0
            while (idx < src.size) {
                val remaining = src.size - idx
                val chunk = min(frameSize, remaining)
                if (chunk == frameSize) {
                    nativeProcessRender(h, src, idx, chunk)
                } else {
                    java.util.Arrays.fill(renderFrame, 0f)
                    System.arraycopy(src, idx, renderFrame, 0, chunk)
                    nativeProcessRender(h, renderFrame, 0, frameSize)
                }
                idx += chunk
            }
        }
    }

    fun release() {
        synchronized(lock) {
            val h = handle
            if (h != 0L) {
                nativeDestroy(h)
            }
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("aec3_jni")
        }
    }

    private external fun nativeCreate(captureSampleRate: Int, renderSampleRate: Int, channels: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeProcessCapture(handle: Long, data: FloatArray, offset: Int, length: Int)
    private external fun nativeProcessRender(handle: Long, data: FloatArray, offset: Int, length: Int)

    private fun resampleLinear(
        data: FloatArray,
        offset: Int,
        length: Int,
        inRate: Int,
        outRate: Int
    ): FloatArray {
        if (length <= 0 || inRate <= 0 || outRate <= 0) return FloatArray(0)
        if (inRate == outRate) {
            return data.copyOfRange(offset, offset + length)
        }
        val ratio = outRate.toDouble() / inRate.toDouble()
        val outLen = max(1, (length * ratio).roundToInt())
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx)
            val i0 = offset + idx
            val i1 = min(offset + length - 1, i0 + 1)
            val s0 = data[i0]
            val s1 = data[i1]
            out[i] = (s0 + (s1 - s0) * frac.toFloat())
        }
        return out
    }
}

data class SpeakerEnrollResult(
    val success: Boolean,
    val message: String,
    val profile: FloatArray? = null,
    val confirmationProfile: FloatArray? = null,
    val neuralProfile: FloatArray? = null
)

internal object SherpaSpeechEnhancer {
    private const val GTCRN_FILE_NAME = "gtcrn_simple.onnx"
    private const val DPDFNET2_FILE_NAME = "dpdfnet2.onnx"
    private const val DPDFNET4_FILE_NAME = "dpdfnet4.onnx"

    private val lock = Any()
    private val cachedModelFiles = mutableMapOf<String, File>()
    private var offlineMode: Int = SpeechEnhancementMode.OFF
    private var offlineDenoiser: OfflineSpeechDenoiser? = null
    private var streamingMode: Int = SpeechEnhancementMode.OFF
    private var streamingDenoiser: OnlineSpeechDenoiser? = null
    private var streamingCarry = FloatArray(0)

    fun processOffline(context: Context, mode: Int, samples: FloatArray, sampleRate: Int): Pair<FloatArray, Int> {
        if (samples.isEmpty()) return samples to sampleRate
        if (SpeechEnhancementMode.clamp(mode) != SpeechEnhancementMode.GTCRN_OFFLINE) {
            return samples to sampleRate
        }
        return synchronized(lock) {
            val denoiser = ensureOfflineDenoiserLocked(context, mode) ?: return@synchronized samples to sampleRate
            val result = denoiser.run(samples, sampleRate)
            result.samples.copyOf() to result.sampleRate
        }
    }

    fun processStreamingChunk(context: Context, mode: Int, samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        if (!SpeechEnhancementMode.isStreaming(mode)) {
            return samples
        }
        return synchronized(lock) {
            val denoiser = ensureStreamingDenoiserLocked(context, mode) ?: return@synchronized samples
            processStreamingChunkLocked(denoiser, samples, sampleRate)
        }
    }

    fun processPreview(context: Context, mode: Int, samples: FloatArray, sampleRate: Int): Pair<FloatArray, Int> {
        if (samples.isEmpty()) return samples to sampleRate
        val normalized = SpeechEnhancementMode.clamp(mode)
        if (!SpeechEnhancementMode.isEnabled(normalized)) {
            return samples to sampleRate
        }
        if (normalized == SpeechEnhancementMode.GTCRN_OFFLINE) {
            return processOffline(context, normalized, samples, sampleRate)
        }
        if (!SpeechEnhancementMode.isStreaming(normalized)) {
            return samples to sampleRate
        }
        return synchronized(lock) {
            val denoiser = ensureStreamingDenoiserLocked(context, normalized) ?: return@synchronized samples to sampleRate
            val frameShift = denoiser.frameShiftInSamples.coerceAtLeast(1)
            streamingCarry = FloatArray(0)
            denoiser.reset()
            val out = ArrayList<FloatArray>()
            var offset = 0
            val chunkSize = max(frameShift * 8, frameShift)
            while (offset < samples.size) {
                val next = min(samples.size, offset + chunkSize)
                val chunk = samples.copyOfRange(offset, next)
                val result = processStreamingChunkLocked(denoiser, chunk, sampleRate)
                if (result.isNotEmpty()) {
                    out += result
                }
                offset = next
            }
            val tail = flushStreamingLocked(denoiser, sampleRate)
            if (tail.isNotEmpty()) {
                out += tail
            }
            denoiser.reset()
            streamingCarry = FloatArray(0)
            concatFloatArrays(out, samples.size) to sampleRate
        }
    }

    fun resetStreaming() {
        synchronized(lock) {
            streamingCarry = FloatArray(0)
            streamingDenoiser?.reset()
        }
    }

    fun release() {
        synchronized(lock) {
            offlineDenoiser?.release()
            offlineDenoiser = null
            streamingDenoiser?.release()
            streamingDenoiser = null
            offlineMode = SpeechEnhancementMode.OFF
            streamingMode = SpeechEnhancementMode.OFF
            streamingCarry = FloatArray(0)
        }
    }

    private fun processStreamingChunkLocked(
        denoiser: OnlineSpeechDenoiser,
        samples: FloatArray,
        sampleRate: Int
    ): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val frameShift = denoiser.frameShiftInSamples.coerceAtLeast(1)
        val combined = concatFloatArrays(streamingCarry, samples)
        val processLen = (combined.size / frameShift) * frameShift
        if (processLen <= 0) {
            streamingCarry = combined
            return FloatArray(0)
        }
        val current = combined.copyOfRange(0, processLen)
        streamingCarry = if (processLen < combined.size) {
            combined.copyOfRange(processLen, combined.size)
        } else {
            FloatArray(0)
        }
        val result = denoiser.run(current, sampleRate)
        return result.samples.copyOf()
    }

    private fun flushStreamingLocked(denoiser: OnlineSpeechDenoiser, sampleRate: Int): FloatArray {
        val carry = streamingCarry
        if (carry.isEmpty()) return FloatArray(0)
        val frameShift = denoiser.frameShiftInSamples.coerceAtLeast(1)
        val padded = FloatArray(frameShift)
        System.arraycopy(carry, 0, padded, 0, carry.size)
        streamingCarry = FloatArray(0)
        val result = denoiser.run(padded, sampleRate).samples
        return result.copyOf(min(result.size, carry.size))
    }

    private fun ensureOfflineDenoiserLocked(context: Context, mode: Int): OfflineSpeechDenoiser? {
        val normalized = SpeechEnhancementMode.clamp(mode)
        if (normalized != SpeechEnhancementMode.GTCRN_OFFLINE) {
            offlineDenoiser?.release()
            offlineDenoiser = null
            offlineMode = SpeechEnhancementMode.OFF
            return null
        }
        if (offlineDenoiser != null && offlineMode == normalized) {
            return offlineDenoiser
        }
        offlineDenoiser?.release()
        offlineDenoiser = OfflineSpeechDenoiser(
            null,
            OfflineSpeechDenoiserConfig(
                buildModelConfigLocked(context, normalized)
            )
        )
        offlineMode = normalized
        return offlineDenoiser
    }

    private fun ensureStreamingDenoiserLocked(context: Context, mode: Int): OnlineSpeechDenoiser? {
        val normalized = SpeechEnhancementMode.clamp(mode)
        if (!SpeechEnhancementMode.isStreaming(normalized)) {
            streamingDenoiser?.release()
            streamingDenoiser = null
            streamingMode = SpeechEnhancementMode.OFF
            streamingCarry = FloatArray(0)
            return null
        }
        if (streamingDenoiser != null && streamingMode == normalized) {
            return streamingDenoiser
        }
        streamingDenoiser?.release()
        streamingCarry = FloatArray(0)
        streamingDenoiser = OnlineSpeechDenoiser(
            null,
            OnlineSpeechDenoiserConfig(
                buildModelConfigLocked(context, normalized)
            )
        )
        streamingMode = normalized
        return streamingDenoiser
    }

    private fun buildModelConfigLocked(context: Context, mode: Int): OfflineSpeechDenoiserModelConfig {
        val normalized = SpeechEnhancementMode.clamp(mode)
        return OfflineSpeechDenoiserModelConfig().apply {
            numThreads = 2
            debug = false
            provider = "cpu"
            when (normalized) {
                SpeechEnhancementMode.GTCRN_OFFLINE,
                SpeechEnhancementMode.GTCRN_STREAMING -> {
                    gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(
                        ensureModelFileLocked(context, GTCRN_FILE_NAME).absolutePath
                    )
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig()
                }
                SpeechEnhancementMode.DPDFNET2_STREAMING -> {
                    gtcrn = OfflineSpeechDenoiserGtcrnModelConfig()
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig(
                        ensureModelFileLocked(context, DPDFNET2_FILE_NAME).absolutePath
                    )
                }
                SpeechEnhancementMode.DPDFNET4_STREAMING -> {
                    gtcrn = OfflineSpeechDenoiserGtcrnModelConfig()
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig(
                        ensureModelFileLocked(context, DPDFNET4_FILE_NAME).absolutePath
                    )
                }
                else -> {
                    gtcrn = OfflineSpeechDenoiserGtcrnModelConfig()
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig()
                }
            }
        }
    }

    private fun ensureModelFileLocked(context: Context, fileName: String): File {
        val cached = cachedModelFiles[fileName]
        if (cached != null && cached.exists() && cached.length() > 0L) {
            return cached
        }
        RecognitionResourceRepository.resolveSpeechEnhancementModel(context, fileName)?.let { installed ->
            cachedModelFiles[fileName] = installed
            return installed
        }
        throw IllegalStateException("缺少语音增强模型 $fileName，请先安装语音识别资源包")
    }

    private fun concatFloatArrays(first: FloatArray, second: FloatArray): FloatArray {
        if (first.isEmpty()) return second.copyOf()
        if (second.isEmpty()) return first.copyOf()
        val out = FloatArray(first.size + second.size)
        System.arraycopy(first, 0, out, 0, first.size)
        System.arraycopy(second, 0, out, first.size, second.size)
        return out
    }

    private fun concatFloatArrays(chunks: List<FloatArray>, expectedSize: Int): FloatArray {
        if (chunks.isEmpty()) return FloatArray(0)
        val totalSize = chunks.sumOf { it.size }
        val out = FloatArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, out, offset, chunk.size)
            offset += chunk.size
        }
        if (expectedSize > 0 && out.size != expectedSize) {
            return if (out.size > expectedSize) {
                out.copyOf(expectedSize)
            } else {
                FloatArray(expectedSize).also { padded ->
                    System.arraycopy(out, 0, padded, 0, out.size)
                }
            }
        }
        return out
    }
}

class RealtimeController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (Long, String) -> Unit,
    private val onStreamingResult: (String) -> Unit,
    private val onListeningResult: (Long, String) -> Unit,
    private val onListeningStreamingResult: (String) -> Unit,
    private val onProgress: (Long, Float) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onInputDevice: (String) -> Unit,
    private val onOutputDevice: (String) -> Unit,
    private val onAec3Status: (String) -> Unit,
    private val onAec3Diag: (String) -> Unit,
    private val onSpeakerVerify: (Float, Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
    initialSuppressWhilePlaying: Boolean,
    initialUseVoiceCommunication: Boolean,
    initialCommunicationMode: Boolean,
    initialMinVolumePercent: Int,
    initialPlaybackGainPercent: Int,
    initialAudioFocusAvoidanceMode: Int,
    initialDenoiserMode: Int,
    initialSpeechEnhancementMode: Int,
    initialPiperNoiseScale: Float,
    initialPiperLengthScale: Float,
    initialPiperNoiseW: Float,
    initialPiperSentenceSilenceSec: Float,
    initialKokoroSpeakerId: Int,
    initialSuppressDelaySec: Float,
    initialPreferredInputType: Int,
    initialPreferredOutputType: Int,
    initialUseAec3: Boolean,
    initialAsrRecognitionLanguage: String,
    initialClassicVadEnabled: Boolean,
    initialSileroVadEnabled: Boolean,
    initialSileroVadThreshold: Float,
    initialSileroVadPreRollMs: Int,
    initialAllowSystemAecWithAec3: Boolean,
    initialSpeakerVerifyEnabled: Boolean,
    initialSpeakerVerifyThreshold: Float,
    initialSpeakerVerifyToleranceLevel: Int,
    initialExperimentalRecognitionSensitivity: Int,
    initialExperimentalTargetSpeakerBackend: Int,
    initialSpeakerProfiles: List<FloatArray>,
    initialNeuralSpeakerProfiles: List<FloatArray?>,
    initialConfirmationSpeakerProfiles: List<FloatArray?>,
    private val shouldSuppressAutoSpeakForText: suspend (String) -> Boolean = { false },
    private val moduleFactory: SpeechModuleFactory = DefaultSpeechModuleFactory
) {
    private var recorder: AudioRecord? = null
    private var loopJob: Job? = null
    private var asr: AsrModule? = null
    private var listeningAsr: AsrModule? = null
    @Volatile private var tts: TtsModule? = null
    private val player = AudioPlayer(context)
    private val sampleRate = 16000
    private val queueLock = Any()
    private val ttsQueue = ArrayDeque<QueuedTts>()
    private var ttsJob: Job? = null
    private val ttsPlaybackGeneration = AtomicLong(0L)
    private var nextUtteranceId = 1L
    @Volatile private var suppressWhilePlaying = initialSuppressWhilePlaying
    @Volatile private var useVoiceCommunication = initialUseVoiceCommunication
    @Volatile private var useCommunicationMode = initialCommunicationMode
    @Volatile private var minSegmentRms = (initialMinVolumePercent.coerceIn(0, 100) / 100.0)
    @Volatile private var denoiserMode = initialDenoiserMode.coerceIn(AudioDenoiserMode.OFF, AudioDenoiserMode.SPEEX)
    @Volatile private var speechEnhancementMode = SpeechEnhancementMode.clamp(initialSpeechEnhancementMode)
    @Volatile private var suppressDelayMs = (initialSuppressDelaySec.coerceIn(0f, 5f) * 1000f).toLong()
    @Volatile private var piperNoiseScale = initialPiperNoiseScale.coerceIn(0f, 2f)
    @Volatile private var piperLengthScale = initialPiperLengthScale.coerceIn(0.1f, 5f)
    @Volatile private var piperNoiseW = initialPiperNoiseW.coerceIn(0.3f, 1.5f)
    @Volatile private var piperSentenceSilenceSec = initialPiperSentenceSilenceSec.coerceIn(0f, 2f)
    @Volatile private var kokoroSpeakerId = initialKokoroSpeakerId.coerceIn(
        UserPrefs.KOKORO_MIN_SPEAKER_ID,
        UserPrefs.KOKORO_MAX_SPEAKER_ID
    )
    @Volatile private var suppressUntilMs: Long = 0L
    @Volatile private var preferredInputType = initialPreferredInputType
    @Volatile private var preferredOutputType = initialPreferredOutputType
    @Volatile private var useAec3 = initialUseAec3
    @Volatile private var asrRecognitionLanguage =
        AsrRecognitionLanguage.normalize(initialAsrRecognitionLanguage)
    @Volatile private var listeningRecognitionLanguage = AsrRecognitionLanguage.DEFAULT
    @Volatile private var listeningRecognitionEnabled = false
    @Volatile private var listeningCapturePaused = false
    @Volatile private var mainRecognitionEnabled = true
    @Volatile private var classicVadEnabled = initialClassicVadEnabled
    @Volatile private var sileroVadEnabled = initialSileroVadEnabled
    @Volatile private var sileroVadThreshold = initialSileroVadThreshold.coerceIn(
        UserPrefs.SILERO_VAD_MIN_THRESHOLD,
        UserPrefs.SILERO_VAD_MAX_THRESHOLD
    )
    @Volatile private var sileroVadPreRollMs = initialSileroVadPreRollMs.coerceIn(
        UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS,
        UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS
    )
    @Volatile private var allowSystemAecWithAec3 = initialAllowSystemAecWithAec3
    @Volatile private var speakerVerifyEnabled = initialSpeakerVerifyEnabled
    @Volatile private var speakerVerifyTolerance = if (
        initialSpeakerVerifyToleranceLevel in SpeakerVerificationTolerance.entries.indices
    ) {
        SpeakerVerificationTolerance.fromIndex(initialSpeakerVerifyToleranceLevel)
    } else {
        SpeakerVerificationTolerance.fromThreshold(initialSpeakerVerifyThreshold)
    }
    @Volatile private var experimentalRecognitionSensitivity =
        initialExperimentalRecognitionSensitivity.coerceIn(0, 100)
    @Volatile private var experimentalTargetSpeakerBackend =
        ExperimentalTargetSpeakerBackend.normalize(initialExperimentalTargetSpeakerBackend)
    @Volatile private var speakerProfiles: List<FloatArray> = emptyList()
    @Volatile private var speakerConfirmationProfiles: List<FloatArray?> = emptyList()
    @Volatile private var neuralSpeakerProfiles: List<FloatArray?> = emptyList()
    @Volatile private var speakerVerifyReferenceProfile: FloatArray? = null
    @Volatile private var adaptivePrimarySpeakerThreshold = speakerVerifyTolerance.primaryThreshold
    @Volatile private var adaptiveConfirmationSpeakerThreshold = speakerVerifyTolerance.confirmationThreshold
    @Volatile private var speakerLastSimilarity: Float = -1f
    private val speakerVerifyLock = Any()
    private val lastRenderMs = AtomicLong(0L)
    private val lastCaptureMs = AtomicLong(0L)
    private val renderFrames = AtomicLong(0L)
    private val captureFrames = AtomicLong(0L)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousAudioMode: Int? = null
    private var previousSpeakerOn: Boolean? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var aec: AcousticEchoCanceler? = null
    private var aec3: Aec3Processor? = null
    private var currentAsrDir: File? = null
    private var currentSileroVadModelFile: File? = null
    @Volatile private var currentVoiceDir: File? = null
    private var lastLevelReportMs: Long = 0L
    private val recorderMutex = Mutex()
    private var rnnoiseProcessor: RnNoiseProcessor? = null
    private var speexNoiseProcessor: SpeexNoiseSuppressor? = null
    private var sileroVadProcessor: SileroVadProcessor? = null
    private val denoiserLock = Any()
    private val sileroVadLock = Any()
    private val sileroPreRollLock = Any()
    private val sileroPreRollSamples = mutableListOf<Float>()
    private var sileroPendingPreRollSamples: FloatArray? = null
    private var sileroSpeechDetected = false
    private var lastAcceptedTtsTextKey: String = ""
    private var lastAcceptedTtsAtMs: Long = 0L
    private val duplicateTtsWindowMs: Long = 1800L
    @Volatile private var pttStreamingEnabled: Boolean = false
    @Volatile private var suppressAsrAutoSpeak: Boolean = false
    @Volatile private var lastStreamingDecodeAtMs: Long = 0L
    private val streamingDecodeBusy = AtomicBoolean(false)
    private val streamingDecodeGeneration = AtomicLong(0L)
    private val recognitionInputEpoch = AtomicLong(0L)
    private val activeAsrJobsLock = Any()
    private val activeAsrJobs = mutableSetOf<Job>()
    private val resultCallbackJobs = CallbackJobTracker(scope)
    private val simulatedAudioCallbacksSynchronous = AtomicBoolean(false)
    private val asrDecodeMutex = Mutex()
    private val listeningAsrDecodeMutex = Mutex()
    private val pendingRecognitionFlush = AtomicReference<CompletableDeferred<Unit>?>(null)
    private var recorderFlushOnStop: AtomicBoolean? = null
    private val segmentProcessMutex = Mutex()
    private val speakerVerifySessionLock = Any()
    private val speakerVerifyPendingSegments = mutableListOf<RecognitionSegment>()
    private var speakerVerifyPendingSamples = 0
    private var speakerVerifyPendingSampleRate = sampleRate
    private var speakerVerifyLastSegmentAtMs = 0L
    private var speakerVerifySessionPassed = false
    private val experimentalTargetSpeakerFrontend: TargetSpeakerFrontend =
        VoiceFilterInspiredTargetSpeakerFrontend()
    private val neuralSpeakerFilterResources = NeuralSpeakerFilterResourceRepository(context)
    private val neuralSeparatorLock = Any()
    private var neuralSeparator: NeuralTargetSpeakerSeparator? = null
    private var neuralSeparatorModelPath: String? = null
    @Volatile private var neuralSeparatorPerformanceChecked = false
    @Volatile private var neuralSeparatorTooSlowForAuto = false

    private data class RecognitionSegment(
        val audio: FloatArray,
        val sampleRate: Int,
        val rms: Double
    )

    private data class SpeakerVerifyAttempt(
        val segment: RecognitionSegment,
        val samples: Int
    )

    private data class GuardedSpeakerAudio(
        val audio: FloatArray,
        val similarity: Float
    )

    private sealed class SpeakerGateResult {
        data class Ready(val segments: List<RecognitionSegment>) : SpeakerGateResult()
        object Pending : SpeakerGateResult()
    }

    private companion object {
        private const val SPEAKER_VERIFY_MAX_PENDING_MS = 5000
        private const val SPEAKER_VERIFY_SESSION_RESET_MS = 3000L
        private const val NEURAL_SPEAKER_EMBEDDING_DIM = 192
        private const val NEURAL_PERFORMANCE_PROBE_SAMPLES = 480 * 4
        private const val RECOGNITION_FLUSH_TIMEOUT_MS = 1500L
        private const val SIMULATED_AUDIO_END_OF_STREAM = -1
        private const val DEFAULT_MAX_SPEECH_DURATION_SEC = 12f
        private const val LISTENING_MIN_SILENCE_DURATION_SEC = 0.3f
        private const val LISTENING_MIN_SPEECH_DURATION_SEC = 0.25f
        private const val LISTENING_MAX_SPEECH_DURATION_SEC = 5f
        private const val LISTENING_ENDPOINT_FALLBACK_SILENCE_MS = 450
        private const val SILERO_WINDOW_SIZE_SAMPLES = 512
        private const val LISTENING_PREVIEW_PREROLL_WINDOWS = 10
    }

    private data class QueuedTts(
        val id: Long,
        val text: String
    )

    private data class TtsSynthesisChunk(
        val text: String,
        val pauseSec: Float
    )

    private class SileroVadProcessor(
        modelFile: File,
        sampleRate: Int,
        threshold: Float,
        minSilenceDurationSec: Float,
        minSpeechDurationSec: Float,
        maxSpeechDurationSec: Float,
        numThreads: Int = 2
    ) {
        private val lock = Any()
        private val vad = Vad(
            null,
            VadModelConfig().apply {
                this.sampleRate = sampleRate
                this.numThreads = numThreads
                provider = "cpu"
                debug = false
                sileroVadModelConfig = SileroVadModelConfig().apply {
                    model = modelFile.absolutePath
                    this.threshold = threshold
                    minSilenceDuration = minSilenceDurationSec
                    minSpeechDuration = minSpeechDurationSec
                    windowSize = SILERO_WINDOW_SIZE_SAMPLES
                    maxSpeechDuration = maxSpeechDurationSec
                }
            }
        )

        fun acceptWaveform(samples: FloatArray) {
            synchronized(lock) {
                vad.acceptWaveform(samples)
            }
        }

        fun isSpeechDetected(): Boolean {
            return synchronized(lock) {
                vad.isSpeechDetected()
            }
        }

        fun drainSegments(): List<FloatArray> {
            return synchronized(lock) {
                drainSegmentsLocked()
            }
        }

        fun flushAndDrain(): List<FloatArray> {
            return synchronized(lock) {
                vad.flush()
                drainSegmentsLocked()
            }
        }

        fun reset() {
            synchronized(lock) {
                vad.reset()
                vad.clear()
            }
        }

        fun release() {
            synchronized(lock) {
                vad.release()
            }
        }

        private fun drainSegmentsLocked(): List<FloatArray> {
            val segments = mutableListOf<FloatArray>()
            while (!vad.empty()) {
                val segment = vad.front()
                segments.add(segment.samples.copyOf())
                vad.pop()
            }
            return segments
        }
    }

    private fun normalizePunctuationForTts(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        for (ch in text) {
            out.append(
                when (ch) {
                    '，', '、', '\\', '/' -> ','
                    '。', '~', '_', '\u2014', '\u2026' -> '.'
                    '！' -> '!'
                    '？' -> '?'
                    '；' -> ';'
                    '：' -> ':'
                    else -> ch
                }
            )
        }
        return out.toString()
    }

    private fun splitForPunctuationSynthesis(text: String): List<TtsSynthesisChunk> {
        val normalized = normalizePunctuationForTts(text).trim()
        if (normalized.isEmpty()) return emptyList()
        val longPause = piperSentenceSilenceSec.coerceIn(0f, 2f)
        val shortPause = if (longPause <= 0f) 0f else (longPause * 0.4f).coerceIn(0.04f, longPause)
        val chunks = mutableListOf<TtsSynthesisChunk>()
        val current = StringBuilder()
        fun pushChunk(pause: Float) {
            val part = current.toString().trim()
            if (part.isEmpty()) return
            chunks.add(TtsSynthesisChunk(part, pause))
            current.setLength(0)
        }
        for (ch in normalized) {
            when (ch) {
                ',' -> pushChunk(shortPause)
                '.', '!', '?', ';', ':' -> pushChunk(longPause)
                else -> current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            chunks.add(TtsSynthesisChunk(tail, 0f))
        }
        return if (chunks.isNotEmpty()) chunks else listOf(TtsSynthesisChunk(normalized, 0f))
    }

    private fun concatAudio(arrays: List<FloatArray>): FloatArray {
        val total = arrays.sumOf { it.size }
        if (total <= 0) return FloatArray(0)
        val out = FloatArray(total)
        var offset = 0
        for (arr in arrays) {
            if (arr.isEmpty()) continue
            System.arraycopy(arr, 0, out, offset, arr.size)
            offset += arr.size
        }
        return out
    }

    private inline fun <T> withAndroidThreadPriority(priority: Int, block: () -> T): T {
        val tid = android.os.Process.myTid()
        val previous = runCatching { android.os.Process.getThreadPriority(tid) }.getOrNull()
        runCatching { android.os.Process.setThreadPriority(tid, priority) }
        return try {
            block()
        } finally {
            if (previous != null) {
                runCatching { android.os.Process.setThreadPriority(tid, previous) }
            }
        }
    }

    private suspend fun <T> withAndroidThreadPrioritySuspending(
        priority: Int,
        block: suspend () -> T
    ): T {
        val tid = android.os.Process.myTid()
        val previous = runCatching { android.os.Process.getThreadPriority(tid) }.getOrNull()
        runCatching { android.os.Process.setThreadPriority(tid, priority) }
        return try {
            block()
        } finally {
            if (previous != null) {
                runCatching { android.os.Process.setThreadPriority(tid, previous) }
            }
        }
    }

    private fun synthesizeByPunctuation(ttsEngine: TtsModule, text: String): FloatArray {
        val chunks = splitForPunctuationSynthesis(text)
        if (chunks.isEmpty()) return FloatArray(0)
        if (chunks.size == 1) {
            val only = chunks[0]
            return ttsEngine.synthesize(only.text, only.pauseSec)
        }
        val parts = mutableListOf<FloatArray>()
        for (chunk in chunks) {
            val piece = ttsEngine.synthesize(chunk.text, chunk.pauseSec)
            if (piece.isEmpty()) continue
            parts.add(piece)
        }
        return concatAudio(parts)
    }

    private fun toTtsDedupKey(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        return t.trimEnd('。', '！', '？', '!', '?', '，', ',', '；', ';', '、', '.')
    }

    private fun shouldSkipDuplicateTts(text: String): Boolean {
        val key = toTtsDedupKey(text)
        if (key.isEmpty()) return true
        val now = SystemClock.uptimeMillis()
        synchronized(queueLock) {
            val duplicated = key == lastAcceptedTtsTextKey && (now - lastAcceptedTtsAtMs) <= duplicateTtsWindowMs
            if (!duplicated) {
                lastAcceptedTtsTextKey = key
                lastAcceptedTtsAtMs = now
            }
            return duplicated
        }
    }

    private fun notifyResult(id: Long, text: String) {
        if (simulatedAudioCallbacksSynchronous.get()) {
            onResult(id, text)
        } else {
            resultCallbackJobs.launch { onResult(id, text) }
        }
    }

    private fun notifyStreamingResult(text: String) {
        scope.launch { onStreamingResult(text) }
    }

    private fun notifyListeningResult(id: Long, text: String) {
        onListeningResult(id, text)
    }

    private fun notifyListeningStreamingResult(text: String) {
        onListeningStreamingResult(text)
    }

    private fun notifyProgress(id: Long, progress: Float) {
        scope.launch { onProgress(id, progress.coerceIn(0f, 1f)) }
    }

    private fun notifyLevel(level: Float) {
        scope.launch { onLevel(level.coerceIn(0f, 1f)) }
    }

    private fun notifyInputDevice(label: String) {
        scope.launch { onInputDevice(label) }
    }

    private fun notifyOutputDevice(label: String) {
        scope.launch { onOutputDevice(label) }
    }

    private fun notifyAec3Status(status: String) {
        scope.launch { onAec3Status(status) }
    }

    private fun notifyAec3Diag(diag: String) {
        scope.launch { onAec3Diag(diag) }
    }

    private fun notifySpeakerVerify(similarity: Float, passed: Boolean) {
        if (simulatedAudioCallbacksSynchronous.get()) {
            onSpeakerVerify(similarity, passed)
        } else {
            scope.launch { onSpeakerVerify(similarity, passed) }
        }
    }

    private fun notifyStatus(msg: String) {
        scope.launch { onStatus(msg) }
    }

    private fun notifyError(msg: String) {
        scope.launch { onError(msg) }
    }

    init {
        rebuildSpeakerVerifyState(
            initialSpeakerProfiles,
            initialNeuralSpeakerProfiles,
            initialConfirmationSpeakerProfiles
        )
        player.setUseCommunicationAttributes(useCommunicationMode)
        player.setPreferredOutputType(preferredOutputType)
        player.setPlaybackGainPercent(initialPlaybackGainPercent)
        player.setAudioFocusAvoidanceMode(initialAudioFocusAvoidanceMode)
        player.setOnOutputDevice { notifyOutputDevice(it) }
        player.setOnRender { data, offset, length, rate ->
            if (useAec3) {
                aec3?.processRender(data, offset, length, rate)
                renderFrames.incrementAndGet()
                lastRenderMs.set(SystemClock.uptimeMillis())
            }
        }
        notifyAec3Status(if (useAec3) "待启动" else "未启用")
    }

    fun setSuppressWhilePlaying(enabled: Boolean) {
        suppressWhilePlaying = enabled
        if (!enabled) {
            suppressUntilMs = 0L
        }
    }

    fun setUseVoiceCommunication(enabled: Boolean) {
        useVoiceCommunication = enabled
    }

    fun setMinVolumePercent(percent: Int) {
        minSegmentRms = (percent.coerceIn(0, 100) / 100.0)
    }

    fun setPlaybackGainPercent(percent: Int) {
        player.setPlaybackGainPercent(percent)
    }

    fun setAudioFocusAvoidanceMode(mode: Int) {
        player.setAudioFocusAvoidanceMode(mode)
    }

    fun setDenoiserMode(mode: Int) {
        val normalized = mode.coerceIn(AudioDenoiserMode.OFF, AudioDenoiserMode.SPEEX)
        if (normalized != AudioDenoiserMode.OFF && SpeechEnhancementMode.isEnabled(speechEnhancementMode)) {
            speechEnhancementMode = SpeechEnhancementMode.OFF
            SherpaSpeechEnhancer.release()
        }
        synchronized(denoiserLock) {
            if (denoiserMode == normalized) return
            denoiserMode = normalized
            when (normalized) {
                AudioDenoiserMode.OFF -> releaseNoiseProcessorsLocked()
                AudioDenoiserMode.RNNOISE -> {
                    speexNoiseProcessor?.release()
                    speexNoiseProcessor = null
                    rnnoiseProcessor?.reset()
                }
                AudioDenoiserMode.SPEEX -> {
                    rnnoiseProcessor?.release()
                    rnnoiseProcessor = null
                    speexNoiseProcessor?.reset()
                }
                else -> Unit
            }
        }
    }

    fun setSpeechEnhancementMode(mode: Int) {
        val normalized = SpeechEnhancementMode.clamp(mode)
        if (SpeechEnhancementMode.isEnabled(normalized)) {
            setDenoiserMode(AudioDenoiserMode.OFF)
        }
        if (speechEnhancementMode == normalized) return
        speechEnhancementMode = normalized
        SherpaSpeechEnhancer.release()
    }

    private fun applyTtsSynthesisTuning(target: TtsModule? = tts) {
        target?.setSynthesisTuning(
            noiseScale = piperNoiseScale,
            lengthScale = piperLengthScale,
            noiseW = piperNoiseW,
            sentenceSilenceSec = piperSentenceSilenceSec
        )
        target?.setKokoroVoice(kokoroSpeakerId)
    }

    fun setKokoroSpeakerId(value: Int) {
        kokoroSpeakerId = value.coerceIn(UserPrefs.KOKORO_MIN_SPEAKER_ID, UserPrefs.KOKORO_MAX_SPEAKER_ID)
        tts?.setKokoroVoice(kokoroSpeakerId)
    }

    fun setPiperNoiseScale(value: Float) {
        piperNoiseScale = value.coerceIn(0f, 2f)
        applyTtsSynthesisTuning()
    }

    fun setPiperLengthScale(value: Float) {
        piperLengthScale = value.coerceIn(0.1f, 5f)
        applyTtsSynthesisTuning()
    }

    fun setPiperNoiseW(value: Float) {
        piperNoiseW = value.coerceIn(0.3f, 1.5f)
        applyTtsSynthesisTuning()
    }

    fun setPiperSentenceSilenceSec(value: Float) {
        piperSentenceSilenceSec = value.coerceIn(0f, 2f)
        applyTtsSynthesisTuning()
    }

    fun setSuppressDelaySec(seconds: Float) {
        suppressDelayMs = (seconds.coerceIn(0f, 5f) * 1000f).toLong()
        if (suppressDelayMs == 0L) {
            suppressUntilMs = 0L
        }
    }

    fun setCommunicationMode(enabled: Boolean) {
        useCommunicationMode = enabled
        player.setUseCommunicationAttributes(enabled)
        if (recorder != null) {
            applyCommunicationMode(enabled)
        }
    }

    fun setPreferredInputType(type: Int) {
        preferredInputType = type
        recorder?.let { rec ->
            applyInputRoutePreference(rec)
            reportInputDevice(rec)
        }
    }

    fun setPreferredOutputType(type: Int) {
        preferredOutputType = type
        player.setPreferredOutputType(type)
        if (recorder != null) {
            applyOutputRoutePreference()
        }
    }

    fun setUseAec3(enabled: Boolean) {
        useAec3 = enabled
        if (!enabled) {
            aec3?.release()
            aec3 = null
            notifyAec3Status("未启用")
            notifyAec3Diag("AEC3 诊断：未启用")
        } else {
            notifyAec3Status("初始化中")
            ensureAec3()
        }
    }

    suspend fun setAsrRecognitionLanguage(language: String) {
        recorderMutex.withLock {
            val normalized = AsrRecognitionLanguage.normalize(language)
            if (asrRecognitionLanguage == normalized) return@withLock
            asrRecognitionLanguage = normalized
            reloadAsrLocked("recognitionLanguage=$normalized")
        }
    }

    suspend fun setListeningRecognitionEnabled(enabled: Boolean, language: String) {
        recorderMutex.withLock recorderLock@ {
            val normalizedLanguage = AsrRecognitionLanguage.normalize(language)
            val sharesMainRecognizer = normalizedLanguage == asrRecognitionLanguage
            if (
                listeningRecognitionEnabled == enabled &&
                listeningRecognitionLanguage == normalizedLanguage &&
                (
                    !enabled ||
                        (sharesMainRecognizer && asr != null && listeningAsr == null) ||
                        (!sharesMainRecognizer && listeningAsr != null)
                    )
            ) {
                return@recorderLock
            }
            val recognitionModeChanged = listeningRecognitionEnabled != enabled
            listeningRecognitionEnabled = enabled
            if (!enabled) listeningCapturePaused = false
            listeningRecognitionLanguage = normalizedLanguage
            recognitionInputEpoch.incrementAndGet()
            lastStreamingDecodeAtMs = 0L
            streamingDecodeGeneration.incrementAndGet()
            streamingDecodeBusy.set(false)
            if (recognitionModeChanged) {
                releaseSileroVadProcessor()
            }
            listeningAsrDecodeMutex.withLock listenerLock@ {
                if (!enabled || sharesMainRecognizer) {
                    runCatching { listeningAsr?.close() }
                    listeningAsr = null
                    return@listenerLock
                }
                val asrDir = currentAsrDir ?: return@listenerLock
                runCatching { listeningAsr?.close() }
                listeningAsr = try {
                    moduleFactory.createAsr(context, asrDir, listeningRecognitionLanguage)
                } catch (error: Throwable) {
                    listeningRecognitionEnabled = false
                    AppLogger.e("Listening ASR load failed", error)
                    notifyError("聆听模式识别资源加载失败")
                    null
                }
            }
        }
    }

    fun setMainRecognitionEnabled(enabled: Boolean) {
        mainRecognitionEnabled = enabled
    }

    fun setListeningCapturePaused(paused: Boolean) {
        if (listeningCapturePaused == paused) return
        listeningCapturePaused = paused
        recognitionInputEpoch.incrementAndGet()
        lastStreamingDecodeAtMs = 0L
        streamingDecodeGeneration.incrementAndGet()
        AppLogger.i("Listening capture ${if (paused) "paused for FAB" else "resumed"}")
    }

    private suspend fun reloadAsrLocked(reason: String) {
        val asrDir = currentAsrDir
        val shouldResume = recorder != null
        if (shouldResume) {
            stopRecorderOnlyLocked(
                flushPendingAudio = false,
                cancelPendingRecognition = true
            )
        }
        runCatching { asr?.close() }
        asr = null
        runCatching { listeningAsr?.close() }
        listeningAsr = null
        if (asrDir != null) {
            try {
                asr = moduleFactory.createAsr(
                    context,
                    asrDir,
                    asrRecognitionLanguage
                )
                if (listeningRecognitionEnabled && !listeningUsesMainRecognizer()) {
                    listeningAsr = moduleFactory.createAsr(
                        context,
                        asrDir,
                        listeningRecognitionLanguage
                    )
                }
                AppLogger.i("ASR reloaded $reason")
            } catch (t: Throwable) {
                AppLogger.e("ASR configuration reload failed: $reason", t)
                notifyError("语音识别资源重新加载失败，请稍后重试")
            }
        }
        if (shouldResume && asr != null) {
            SherpaSpeechEnhancer.resetStreaming()
            ensureAec3()
            startRecorderLoop()
        }
    }

    private fun normalizeVadFlags(
        classicEnabled: Boolean,
        sileroEnabled: Boolean
    ): Pair<Boolean, Boolean> {
        return if (!classicEnabled && !sileroEnabled) {
            true to false
        } else {
            classicEnabled to sileroEnabled
        }
    }

    fun setClassicVadEnabled(enabled: Boolean) {
        val (classicEnabled, sileroEnabled) = normalizeVadFlags(enabled, sileroVadEnabled)
        classicVadEnabled = classicEnabled
        sileroVadEnabled = sileroEnabled
    }

    fun setSileroVadEnabled(enabled: Boolean) {
        val (classicEnabled, sileroEnabled) = normalizeVadFlags(classicVadEnabled, enabled)
        classicVadEnabled = classicEnabled
        sileroVadEnabled = sileroEnabled
        if (!sileroEnabled) {
            resetSileroVadProcessor()
        }
    }

    fun setSileroVadThreshold(threshold: Float) {
        val normalized = threshold.coerceIn(
            UserPrefs.SILERO_VAD_MIN_THRESHOLD,
            UserPrefs.SILERO_VAD_MAX_THRESHOLD
        )
        if (sileroVadThreshold == normalized) return
        sileroVadThreshold = normalized
        releaseSileroVadProcessor()
    }

    fun setSileroVadPreRollMs(preRollMs: Int) {
        sileroVadPreRollMs = preRollMs.coerceIn(
            UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS,
            UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS
        )
        trimSileroPreRollSamples()
    }

    fun setPushToTalkStreamingEnabled(enabled: Boolean) {
        val wasEnabled = pttStreamingEnabled
        pttStreamingEnabled = enabled
        if (!enabled) {
            lastStreamingDecodeAtMs = 0L
            streamingDecodeGeneration.incrementAndGet()
            streamingDecodeBusy.set(false)
            if (wasEnabled && sileroVadEnabled) {
                drainSileroVadSegments(flush = true).forEach { segment ->
                    val audio = prependPendingSileroPreRoll(segment)
                    if (classicVadEnabled && !passesClassicVadGate(audio)) return@forEach
                    processRecognizedSegment(audio)
                }
            }
        }
    }

    fun setSuppressAsrAutoSpeak(enabled: Boolean) {
        suppressAsrAutoSpeak = enabled
    }

    fun isMicActive(): Boolean {
        return recorder != null
    }

    private fun nextResultId(): Long {
        synchronized(queueLock) {
            return nextUtteranceId++
        }
    }

    fun setAllowSystemAecWithAec3(enabled: Boolean) {
        allowSystemAecWithAec3 = enabled
    }

    fun setSpeakerVerifyEnabled(enabled: Boolean) {
        speakerVerifyEnabled = enabled
        resetSpeakerVerifyGateState()
    }

    fun resetSpeakerVerificationSession() {
        resetSpeakerVerifyGateState()
    }

    fun setSpeakerVerifyThreshold(threshold: Float) {
        setSpeakerVerifyTolerance(SpeakerVerificationTolerance.fromThreshold(threshold).index)
    }

    fun setSpeakerVerifyTolerance(level: Int) {
        speakerVerifyTolerance = SpeakerVerificationTolerance.fromIndex(level)
        synchronized(speakerVerifyLock) {
            updateAdaptiveSpeakerThresholdsLocked()
        }
        resetSpeakerVerifyGateState()
    }

    fun setExperimentalRecognitionSensitivity(sensitivity: Int) {
        experimentalRecognitionSensitivity = sensitivity.coerceIn(0, 100)
        resetSpeakerVerifyGateState()
    }

    fun setExperimentalTargetSpeakerBackend(backend: Int) {
        experimentalTargetSpeakerBackend = ExperimentalTargetSpeakerBackend.normalize(backend)
        neuralSeparatorPerformanceChecked = false
        neuralSeparatorTooSlowForAuto = false
        resetSpeakerVerifyGateState()
    }

    fun setSpeakerProfiles(
        profiles: List<FloatArray>,
        neuralProfiles: List<FloatArray?> = emptyList(),
        confirmationProfiles: List<FloatArray?> = emptyList()
    ) {
        rebuildSpeakerVerifyState(profiles, neuralProfiles, confirmationProfiles)
        resetSpeakerVerifyGateState()
    }

    fun clearSpeakerProfiles() {
        rebuildSpeakerVerifyState(emptyList(), emptyList())
    }

    fun hasSpeakerProfiles(): Boolean {
        return speakerProfiles.isNotEmpty()
    }

    fun setSpeakerProfile(profile: FloatArray?) {
        setSpeakerProfiles(if (profile == null || profile.isEmpty()) emptyList() else listOf(profile))
    }

    fun clearSpeakerProfile() {
        clearSpeakerProfiles()
    }

    fun hasSpeakerProfile(): Boolean {
        return hasSpeakerProfiles()
    }

    fun latestSpeakerSimilarity(): Float {
        return speakerLastSimilarity
    }

    fun releaseNeuralSpeakerFilterResources() {
        synchronized(neuralSeparatorLock) {
            runCatching { neuralSeparator?.close() }
            neuralSeparator = null
            neuralSeparatorModelPath = null
        }
        SpeechBrainEcapaEmbedder.releaseModel()
        neuralSeparatorPerformanceChecked = false
        neuralSeparatorTooSlowForAuto = false
    }

    private fun rebuildSpeakerVerifyState(
        profiles: List<FloatArray>,
        neuralProfiles: List<FloatArray?>,
        confirmationProfiles: List<FloatArray?> = emptyList()
    ) {
        val normalizedPairs = profiles.mapIndexedNotNull { index, profile ->
            if (profile.isEmpty()) {
                null
            } else {
                Triple(
                    profile.copyOf(),
                    neuralProfiles.getOrNull(index)
                        ?.takeIf { it.size == NEURAL_SPEAKER_EMBEDDING_DIM }
                        ?.copyOf(),
                    confirmationProfiles.getOrNull(index)
                        ?.takeIf { it.isNotEmpty() }
                        ?.copyOf()
                )
            }
        }
        val normalizedProfiles = normalizedPairs.map { it.first }
        val normalizedNeuralProfiles = normalizedPairs.map { it.second }
        val normalizedConfirmationProfiles = normalizedPairs.map { it.third }
        synchronized(speakerVerifyLock) {
            releaseSpeakerVerifyStateLocked()
            speakerProfiles = normalizedProfiles
            neuralSpeakerProfiles = normalizedNeuralProfiles
            speakerConfirmationProfiles = normalizedConfirmationProfiles
            speakerVerifyReferenceProfile = SpeakerVerifier.combineProfilesOfficialStyle(normalizedProfiles)
            updateAdaptiveSpeakerThresholdsLocked()
        }
        AppLogger.i(
            "Speaker thresholds primary=$adaptivePrimarySpeakerThreshold " +
                "confirmation=$adaptiveConfirmationSpeakerThreshold " +
                "confirmationProfiles=${normalizedConfirmationProfiles.count { it != null }}"
        )
        speakerLastSimilarity = -1f
    }

    private fun updateAdaptiveSpeakerThresholdsLocked() {
        val tolerance = speakerVerifyTolerance
        adaptivePrimarySpeakerThreshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = speakerProfiles,
            defaultThreshold = tolerance.primaryThreshold,
            minimum = 0.38f,
            maximum = 0.68f,
            maxRelaxation = tolerance.adaptiveRelaxation
        )
        adaptiveConfirmationSpeakerThreshold = AdaptiveSpeakerVerificationPolicy.calibratedThreshold(
            profiles = speakerConfirmationProfiles.mapNotNull { it },
            defaultThreshold = tolerance.confirmationThreshold,
            minimum = 0.46f,
            maximum = 0.74f,
            maxRelaxation = tolerance.adaptiveRelaxation
        )
    }

    private fun releaseSpeakerVerifyStateLocked() {
        speakerVerifyReferenceProfile = null
    }

    private fun resetSpeakerVerifyGateState() {
        synchronized(speakerVerifySessionLock) {
            speakerVerifyPendingSegments.clear()
            speakerVerifyPendingSamples = 0
            speakerVerifyPendingSampleRate = sampleRate
            speakerVerifyLastSegmentAtMs = 0L
            speakerVerifySessionPassed = false
        }
        experimentalTargetSpeakerFrontend.reset()
    }

    private fun clearSpeakerVerifyPendingLocked() {
        speakerVerifyPendingSegments.clear()
        speakerVerifyPendingSamples = 0
        speakerVerifyPendingSampleRate = sampleRate
    }

    private fun appendSpeakerVerifyPendingLocked(segment: RecognitionSegment) {
        if (speakerVerifyPendingSegments.isNotEmpty() && speakerVerifyPendingSampleRate != segment.sampleRate) {
            clearSpeakerVerifyPendingLocked()
        }
        speakerVerifyPendingSampleRate = segment.sampleRate
        speakerVerifyPendingSegments.add(segment)
        speakerVerifyPendingSamples += segment.audio.size
        val maxSamples = segment.sampleRate * SPEAKER_VERIFY_MAX_PENDING_MS / 1000
        while (speakerVerifyPendingSamples > maxSamples && speakerVerifyPendingSegments.isNotEmpty()) {
            val removed = speakerVerifyPendingSegments.removeAt(0)
            speakerVerifyPendingSamples -= removed.audio.size
        }
    }

    private fun combineRecognitionSegments(segments: List<RecognitionSegment>): RecognitionSegment {
        if (segments.size == 1) return segments.first()
        val sampleRate = segments.firstOrNull()?.sampleRate ?: this.sampleRate
        val totalSamples = segments.sumOf { it.audio.size }
        val out = FloatArray(totalSamples)
        var offset = 0
        var rms = 0.0
        segments.forEach { segment ->
            System.arraycopy(segment.audio, 0, out, offset, segment.audio.size)
            offset += segment.audio.size
            rms = max(rms, segment.rms)
        }
        return RecognitionSegment(out, sampleRate, rms)
    }

    private fun prepareExperimentalTargetAttempt(
        segment: RecognitionSegment,
        nowMs: Long
    ): SpeakerVerifyAttempt? {
        var resetFrontend = false
        val attempt = synchronized(speakerVerifySessionLock) {
            if (nowMs - speakerVerifyLastSegmentAtMs > SPEAKER_VERIFY_SESSION_RESET_MS) {
                clearSpeakerVerifyPendingLocked()
                resetFrontend = true
            }
            speakerVerifyLastSegmentAtMs = nowMs
            appendSpeakerVerifyPendingLocked(segment)
            val minimumSamples = experimentalTargetSpeakerFrontend.minimumSamples(segment.sampleRate)
            if (speakerVerifyPendingSamples < minimumSamples) {
                return@synchronized null
            }
            SpeakerVerifyAttempt(
                segment = combineRecognitionSegments(speakerVerifyPendingSegments),
                samples = speakerVerifyPendingSamples
            )
        }
        if (resetFrontend) experimentalTargetSpeakerFrontend.reset()
        return attempt
    }

    private fun markSpeakerVerifySessionPassed(nowMs: Long) {
        synchronized(speakerVerifySessionLock) {
            clearSpeakerVerifyPendingLocked()
            speakerVerifySessionPassed = true
            speakerVerifyLastSegmentAtMs = nowMs
        }
    }

    private fun canReuseSpeakerVerifySession(segment: RecognitionSegment, nowMs: Long): Boolean {
        return synchronized(speakerVerifySessionLock) {
            val durationMs = segment.audio.size * 1000 / segment.sampleRate.coerceAtLeast(1)
            val reusable = AdaptiveSpeakerVerificationPolicy.canReuseVerifiedSession(
                sessionPassed = speakerVerifySessionPassed,
                lastVerifiedAtMs = speakerVerifyLastSegmentAtMs,
                nowMs = nowMs,
                utteranceDurationMs = durationMs
            )
            reusable
        }
    }

    private fun isSpeakerVerifySessionReadyForPreview(): Boolean {
        if (!speakerVerifyEnabled || speakerProfiles.isEmpty()) return true
        val sessionReady = synchronized(speakerVerifySessionLock) {
            speakerVerifySessionPassed &&
                SystemClock.uptimeMillis() - speakerVerifyLastSegmentAtMs <= SPEAKER_VERIFY_SESSION_RESET_MS
        }
        if (sessionReady) return true
        return experimentalTargetSpeakerFrontend.isTargetActive()
    }

    private fun resolveSpeakerGate(segment: RecognitionSegment): SpeakerGateResult {
        if (!speakerVerifyEnabled || speakerProfiles.isEmpty()) {
            resetSpeakerVerifyGateState()
            return SpeakerGateResult.Ready(listOf(segment))
        }
        val nowMs = SystemClock.uptimeMillis()
        if (canReuseSpeakerVerifySession(segment, nowMs)) {
            AppLogger.i("Speaker verification reused recent owner session")
            return SpeakerGateResult.Ready(listOf(segment))
        }
        return resolveExperimentalTargetSpeakerGate(segment, nowMs)
    }

    private fun resolveExperimentalTargetSpeakerGate(
        segment: RecognitionSegment,
        nowMs: Long
    ): SpeakerGateResult {
        val profileSnapshot = speakerProfiles
        if (!speakerVerifyEnabled || profileSnapshot.isEmpty()) {
            resetSpeakerVerifyGateState()
            return SpeakerGateResult.Ready(listOf(segment))
        }
        val attempt = prepareExperimentalTargetAttempt(segment, nowMs)
            ?: return SpeakerGateResult.Pending
        val confirmationAvailable = speakerConfirmationProfiles.any { it?.isNotEmpty() == true } &&
            SpeakerVerifier.confirmationModelAvailable(context)
        val primaryThreshold = adaptivePrimarySpeakerThreshold
        val selection = experimentalTargetSpeakerFrontend.process(
            audio = attempt.segment.audio,
            sampleRate = attempt.segment.sampleRate,
            threshold = AdaptiveSpeakerVerificationPolicy.candidateThreshold(primaryThreshold)
        ) { window ->
            val embedding = SpeakerVerifier.computeEmbedding(
                context,
                window,
                attempt.segment.sampleRate
            )
                ?: return@process null
            synchronized(speakerVerifyLock) {
                val references = buildList {
                    speakerVerifyReferenceProfile?.let(::add)
                    addAll(speakerProfiles)
                }
                references.maxOfOrNull { reference ->
                    SpeakerVerifier.cosineSimilarity(reference, embedding)
                }
            }
        }
        val primaryDecision = AdaptiveSpeakerVerificationPolicy.primaryDecision(
            similarity = selection.bestSimilarity,
            threshold = primaryThreshold,
            confirmationAvailable = confirmationAvailable
        )
        val confirmationSimilarity = if (
            selection.targetDetected && primaryDecision == PrimarySpeakerDecision.CONFIRM
        ) {
            SpeakerVerifier.computeConfirmationEmbedding(
                context,
                selection.audio ?: attempt.segment.audio,
                attempt.segment.sampleRate
            )?.let(::bestConfirmationSpeakerSimilarity)
        } else {
            null
        }
        val identityAccepted = when (primaryDecision) {
            PrimarySpeakerDecision.ACCEPT -> selection.targetDetected
            PrimarySpeakerDecision.REJECT -> false
            PrimarySpeakerDecision.CONFIRM -> selection.targetDetected &&
                confirmationSimilarity != null &&
                AdaptiveSpeakerVerificationPolicy.confirmationAccepted(
                    primarySimilarity = selection.bestSimilarity,
                    primaryThreshold = primaryThreshold,
                    confirmationSimilarity = confirmationSimilarity,
                    confirmationThreshold = adaptiveConfirmationSpeakerThreshold
                )
        }
        if (selection.bestSimilarity >= 0f) {
            speakerLastSimilarity = selection.bestSimilarity
            notifySpeakerVerify(selection.bestSimilarity, identityAccepted)
        }
        AppLogger.i(
            "Adaptive speaker decision=$primaryDecision primary=${selection.bestSimilarity} " +
                "primaryThreshold=$primaryThreshold confirmation=$confirmationSimilarity " +
                "confirmationThreshold=$adaptiveConfirmationSpeakerThreshold accepted=$identityAccepted"
        )
        val guardedSelection = selection.audio
            ?.takeIf { identityAccepted && it.isNotEmpty() }
            ?.let { lightweightAudio ->
            applyNeuralTargetSpeakerSeparation(
                originalAudio = attempt.segment.audio,
                sampleRate = attempt.segment.sampleRate,
                lightweightAudio = lightweightAudio,
                baselineSimilarity = selection.bestSimilarity
            )
        }
        val filteredAudio = guardedSelection?.audio
        guardedSelection?.similarity?.takeIf { it >= 0f }?.let { similarity ->
            speakerLastSimilarity = similarity
            notifySpeakerVerify(similarity, true)
        }
        if (filteredAudio != null && filteredAudio.isNotEmpty()) {
            markSpeakerVerifySessionPassed(nowMs)
            AppLogger.i(
                "Experimental target speaker accepted similarity=${selection.bestSimilarity} " +
                    "windows=${selection.evaluatedWindows}"
            )
            return SpeakerGateResult.Ready(
                listOf(
                    RecognitionSegment(
                        audio = filteredAudio,
                        sampleRate = attempt.segment.sampleRate,
                        rms = rmsEnergy(filteredAudio)
                    )
                )
            )
        }
        // RecognitionSegment is emitted after VAD/endpointing has completed the
        // utterance. A failed decision must not wait for an unrelated sentence.
        synchronized(speakerVerifySessionLock) {
            clearSpeakerVerifyPendingLocked()
        }
        experimentalTargetSpeakerFrontend.reset()
        AppLogger.i(
            "Experimental target speaker dropped audio similarity=${selection.bestSimilarity} " +
                "windows=${selection.evaluatedWindows}"
        )
        return SpeakerGateResult.Pending
    }

    private fun applyNeuralTargetSpeakerSeparation(
        originalAudio: FloatArray,
        sampleRate: Int,
        lightweightAudio: FloatArray,
        baselineSimilarity: Float
    ): GuardedSpeakerAudio {
        val backend = experimentalTargetSpeakerBackend
        if (backend == ExperimentalTargetSpeakerBackend.LIGHTWEIGHT) {
            return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        }
        if (backend == ExperimentalTargetSpeakerBackend.AUTO && neuralSeparatorTooSlowForAuto) {
            return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        }
        if (!NeuralSeparationActivationPolicy.shouldRun(
                mode = backend,
                baselineSimilarity = baselineSimilarity,
                verificationThreshold = adaptivePrimarySpeakerThreshold
            )
        ) {
            return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        }
        val condition = combinedNeuralSpeakerProfile()
            ?: return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        val resources = neuralSpeakerFilterResources.status()
        if (!ExperimentalTargetSpeakerBackend.shouldUseNeural(backend, resources.installed, true)) {
            return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        }
        val modelFile = resources.tseModel
            ?: return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        val separator = ensureNeuralSeparator(modelFile)
            ?: return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        if (backend == ExperimentalTargetSpeakerBackend.AUTO && !neuralSeparatorPerformanceChecked) {
            val probe = separator.separate(
                FloatArray(NEURAL_PERFORMANCE_PROBE_SAMPLES),
                NeuralTargetSpeakerSeparator.MODEL_SAMPLE_RATE,
                condition
            )
            neuralSeparatorPerformanceChecked = true
            val probeRtf = probe?.realtimeFactor ?: Float.POSITIVE_INFINITY
            if (NeuralSeparationPerformancePolicy.shouldDisableAuto(probeRtf)) {
                neuralSeparatorTooSlowForAuto = true
                AppLogger.i(
                    "Neural target speaker separator skipped for auto mode after probe: rtf=$probeRtf"
                )
                return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
            }
        }
        val result = separator.separate(originalAudio, sampleRate, condition)
            ?: return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        if (backend == ExperimentalTargetSpeakerBackend.AUTO &&
            NeuralSeparationPerformancePolicy.shouldDisableAuto(result.realtimeFactor)
        ) {
            neuralSeparatorTooSlowForAuto = true
            AppLogger.i(
                "Neural target speaker separator disabled for auto mode: rtf=${result.realtimeFactor}"
            )
        }
        val candidate = result.audio
        val candidateEmbedding = SpeakerVerifier.computeEmbedding(context, candidate, sampleRate)
            ?: return GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        val candidateSimilarity = bestSpeakerSimilarity(candidateEmbedding)
        val originalRms = rmsEnergy(originalAudio).coerceAtLeast(1e-7)
        val candidateRms = rmsEnergy(candidate)
        val energyRatio = candidateRms / originalRms
        val accepted = NeuralSeparationQualityPolicy.accepts(
            candidateSimilarity = candidateSimilarity,
            baselineSimilarity = baselineSimilarity,
            verificationThreshold = adaptivePrimarySpeakerThreshold,
            energyRatio = energyRatio
        )
        AppLogger.i(
            "Neural target speaker result accepted=$accepted similarity=$candidateSimilarity " +
                "baseline=$baselineSimilarity energyRatio=$energyRatio rtf=${result.realtimeFactor}"
        )
        return if (accepted) {
            GuardedSpeakerAudio(candidate, candidateSimilarity)
        } else {
            GuardedSpeakerAudio(lightweightAudio, baselineSimilarity)
        }
    }

    private fun combinedNeuralSpeakerProfile(): FloatArray? {
        val profiles = neuralSpeakerProfiles.mapNotNull { profile ->
            profile?.takeIf { it.size == NEURAL_SPEAKER_EMBEDDING_DIM }
        }
        if (profiles.isEmpty()) return null
        val combined = FloatArray(NEURAL_SPEAKER_EMBEDDING_DIM)
        profiles.forEach { profile ->
            for (index in combined.indices) combined[index] += profile[index]
        }
        val scale = 1f / profiles.size
        for (index in combined.indices) combined[index] *= scale
        return combined
    }

    private fun bestSpeakerSimilarity(embedding: FloatArray): Float {
        return synchronized(speakerVerifyLock) {
            buildList {
                speakerVerifyReferenceProfile?.let(::add)
                addAll(speakerProfiles)
            }.maxOfOrNull { reference ->
                SpeakerVerifier.cosineSimilarity(reference, embedding)
            } ?: -1f
        }
    }

    private fun bestConfirmationSpeakerSimilarity(embedding: FloatArray): Float {
        return synchronized(speakerVerifyLock) {
            speakerConfirmationProfiles.mapNotNull { it }.maxOfOrNull { reference ->
                SpeakerVerifier.cosineSimilarity(reference, embedding)
            } ?: -1f
        }
    }

    private fun ensureNeuralSeparator(modelFile: File): NeuralTargetSpeakerSeparator? {
        return synchronized(neuralSeparatorLock) {
            val path = modelFile.absolutePath
            neuralSeparator?.takeIf { neuralSeparatorModelPath == path }?.let { return@synchronized it }
            runCatching { neuralSeparator?.close() }
            neuralSeparator = null
            neuralSeparatorModelPath = null
            neuralSeparatorPerformanceChecked = false
            neuralSeparatorTooSlowForAuto = false
            runCatching { NeuralTargetSpeakerSeparator(modelFile) }
                .onFailure { AppLogger.e("Neural target speaker separator load failed", it) }
                .getOrNull()
                ?.also {
                    neuralSeparator = it
                    neuralSeparatorModelPath = path
                }
        }
    }

    private fun enqueueTts(text: String): Long {
        SoundboardManager.interruptForTtsPlayback()
        val id = nextUtteranceId++
        synchronized(queueLock) {
            ttsQueue.addLast(QueuedTts(id, text))
        }
        return id
    }

    fun isTtsReadyFor(voiceDir: File): Boolean {
        val loadedTts = tts ?: return false
        val loadedDir = currentVoiceDir ?: return false
        return loadedTts.sampleRate > 0 && loadedDir.absolutePath == voiceDir.absolutePath
    }

    fun enqueueSpeakTextPendingTts(text: String, interruptCurrent: Boolean = false): Long? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null
        if (interruptCurrent) {
            ttsPlaybackGeneration.incrementAndGet()
            val activeJob = ttsJob
            ttsJob = null
            synchronized(queueLock) {
                ttsQueue.clear()
            }
            runCatching { player.stop() }
            activeJob?.cancel()
        }
        val id = enqueueTts(normalized)
        notifyResult(id, normalized)
        return id
    }

    private suspend fun stopTtsPlaybackLocked(clearQueue: Boolean) {
        ttsPlaybackGeneration.incrementAndGet()
        val activeJob = ttsJob
        ttsJob = null
        if (clearQueue) {
            synchronized(queueLock) {
                ttsQueue.clear()
            }
        }
        player.stop()
        if (activeJob != null) {
            try {
                activeJob.cancel()
                activeJob.join()
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureTtsLoop() {
        if (ttsJob?.isActive == true) return
        val loopGeneration = ttsPlaybackGeneration.get()
        ttsJob = scope.launch(Dispatchers.IO) {
            while (isActive && loopGeneration == ttsPlaybackGeneration.get()) {
                val next = synchronized(queueLock) {
                    if (ttsQueue.isNotEmpty()) ttsQueue.removeFirst() else null
                } ?: break
                try {
                    notifyProgress(next.id, 0f)
                    val ttsEngine = tts
                    val pcm = if (ttsEngine != null) {
                        if (ttsEngine is PiperTtsEngine) {
                            withAndroidThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) {
                                synthesizeByPunctuation(ttsEngine, next.text)
                            }
                        } else {
                            synthesizeByPunctuation(ttsEngine, next.text)
                        }
                    } else {
                        FloatArray(0)
                    }
                    if (!isActive || loopGeneration != ttsPlaybackGeneration.get()) {
                        break
                    }
                    if (pcm.isNotEmpty()) {
                        player.play(pcm, tts?.sampleRate ?: 22050) { progress ->
                            if (loopGeneration == ttsPlaybackGeneration.get()) {
                                notifyProgress(next.id, progress)
                            }
                        }
                        if (suppressDelayMs > 0L) {
                            suppressUntilMs = SystemClock.uptimeMillis() + suppressDelayMs
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("TTS failed", e)
                    notifyError("语音朗读失败，请检查语音合成设置")
                } finally {
                    notifyProgress(next.id, 1f)
                }
            }
        }
    }

    private fun ensureAec3() {
        if (!useAec3) return
        if (aec3 != null) return
        val renderRate = tts?.sampleRate ?: sampleRate
        if (renderRate != sampleRate) {
            AppLogger.i("AEC3 render rate $renderRate resampled to $sampleRate")
        }
        val proc = Aec3Processor(sampleRate)
        if (proc.isReady()) {
            aec3 = proc
            AppLogger.i("AEC3 enabled capture=${sampleRate}")
            notifyAec3Status("已启用")
            notifyAec3Diag("AEC3 诊断：已启用，等待渲染参考")
        } else {
            proc.release()
            AppLogger.e("AEC3 init failed")
            notifyAec3Status("初始化失败")
            notifyAec3Diag("AEC3 诊断：初始化失败")
            notifyError("AEC3 初始化失败")
        }
    }

    suspend fun loadAsr(asrDir: File): Boolean {
        return recorderMutex.withLock {
            if (asr != null && currentAsrDir?.absolutePath == asrDir.absolutePath) {
                return@withLock true
            }

            val shouldResume = recorder != null
            if (shouldResume) {
                stopRecorderOnlyLocked(
                    flushPendingAudio = false,
                    cancelPendingRecognition = true
                )
            }
            var nextAsr: AsrModule? = null
            var nextListeningAsr: AsrModule? = null
            try {
                nextAsr = moduleFactory.createAsr(
                    context,
                    asrDir,
                    asrRecognitionLanguage
                )
                if (listeningRecognitionEnabled && !listeningUsesMainRecognizer()) {
                    nextListeningAsr = moduleFactory.createAsr(
                        context,
                        asrDir,
                        listeningRecognitionLanguage
                    )
                }
            } catch (e: Throwable) {
                runCatching { nextAsr?.close() }
                runCatching { nextListeningAsr?.close() }
                if (shouldResume && asr != null) {
                    SherpaSpeechEnhancer.resetStreaming()
                    ensureAec3()
                    startRecorderLoop()
                }
                AppLogger.e("ASR load failed", e)
                notifyError("语音识别资源加载失败，请检查资源包")
                return@withLock false
            }

            val previousAsr = asr
            val previousListeningAsr = listeningAsr
            asr = nextAsr
            listeningAsr = nextListeningAsr
            currentAsrDir = asrDir
            releaseSileroVadProcessor()
            currentSileroVadModelFile = resolveSileroVadModel(asrDir)
                ?: RecognitionResourceRepository.resolveSileroVadModel(context)
            runCatching { previousAsr?.close() }
            runCatching { previousListeningAsr?.close() }
            AppLogger.i("ASR loaded dir=${asrDir.absolutePath}")
            if (shouldResume) {
                SherpaSpeechEnhancer.resetStreaming()
                ensureAec3()
                startRecorderLoop()
            }
            true
        }
    }

    suspend fun loadTts(voiceDir: File): Boolean {
        return recorderMutex.withLock {
            try {
                if (tts == null || currentVoiceDir?.absolutePath != voiceDir.absolutePath) {
                    val previousTts = tts
                    stopTtsPlaybackLocked(clearQueue = previousTts != null)
                    val nextTts = moduleFactory.createTts(context, voiceDir)
                    applyTtsSynthesisTuning(nextTts)
                    tts = nextTts
                    currentVoiceDir = voiceDir
                    previousTts?.close()
                    AppLogger.i("TTS loaded dir=${voiceDir.absolutePath}")
                    if (useAec3) {
                        aec3?.release()
                        aec3 = null
                        ensureAec3()
                    }
                    if (synchronized(queueLock) { ttsQueue.isNotEmpty() }) {
                        ensureTtsLoop()
                    }
                }
            } catch (e: Throwable) {
                AppLogger.e("TTS load failed", e)
                notifyError(
                    if (isSystemTtsVoiceDir(voiceDir)) {
                        "系统语音合成初始化失败，请先完成系统语音合成设置"
                    } else {
                        "语音合成引擎加载失败，请检查语音包"
                    }
                )
                return@withLock false
            }
            true
        }
    }

    suspend fun enrollSpeaker(
        durationSec: Float = 4f,
        onCapture: ((progress: Float, level: Float) -> Unit)? = null
    ): SpeakerEnrollResult {
        return recorderMutex.withLock {
            AppLogger.i("Speaker enroll start durationSec=$durationSec sampleRate=$sampleRate")
            if (recorder != null) {
                return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "请先停止麦克风再注册说话人"
                )
            }
            val seconds = durationSec.coerceIn(2f, 8f)
            val sampleCount = (sampleRate * seconds).roundToInt().coerceAtLeast(sampleRate)
            onCapture?.invoke(0f, 0f)
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = createSpeechAudioRecord(max(minBuf, 4096))
            if (rec == null) {
                return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "声纹录制失败：录音初始化失败"
                )
            }
            applyInputRoutePreference(rec)
            applyPreferredMicrophoneDirection(rec)
            val temp = ShortArray(1024)
            val captured = FloatArray(sampleCount)
            var offset = 0
            var levelEma = 0f
            try {
                rec.startRecording()
                while (offset < sampleCount) {
                    val read = rec.read(temp, 0, min(temp.size, sampleCount - offset))
                    if (read <= 0) continue
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val v = temp[i] / 32768f
                        captured[offset + i] = v
                        sumSq += v * v
                    }
                    offset += read
                    val chunkRms = if (read > 0) sqrt(sumSq / read).toFloat() else 0f
                    levelEma = levelEma * 0.82f + chunkRms * 0.18f
                    val normalizedLevel = (levelEma / 0.2f).coerceIn(0f, 1f)
                    val progress = (offset.toFloat() / sampleCount.toFloat()).coerceIn(0f, 1f)
                    onCapture?.invoke(progress, normalizedLevel)
                }
            } catch (e: Exception) {
                AppLogger.e("Speaker enroll read failed", e)
                return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "声纹录制失败，请检查麦克风后重试"
                )
            } finally {
                try {
                    rec.stop()
                } catch (_: Exception) {
                }
                try {
                    rec.release()
                } catch (_: Exception) {
                }
            }
            if (offset < sampleRate / 2) {
                return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "声纹录制失败：录音时长不足"
                )
            }
            onCapture?.invoke(1f, 0f)
            val audio = if (offset == captured.size) captured else captured.copyOf(offset)
            val assessment = SpeakerEnrollmentQualityPolicy.assess(audio, sampleRate)
            AppLogger.i(
                "Speaker enroll captured samples=${audio.size} activeRatio=${assessment.activeRatio} " +
                    "snrDb=${assessment.estimatedSnrDb} clipping=${assessment.clippingRatio}"
            )
            val enrollmentAudio = assessment.audio
            if (!assessment.accepted || enrollmentAudio == null) {
                return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "声纹录制失败：${assessment.message ?: "有效语音不足"}"
                )
            }
            val embedding = SpeakerVerifier.computeEmbedding(context, enrollmentAudio, sampleRate)
                ?: return@withLock SpeakerEnrollResult(
                    success = false,
                    message = "声纹录制失败：有效语音不足"
                )
            AppLogger.i("Speaker enroll embedding dim=${embedding.size}")
            val confirmationEmbedding = SpeakerVerifier.computeConfirmationEmbedding(
                context,
                enrollmentAudio,
                sampleRate
            )
            val neuralResources = neuralSpeakerFilterResources.status()
            val neuralEmbedding = neuralResources.ecapaModel?.let { modelFile ->
                try {
                    SpeechBrainEcapaEmbedder.compute(context, modelFile, enrollmentAudio, sampleRate)
                } finally {
                    SpeechBrainEcapaEmbedder.releaseModel()
                }
            }
            SpeakerEnrollResult(
                success = true,
                message = "声纹录制成功",
                profile = embedding,
                confirmationProfile = confirmationEmbedding,
                neuralProfile = neuralEmbedding
            )
        }
    }

    suspend fun startMic(requireTts: Boolean = !listeningRecognitionEnabled): Boolean {
        return recorderMutex.withLock {
            if (asr == null) {
                notifyError("语音识别资源未就绪，请先安装语音识别资源")
                return@withLock false
            }
            if (tts == null && requireTts) {
                notifyError("语音合成未就绪，请先选择语音包")
                return@withLock false
            }
            synchronized(queueLock) {
                lastAcceptedTtsTextKey = ""
                lastAcceptedTtsAtMs = 0L
            }
            lastStreamingDecodeAtMs = 0L
            streamingDecodeGeneration.incrementAndGet()
            streamingDecodeBusy.set(false)
            resetSpeakerVerifyGateState()
            stopRecorderOnlyLocked(
                flushPendingAudio = false,
                cancelPendingRecognition = true
            )
            SherpaSpeechEnhancer.resetStreaming()
            ensureAec3()
            startRecorderLoop()
            true
        }
    }

    suspend fun stopMic() {
        recorderMutex.withLock {
            stopRecorderOnlyLocked(
                flushPendingAudio = true,
                cancelPendingRecognition = false
            )
        }
    }

    suspend fun flushPendingRecognition() {
        val activeLoop = loopJob
        if (recorder == null || activeLoop == null || !activeLoop.isActive) {
            awaitActiveAsrJobs()
            withTimeoutOrNull(RECOGNITION_FLUSH_TIMEOUT_MS) {
                resultCallbackJobs.awaitIdle()
            }
            return
        }
        val request = CompletableDeferred<Unit>()
        val pending = if (pendingRecognitionFlush.compareAndSet(null, request)) {
            request
        } else {
            pendingRecognitionFlush.get()
        }
        if (pending != null) {
            withTimeoutOrNull(RECOGNITION_FLUSH_TIMEOUT_MS) { pending.await() }
        }
        awaitActiveAsrJobs()
        withTimeoutOrNull(RECOGNITION_FLUSH_TIMEOUT_MS) {
            resultCallbackJobs.awaitIdle()
        }
    }

    suspend fun enqueueSpeakText(text: String, interruptCurrent: Boolean = false): Long? {
        return recorderMutex.withLock {
            val normalized = text.trim()
            if (normalized.isEmpty()) return@withLock null
            if (tts == null) {
                notifyError("语音合成未就绪，请先选择语音包")
                return@withLock null
            }
            if (interruptCurrent) {
                stopTtsPlaybackLocked(clearQueue = true)
            }
            val id = enqueueTts(normalized)
            notifyResult(id, normalized)
            ensureTtsLoop()
            id
        }
    }

    suspend fun start(asrDir: File, voiceDir: File) {
        AppLogger.i("Realtime start asrDir=${asrDir.absolutePath} voiceDir=${voiceDir.absolutePath}")
        if (!loadAsr(asrDir)) return
        if (!loadTts(voiceDir)) return
        startMic()
    }

    suspend fun restartRecorder() {
        recorderMutex.withLock {
            if (asr == null) return
            if (recorder == null) return
            stopRecorderOnlyLocked(
                flushPendingAudio = true,
                cancelPendingRecognition = false
            )
            SherpaSpeechEnhancer.resetStreaming()
            ensureAec3()
            startRecorderLoop()
        }
    }

    suspend fun stop() {
        recorderMutex.withLock {
            stopRecorderOnlyLocked(
                flushPendingAudio = false,
                cancelPendingRecognition = true
            )
            stopTtsPlaybackLocked(clearQueue = true)
            releaseNoiseProcessors()
            releaseSileroVadProcessor()
            SherpaSpeechEnhancer.release()
            synchronized(speakerVerifyLock) {
                releaseSpeakerVerifyStateLocked()
            }
            SpeakerVerifier.release()
            releaseNeuralSpeakerFilterResources()
            aec3?.release()
            aec3 = null
            notifyAec3Status(if (useAec3) "待启动" else "未启用")
            AppLogger.i("Realtime stop")
        }
    }

    internal suspend fun releaseAfterSimulatedAudio() {
        stop()
        recorderMutex.withLock {
            runCatching { asr?.close() }
            asr = null
            runCatching { listeningAsr?.close() }
            listeningAsr = null
            currentAsrDir = null
            runCatching { tts?.close() }
            tts = null
            currentVoiceDir = null
            player.stop()
        }
    }

    private suspend fun stopRecorderOnlyLocked(
        flushPendingAudio: Boolean,
        cancelPendingRecognition: Boolean
    ) {
        try {
            aec?.release()
        } catch (_: Exception) {
        }
        aec = null
        val rec = recorder
        recorder = null
        recorderFlushOnStop?.set(flushPendingAudio)
        try {
            rec?.stop()
        } catch (_: Exception) {
        }
        val job = loopJob
        loopJob = null
        if (job != null) {
            try {
                job.cancel()
                job.join()
            } catch (_: Exception) {
            }
        }
        recorderFlushOnStop = null
        if (cancelPendingRecognition) {
            cancelAndJoinActiveAsrJobs()
        } else {
            awaitActiveAsrJobs()
        }
        streamingDecodeBusy.set(false)
        lastStreamingDecodeAtMs = 0L
        streamingDecodeGeneration.incrementAndGet()
        resetSpeakerVerifyGateState()
        try {
            rec?.release()
        } catch (_: Exception) {
        }
        unregisterAudioDeviceCallback()
        restoreOutputRoutePreference()
        restoreCommunicationMode()
        resetNoiseProcessors()
        resetSileroVadProcessor()
        SherpaSpeechEnhancer.resetStreaming()
        if (aec3 == null) {
            notifyAec3Status(if (useAec3) "待启动" else "未启用")
        }
    }

    private fun ensureRnNoiseProcessor(): RnNoiseProcessor? {
        synchronized(denoiserLock) {
            rnnoiseProcessor?.let { return it }
            return runCatching { RnNoiseProcessor() }
                .onFailure {
                    AppLogger.e("RNNoise init failed", it)
                    notifyError("RNNoise 初始化失败")
                    denoiserMode = AudioDenoiserMode.OFF
                }
                .getOrNull()
                ?.also { rnnoiseProcessor = it }
        }
    }

    private fun ensureSpeexNoiseProcessor(): SpeexNoiseSuppressor? {
        synchronized(denoiserLock) {
            speexNoiseProcessor?.let { return it }
            return runCatching { SpeexNoiseSuppressor(sampleRate = sampleRate, frameSize = 160) }
                .onFailure {
                    AppLogger.e("Speex init failed", it)
                    notifyError("Speex 初始化失败")
                    denoiserMode = AudioDenoiserMode.OFF
                }
                .getOrNull()
                ?.also { speexNoiseProcessor = it }
        }
    }

    private fun applyNoiseSuppression(buffer: FloatArray, length: Int) {
        synchronized(denoiserLock) {
            when (denoiserMode) {
                AudioDenoiserMode.RNNOISE -> ensureRnNoiseProcessorLocked()?.processInPlace(buffer, length)
                AudioDenoiserMode.SPEEX -> ensureSpeexNoiseProcessorLocked()?.processInPlace(buffer, length)
                else -> Unit
            }
        }
    }

    private fun resetNoiseProcessors() {
        synchronized(denoiserLock) {
            rnnoiseProcessor?.reset()
            speexNoiseProcessor?.reset()
        }
    }

    private fun releaseNoiseProcessors() {
        synchronized(denoiserLock) {
            releaseNoiseProcessorsLocked()
        }
    }

    private fun ensureRnNoiseProcessorLocked(): RnNoiseProcessor? {
        rnnoiseProcessor?.let { return it }
        return runCatching { RnNoiseProcessor() }
            .onFailure {
                AppLogger.e("RNNoise init failed", it)
                notifyError("RNNoise 初始化失败")
                denoiserMode = AudioDenoiserMode.OFF
            }
            .getOrNull()
            ?.also { rnnoiseProcessor = it }
    }

    private fun ensureSpeexNoiseProcessorLocked(): SpeexNoiseSuppressor? {
        speexNoiseProcessor?.let { return it }
        return runCatching { SpeexNoiseSuppressor(sampleRate = sampleRate, frameSize = 160) }
            .onFailure {
                AppLogger.e("Speex init failed", it)
                notifyError("Speex 初始化失败")
                denoiserMode = AudioDenoiserMode.OFF
            }
            .getOrNull()
            ?.also { speexNoiseProcessor = it }
    }

    private fun releaseNoiseProcessorsLocked() {
        rnnoiseProcessor?.release()
        rnnoiseProcessor = null
        speexNoiseProcessor?.release()
        speexNoiseProcessor = null
    }

    private fun resolveSileroVadModel(asrDir: File): File? {
        return asrDir.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals("silero_vad.onnx", ignoreCase = true) }
    }

    private fun ensureSileroVadProcessorLocked(): SileroVadProcessor? {
        sileroVadProcessor?.let { return it }
        val modelFile = currentSileroVadModelFile
            ?: RecognitionResourceRepository.resolveSileroVadModel(context)?.also {
                currentSileroVadModelFile = it
            }
            ?: run {
                AppLogger.e("Silero VAD model missing")
                notifyError("Silero VAD 模型缺失，已回退阈值式 VAD")
                sileroVadEnabled = false
                classicVadEnabled = true
                return null
            }
        return runCatching {
            SileroVadProcessor(
                modelFile = modelFile,
                sampleRate = sampleRate,
                threshold = sileroVadThreshold,
                minSilenceDurationSec = if (listeningRecognitionEnabled) {
                    LISTENING_MIN_SILENCE_DURATION_SEC
                } else {
                    0.4f
                },
                minSpeechDurationSec = if (listeningRecognitionEnabled) {
                    LISTENING_MIN_SPEECH_DURATION_SEC
                } else {
                    0.2f
                },
                maxSpeechDurationSec = if (listeningRecognitionEnabled) {
                    LISTENING_MAX_SPEECH_DURATION_SEC
                } else {
                    DEFAULT_MAX_SPEECH_DURATION_SEC
                }
            )
        }.onFailure {
            AppLogger.e("Silero VAD init failed", it)
            notifyError("Silero VAD 初始化失败")
            sileroVadEnabled = false
            classicVadEnabled = true
        }.getOrNull()?.also { sileroVadProcessor = it }
    }

    private fun acceptSileroVadWaveform(samples: FloatArray) {
        if (!sileroVadEnabled) return
        synchronized(sileroVadLock) {
            ensureSileroVadProcessorLocked()?.acceptWaveform(samples)
        }
    }

    private fun isSileroSpeechDetected(): Boolean {
        if (!sileroVadEnabled) return false
        return synchronized(sileroVadLock) {
            ensureSileroVadProcessorLocked()?.isSpeechDetected() == true
        }
    }

    private fun drainSileroVadSegments(flush: Boolean = false): List<FloatArray> {
        if (!sileroVadEnabled) return emptyList()
        return synchronized(sileroVadLock) {
            val processor = ensureSileroVadProcessorLocked() ?: return emptyList()
            if (flush) processor.flushAndDrain() else processor.drainSegments()
        }
    }

    private fun resetSileroVadProcessor() {
        synchronized(sileroVadLock) {
            sileroVadProcessor?.reset()
        }
        resetSileroPreRollState()
    }

    private fun releaseSileroVadProcessor() {
        synchronized(sileroVadLock) {
            sileroVadProcessor?.release()
            sileroVadProcessor = null
        }
        resetSileroPreRollState()
    }

    private fun trimSileroPreRollSamples() {
        synchronized(sileroPreRollLock) {
            trimSileroPreRollSamplesLocked()
        }
    }

    private fun resetSileroPreRollState() {
        synchronized(sileroPreRollLock) {
            sileroPreRollSamples.clear()
            sileroPendingPreRollSamples = null
            sileroSpeechDetected = false
        }
    }

    private fun updateSileroPreRollState(samples: FloatArray, speechDetected: Boolean) {
        synchronized(sileroPreRollLock) {
            if (!sileroSpeechDetected && speechDetected) {
                sileroPendingPreRollSamples = sileroPreRollSamples.toFloatArray()
            }
            sileroSpeechDetected = speechDetected
            if (!speechDetected) {
                appendSileroPreRollSamplesLocked(samples)
            }
        }
    }

    private fun prependPendingSileroPreRoll(segment: FloatArray): FloatArray {
        val prefix = synchronized(sileroPreRollLock) {
            val pending = sileroPendingPreRollSamples
            sileroPendingPreRollSamples = null
            pending
        } ?: return segment
        if (prefix.isEmpty() || segment.isEmpty()) return segment
        val limit = sileroPreRollSampleLimit()
        val effectivePrefix = if (limit > 0 && prefix.size > limit) {
            prefix.copyOfRange(prefix.size - limit, prefix.size)
        } else {
            prefix
        }
        if (effectivePrefix.isEmpty()) return segment
        return RecognitionAudioBoundary.prependWithoutDuplicate(effectivePrefix, segment)
    }

    private fun appendSileroPreRollSamplesLocked(samples: FloatArray) {
        if (samples.isEmpty()) return
        val limit = sileroPreRollSampleLimit()
        if (limit <= 0) {
            sileroPreRollSamples.clear()
            return
        }
        for (sample in samples) {
            sileroPreRollSamples.add(sample)
        }
        trimSileroPreRollSamplesLocked()
    }

    private fun trimSileroPreRollSamplesLocked() {
        val limit = sileroPreRollSampleLimit()
        if (limit <= 0) {
            sileroPreRollSamples.clear()
            sileroPendingPreRollSamples = null
            return
        }
        val overflow = sileroPreRollSamples.size - limit
        if (overflow > 0) {
            sileroPreRollSamples.subList(0, overflow).clear()
        }
        val pending = sileroPendingPreRollSamples
        if (pending != null && pending.size > limit) {
            sileroPendingPreRollSamples = pending.copyOfRange(pending.size - limit, pending.size)
        }
    }

    private fun sileroPreRollSampleLimit(): Int {
        return (sampleRate * sileroVadPreRollMs.coerceIn(
            UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS,
            UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS
        )) / 1000
    }

    private fun disableSpeechEnhancement(mode: Int, cause: Throwable? = null) {
        val label = SpeechEnhancementMode.labelOf(mode)
        if (cause != null) {
            AppLogger.e("Speech enhancement disabled mode=$label", cause)
        } else {
            AppLogger.i("Speech enhancement disabled mode=$label")
        }
        speechEnhancementMode = SpeechEnhancementMode.OFF
        SherpaSpeechEnhancer.release()
        notifyStatus("$label 初始化失败，已回退原始语音")
    }

    private fun prepareSpeechEnhancedAudio(samples: FloatArray, sourceSampleRate: Int): Pair<FloatArray, Int> {
        val currentMode = SpeechEnhancementMode.clamp(speechEnhancementMode)
        if (currentMode != SpeechEnhancementMode.GTCRN_OFFLINE || samples.isEmpty()) {
            return samples to sourceSampleRate
        }
        return runCatching {
            SherpaSpeechEnhancer.processOffline(context, currentMode, samples, sourceSampleRate)
        }.getOrElse { error ->
            disableSpeechEnhancement(currentMode, error)
            samples to sourceSampleRate
        }
    }

    private fun processRealtimeSpeechEnhancement(samples: FloatArray, sourceSampleRate: Int): FloatArray {
        val currentMode = SpeechEnhancementMode.clamp(speechEnhancementMode)
        if (!SpeechEnhancementMode.isStreaming(currentMode) || samples.isEmpty()) {
            return samples
        }
        return runCatching {
            SherpaSpeechEnhancer.processStreamingChunk(context, currentMode, samples, sourceSampleRate)
        }.getOrElse { error ->
            disableSpeechEnhancement(currentMode, error)
            samples
        }
    }

    private fun applyCommunicationMode(enabled: Boolean) {
        val manager = audioManager ?: return
        try {
            if (enabled) {
                if (previousAudioMode == null) {
                    previousAudioMode = manager.mode
                }
                if (manager.mode != AudioManager.MODE_IN_COMMUNICATION) {
                    manager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            } else {
                restoreCommunicationMode()
            }
        } catch (e: Exception) {
            AppLogger.e("AudioManager mode set failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyOutputRoutePreference() {
        val manager = audioManager ?: return
        try {
            if (!useCommunicationMode) {
                restoreOutputRoutePreference()
                return
            }
            if (previousSpeakerOn == null) {
                previousSpeakerOn = manager.isSpeakerphoneOn
            }
            if (Build.VERSION.SDK_INT >= 31) {
                if (preferredOutputType == AudioRoutePreference.OUTPUT_AUTO) {
                    manager.clearCommunicationDevice()
                } else {
                    val target = pickPreferredOutputDevice(
                        manager.availableCommunicationDevices.toTypedArray(),
                        preferredOutputType
                    )
                    if (target != null) {
                        manager.setCommunicationDevice(target)
                    } else {
                        AppLogger.i("Prefer output route: target type=$preferredOutputType not found")
                    }
                }
            } else {
                when (preferredOutputType) {
                    AudioRoutePreference.OUTPUT_SPEAKER -> manager.isSpeakerphoneOn = true
                    AudioRoutePreference.OUTPUT_EARPIECE, AudioRoutePreference.OUTPUT_AUTO -> manager.isSpeakerphoneOn = false
                    else -> {
                        // Old APIs can only reliably switch speaker/earpiece.
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AudioManager output route failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreOutputRoutePreference() {
        val manager = audioManager ?: return
        val prev = previousSpeakerOn
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                manager.clearCommunicationDevice()
            }
            if (prev != null) {
                manager.isSpeakerphoneOn = prev
            }
        } catch (e: Exception) {
            AppLogger.e("AudioManager output route restore failed", e)
        } finally {
            previousSpeakerOn = null
        }
    }

    private fun restoreCommunicationMode() {
        val manager = audioManager ?: return
        val prev = previousAudioMode ?: return
        try {
            if (manager.mode != prev) {
                manager.mode = prev
            }
        } catch (e: Exception) {
            AppLogger.e("AudioManager mode restore failed", e)
        } finally {
            previousAudioMode = null
        }
    }

    private fun passesClassicVadGate(audio: FloatArray): Boolean {
        if (!classicVadEnabled) return true
        val minVoicedMs = 200
        val minVoicedRatio = 0.2
        val speechThreshold = experimentalClassicVadThreshold(0.03)
        val minSpeechMs = 600
        val maxSpeechMs = activeMaxSpeechDurationMs() + sileroVadPreRollMs
        val durationMs = audio.size * 1000 / sampleRate
        if (durationMs !in minSpeechMs..maxSpeechMs) return false
        val frameSize = 160
        var voicedMs = 0
        var index = 0
        while (index < audio.size) {
            val end = min(index + frameSize, audio.size)
            var sumSq = 0.0
            for (i in index until end) {
                val v = audio[i]
                sumSq += v * v
            }
            val rms = sqrt(sumSq / (end - index).coerceAtLeast(1))
            if (rms > speechThreshold) {
                voicedMs += (end - index) * 1000 / sampleRate
            }
            index = end
        }
        val voicedRatio = if (durationMs > 0) voicedMs.toDouble() / durationMs else 0.0
        return voicedMs >= minVoicedMs && voicedRatio >= minVoicedRatio
    }

    private fun listeningCaptureActive(): Boolean =
        listeningRecognitionEnabled && !listeningCapturePaused

    private fun activeMaxSpeechDurationMs(): Int = if (listeningCaptureActive()) {
        (LISTENING_MAX_SPEECH_DURATION_SEC * 1000).toInt()
    } else {
        (DEFAULT_MAX_SPEECH_DURATION_SEC * 1000).toInt()
    }

    private suspend fun decodeMainSegment(segment: RecognitionSegment): String {
        val rawText = try {
            asrDecodeMutex.withLock {
                asr?.transcribe(segment.audio, segment.sampleRate) ?: ""
            }
        } catch (e: Exception) {
            AppLogger.e("ASR failed", e)
            notifyError("语音识别失败，请检查识别资源")
            ""
        }
        return filterAsrText(rawText, segment.rms)
    }

    private fun listeningUsesMainRecognizer(): Boolean =
        listeningRecognitionLanguage == asrRecognitionLanguage

    private fun listeningRecognizerReady(): Boolean =
        if (listeningUsesMainRecognizer()) asr != null else listeningAsr != null

    private suspend fun decodeListeningSegment(segment: RecognitionSegment): String {
        val rawText = try {
            if (listeningUsesMainRecognizer()) {
                asrDecodeMutex.withLock {
                    asr?.transcribe(segment.audio, segment.sampleRate) ?: ""
                }
            } else {
                listeningAsrDecodeMutex.withLock {
                    listeningAsr?.transcribe(segment.audio, segment.sampleRate) ?: ""
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Listening ASR failed", e)
            notifyError("聆听字幕识别失败，请稍后重试")
            ""
        }
        return filterAsrText(rawText, segment.rms)
    }

    private suspend fun processMainRecognizedText(text: String) {
        if (text.isNotBlank()) {
            val suppressForSoundboard = runCatching {
                shouldSuppressAutoSpeakForText(text)
            }.onFailure {
                AppLogger.e("Auto speak suppress check failed", it)
            }.getOrDefault(false)
            if (suppressAsrAutoSpeak || suppressForSoundboard) {
                val id = nextResultId()
                notifyResult(id, text)
                notifyProgress(id, 1f)
                return
            }
            if (shouldSkipDuplicateTts(text)) {
                AppLogger.i("Skip duplicate tts text=$text")
                return
            }
            val id = enqueueTts(text)
            notifyResult(id, text)
            ensureTtsLoop()
        }
    }

    private suspend fun processAsrReadySegment(segment: RecognitionSegment) {
        processMainRecognizedText(decodeMainSegment(segment))
    }

    private fun effectiveMinSegmentRms(): Double {
        return ExperimentalRecognitionSensitivityPolicy.minSegmentRms(
            minSegmentRms,
            experimentalRecognitionSensitivity
        )
    }

    private fun experimentalClassicVadThreshold(base: Double): Double {
        return ExperimentalRecognitionSensitivityPolicy.classicVadThreshold(
            base,
            experimentalRecognitionSensitivity
        )
    }

    private fun experimentalEndpointSilenceThreshold(base: Double): Double {
        return ExperimentalRecognitionSensitivityPolicy.endpointSilenceThreshold(
            base,
            experimentalRecognitionSensitivity
        )
    }

    private fun applyExperimentalInputGain(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        return ExperimentalRecognitionSensitivityPolicy.applyInputGain(
            samples,
            experimentalRecognitionSensitivity
        )
    }

    private fun processRecognizedSegment(audio: FloatArray): Boolean {
        val rms = rmsEnergy(audio)
        val minSegmentEnergy = effectiveMinSegmentRms()
        if (rms < minSegmentEnergy) return false
        val decodeForListening = listeningCaptureActive()
        val decodeForMain = mainRecognitionEnabled
        if (!decodeForListening && !decodeForMain) return false
        launchTrackedAsrJob {
            segmentProcessMutex.withLock {
                val (speechEnhancedAudio, effectiveSampleRate) = prepareSpeechEnhancedAudio(audio, sampleRate)
                val effectiveAudio = applyExperimentalInputGain(speechEnhancedAudio)
                if (effectiveAudio.isEmpty()) return@withLock
                val segment = RecognitionSegment(
                    audio = effectiveAudio,
                    sampleRate = effectiveSampleRate,
                    rms = max(rms, rmsEnergy(effectiveAudio))
                )
                if (decodeForListening && listeningRecognizerReady()) {
                    val listeningText = decodeListeningSegment(segment)
                    // A VAD boundary must finalize the visible live caption even when the
                    // final decode is empty; otherwise later segments accumulate in one row.
                    notifyListeningResult(nextResultId(), listeningText)
                }
                if (decodeForMain) {
                    when (val gate = resolveSpeakerGate(segment)) {
                        SpeakerGateResult.Pending -> return@withLock
                        is SpeakerGateResult.Ready -> {
                            gate.segments.forEach { readySegment ->
                                processAsrReadySegment(readySegment)
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    private fun maybeDecodeStreamingSenseVoice(window: List<Float>, nowMs: Long) {
        val decodeForListening = listeningCaptureActive()
        if (!pttStreamingEnabled && !decodeForListening) return
        if (asr == null && listeningAsr == null) return
        if (!classicVadEnabled && !sileroVadEnabled) return
        val mainPreviewReady = pttStreamingEnabled && isSpeakerVerifySessionReadyForPreview()
        if (!mainPreviewReady && !decodeForListening) return
        val minSamples = if (decodeForListening) sampleRate / 4 else sampleRate / 2
        if (window.size < minSamples) return
        // SenseVoice decodes the complete rolling window rather than advancing a streaming state.
        // Leave an idle gap for continuous listening so background captions do not monopolize CPU.
        val decodeIntervalMs = if (decodeForListening && !pttStreamingEnabled) 520L else 260L
        if ((nowMs - lastStreamingDecodeAtMs) < decodeIntervalMs) return
        if (!streamingDecodeBusy.compareAndSet(false, true)) return
        lastStreamingDecodeAtMs = nowMs
        val decodeGeneration = streamingDecodeGeneration.get()
        val maxSamples = if (decodeForListening) {
            sampleRate * activeMaxSpeechDurationMs() / 1000 +
                SILERO_WINDOW_SIZE_SAMPLES * LISTENING_PREVIEW_PREROLL_WINDOWS
        } else {
            sampleRate * 3
        }
        val snapshot = if (window.size > maxSamples) {
            window.takeLast(maxSamples).toFloatArray()
        } else {
            window.toFloatArray()
        }
        if (sileroVadEnabled && !isSileroSpeechDetected()) {
            streamingDecodeBusy.set(false)
            return
        }
        val segmentRms = rmsEnergy(snapshot)
        if (classicVadEnabled) {
            val minStreamingRms = kotlin.math.max(
                experimentalClassicVadThreshold(0.010),
                effectiveMinSegmentRms() * 0.85
            )
            if (segmentRms < minStreamingRms) {
                streamingDecodeBusy.set(false)
                return
            }
            val tailSize = kotlin.math.min(snapshot.size, sampleRate / 4) // ~250ms
            if (tailSize <= 0) {
                streamingDecodeBusy.set(false)
                return
            }
            val tailStart = snapshot.size - tailSize
            var tailSum = 0.0
            var voicedCount = 0
            for (i in tailStart until snapshot.size) {
                val v = snapshot[i].toDouble()
                tailSum += v * v
                if (kotlin.math.abs(snapshot[i]) >= 0.02f) {
                    voicedCount++
                }
            }
            val tailRms = kotlin.math.sqrt(tailSum / tailSize)
            val minTailRms = kotlin.math.max(
                experimentalClassicVadThreshold(0.014),
                effectiveMinSegmentRms() * 0.65
            )
            val voicedRatio = voicedCount.toDouble() / tailSize.toDouble()
            if (tailRms < minTailRms || voicedRatio < 0.08) {
                streamingDecodeBusy.set(false)
                return
            }
        }
        launchTrackedAsrJob {
            try {
                if (decodeForListening && listeningRecognizerReady()) {
                    val listeningRaw = if (listeningUsesMainRecognizer()) {
                        asrDecodeMutex.withLock {
                            asr?.transcribe(snapshot, sampleRate).orEmpty()
                        }
                    } else {
                        listeningAsrDecodeMutex.withLock {
                            listeningAsr?.transcribe(snapshot, sampleRate).orEmpty()
                        }
                    }
                    val listeningText = filterAsrText(listeningRaw, segmentRms)
                    if (
                        listeningText.isNotBlank() &&
                        decodeGeneration == streamingDecodeGeneration.get()
                    ) {
                        notifyListeningStreamingResult(listeningText)
                    }
                }
                if (mainPreviewReady) {
                    val raw = asrDecodeMutex.withLock {
                        asr?.transcribe(snapshot, sampleRate).orEmpty()
                    }
                    val text = filterAsrText(raw, segmentRms)
                    if (text.isNotBlank() && decodeGeneration == streamingDecodeGeneration.get()) {
                        notifyStreamingResult(text)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ASR streaming failed", e)
            } finally {
                streamingDecodeBusy.set(false)
            }
        }
    }

    private fun launchTrackedAsrJob(block: suspend () -> Unit) {
        lateinit var job: Job
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(activeAsrJobsLock) { activeAsrJobs.remove(job) }
            }
        }
        synchronized(activeAsrJobsLock) { activeAsrJobs.add(job) }
        job.start()
    }

    private suspend fun cancelAndJoinActiveAsrJobs() {
        val jobs = synchronized(activeAsrJobsLock) {
            activeAsrJobs.toList().also { activeAsrJobs.clear() }
        }
        jobs.forEach(Job::cancel)
        jobs.forEach { job ->
            try {
                job.join()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun awaitActiveAsrJobs() {
        while (true) {
            val jobs = synchronized(activeAsrJobsLock) {
                activeAsrJobs.filter { it.isActive }
            }
            if (jobs.isEmpty()) return
            jobs.forEach { job ->
                try {
                    job.join()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun createSpeechAudioRecord(bufferSize: Int): AudioRecord? {
        val sources = buildList {
            if (useVoiceCommunication) {
                add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } else {
                val rawSupported = audioManager
                    .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
                    ?.equals("true", ignoreCase = true) == true
                if (rawSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    add(MediaRecorder.AudioSource.UNPROCESSED)
                }
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()
        sources.forEach { source ->
            val record = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.onFailure {
                AppLogger.e("AudioRecord create failed source=$source", it)
            }.getOrNull() ?: return@forEach
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                AppLogger.i("AudioRecord selected speech source=$source")
                return record
            }
            AppLogger.i("AudioRecord source unavailable source=$source state=${record.state}")
            runCatching { record.release() }
        }
        return null
    }

    private fun applyPreferredMicrophoneDirection(record: AudioRecord) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val direction = when (preferredInputType) {
            AudioRoutePreference.INPUT_USB,
            AudioRoutePreference.INPUT_BLUETOOTH,
            AudioRoutePreference.INPUT_WIRED -> MicrophoneDirection.MIC_DIRECTION_EXTERNAL
            else -> MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER
        }
        runCatching {
            val directionApplied = record.setPreferredMicrophoneDirection(direction)
            val fieldApplied = if (direction == MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER) {
                record.setPreferredMicrophoneFieldDimension(0.35f)
            } else {
                true
            }
            AppLogger.i(
                "Microphone direction requested direction=$direction " +
                    "directionApplied=$directionApplied fieldApplied=$fieldApplied"
            )
        }.onFailure {
            AppLogger.e("Microphone direction request failed", it)
        }
    }

    internal suspend fun runSimulatedAudio(
        samples: FloatArray,
        sourceSampleRate: Int,
        chunkSamples: Int = 2048,
        paceAsRealtime: Boolean = false,
        callbacksSynchronous: Boolean = true
    ): SimulatedAudioRunResult {
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "Simulated audio is available only in debug builds"
        }
        require(sourceSampleRate > 0) { "sourceSampleRate must be positive" }
        require(chunkSamples in 160..8192) { "chunkSamples must be between 160 and 8192" }
        return recorderMutex.withLock {
            check(asr != null) { "ASR must be loaded before simulated audio is processed" }
            check(recorder == null && loopJob?.isActive != true) {
                "Simulated audio cannot run while the microphone is active"
            }
            val normalized = resampleSimulatedAudio(samples, sourceSampleRate, sampleRate)
            if (normalized.isEmpty()) {
                return@withLock SimulatedAudioRunResult(
                    inputSamples = samples.size,
                    processedSamples = 0,
                    chunkCount = 0,
                    elapsedMs = 0L
                )
            }
            lastStreamingDecodeAtMs = 0L
            streamingDecodeGeneration.incrementAndGet()
            streamingDecodeBusy.set(false)
            resetSpeakerVerifyGateState()
            resetSileroPreRollState()
            releaseSileroVadProcessor()
            SherpaSpeechEnhancer.resetStreaming()
            val startedAt = SystemClock.elapsedRealtime()
            var offset = 0
            var chunks = 0
            simulatedAudioCallbacksSynchronous.set(callbacksSynchronous)
            try {
                withContext(Dispatchers.Default) {
                    withAndroidThreadPrioritySuspending(android.os.Process.THREAD_PRIORITY_AUDIO) {
                        runRecognitionInputLoop(AtomicBoolean(true)) { buffer ->
                            if (offset >= normalized.size) {
                                SIMULATED_AUDIO_END_OF_STREAM
                            } else {
                                val count = minOf(buffer.size, chunkSamples, normalized.size - offset)
                                for (index in 0 until count) {
                                    buffer[index] = (
                                        normalized[offset + index].coerceIn(-1f, 1f) * Short.MAX_VALUE
                                        ).roundToInt().toShort()
                                }
                                offset += count
                                chunks++
                                if (paceAsRealtime) {
                                    SystemClock.sleep(count * 1000L / sampleRate)
                                }
                                count
                            }
                        }
                    }
                }
                awaitActiveAsrJobs()
            } finally {
                simulatedAudioCallbacksSynchronous.set(false)
            }
            SimulatedAudioRunResult(
                inputSamples = samples.size,
                processedSamples = normalized.size,
                chunkCount = chunks,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
            )
        }
    }

    private fun resampleSimulatedAudio(
        samples: FloatArray,
        sourceRate: Int,
        targetRate: Int
    ): FloatArray {
        if (samples.isEmpty() || sourceRate == targetRate) return samples.copyOf()
        val outputSize = (
            samples.size.toLong() * targetRate.toLong() / sourceRate.toLong()
            ).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (samples.size == 1 || outputSize == 1) return FloatArray(outputSize) { samples[0] }
        val scale = (samples.size - 1).toDouble() / (outputSize - 1).toDouble()
        return FloatArray(outputSize) { outputIndex ->
            val sourcePosition = outputIndex * scale
            val lower = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val upper = minOf(lower + 1, samples.lastIndex)
            val fraction = (sourcePosition - lower).toFloat()
            samples[lower] + (samples[upper] - samples[lower]) * fraction
        }
    }

    private fun startRecorderLoop() {
        applyCommunicationMode(useCommunicationMode)
        applyOutputRoutePreference()
        player.setUseCommunicationAttributes(useCommunicationMode)
        player.setPreferredOutputType(preferredOutputType)
        registerAudioDeviceCallback()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = createSpeechAudioRecord(max(minBuf, 4096))
        if (rec == null) {
            AppLogger.e("AudioRecord init failed for all speech sources")
            notifyError("录音初始化失败")
            return
        }
        applyInputRoutePreference(rec)
        applyPreferredMicrophoneDirection(rec)
        if (useVoiceCommunication && (!useAec3 || allowSystemAecWithAec3)) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
                    AppLogger.i("AEC enabled")
                } else {
                    AppLogger.i("AEC not available")
                }
            } catch (e: Exception) {
                AppLogger.e("AEC init failed", e)
            }
        } else if (useVoiceCommunication && useAec3) {
            AppLogger.i("AEC3 active, skip system AEC")
        }
        recorder = rec
        val flushOnStop = AtomicBoolean(true)
        recorderFlushOnStop = flushOnStop
        loopJob = scope.launch(Dispatchers.Default) {
            withAndroidThreadPrioritySuspending(android.os.Process.THREAD_PRIORITY_AUDIO) {
                try {
                    rec.startRecording()
                    reportInputDevice(rec)
                    updateOutputDeviceFromSystem()
                    runRecognitionInputLoop(flushOnStop) { target ->
                        val read = rec.read(target, 0, target.size)
                        if (read > 0) read else 0
                    }
                } catch (e: Exception) {
                    if (recorder === rec) {
                        AppLogger.e("Realtime loop failed", e)
                        notifyError("实时转换出现问题，请重新开始识别")
                    }
                }
            }
        }
    }

    private suspend fun runRecognitionInputLoop(
        flushOnStop: AtomicBoolean,
        readChunk: suspend (ShortArray) -> Int
    ) {
        val buffer = ShortArray(2048)
        val window = mutableListOf<Float>()
        var silenceMs = 0
        var voicedMs = 0
        var listeningSpeechSeen = false
        var listeningTrailingSilenceMs = 0
        var activeInputEpoch = recognitionInputEpoch.get()
        val minVoicedMs = 200
        val minVoicedRatio = 0.2
        val minSpeechMs = 600

        fun flushRecognitionWindow() {
            if (sileroVadEnabled) {
                val segments = drainSileroVadSegments(flush = true)
                if (segments.isNotEmpty()) {
                    streamingDecodeGeneration.incrementAndGet()
                }
                segments.forEach { segment ->
                    val audio = prependPendingSileroPreRoll(segment)
                    if (classicVadEnabled && !passesClassicVadGate(audio)) return@forEach
                    processRecognizedSegment(audio)
                }
                window.clear()
                resetSileroPreRollState()
            } else if (window.isNotEmpty()) {
                val audio = window.toFloatArray()
                val durationMs = audio.size * 1000 / sampleRate
                if (
                    RecognitionWindowPolicy.shouldSubmit(
                        durationMs = durationMs,
                        voicedMs = voicedMs,
                        rms = rmsEnergy(audio),
                        minRms = effectiveMinSegmentRms()
                    )
                ) {
                    streamingDecodeGeneration.incrementAndGet()
                    processRecognizedSegment(audio)
                }
                window.clear()
            }
            silenceMs = 0
            voicedMs = 0
            listeningSpeechSeen = false
            listeningTrailingSilenceMs = 0
        }

        fun completePendingFlushRequest() {
            pendingRecognitionFlush.getAndSet(null)?.let { request ->
                flushRecognitionWindow()
                request.complete(Unit)
            }
        }

        try {
            while (currentCoroutineContext().isActive) {
                val read = readChunk(buffer)
                if (read == SIMULATED_AUDIO_END_OF_STREAM) break
                if (read <= 0) {
                    completePendingFlushRequest()
                    continue
                }
                val floatBuf = FloatArray(read)
                for (i in 0 until read) {
                    floatBuf[i] = buffer[i] / 32768f
                }
                if (useAec3) {
                    aec3?.processCapture(floatBuf, 0, read)
                    captureFrames.incrementAndGet()
                    lastCaptureMs.set(SystemClock.uptimeMillis())
                }
                applyNoiseSuppression(floatBuf, read)
                var sumSq = 0.0
                for (i in 0 until read) {
                    val v = floatBuf[i]
                    sumSq += v * v
                }
                val bufRms = if (read > 0) sqrt(sumSq / read) else 0.0
                val now = SystemClock.uptimeMillis()
                if (now - lastLevelReportMs >= 60L) {
                    lastLevelReportMs = now
                    notifyLevel(bufRms.toFloat())
                    if (useAec3) {
                        notifyAec3Diag(buildAec3Diag(now))
                    }
                }
                if (suppressWhilePlaying && (player.isPlaying || now < suppressUntilMs)) {
                    if (window.isNotEmpty()) {
                        streamingDecodeGeneration.incrementAndGet()
                    }
                    window.clear()
                    resetSileroPreRollState()
                    silenceMs = 0
                    voicedMs = 0
                    completePendingFlushRequest()
                    continue
                }
                val recognitionBuf = processRealtimeSpeechEnhancement(floatBuf, sampleRate)
                if (recognitionBuf.isEmpty()) {
                    completePendingFlushRequest()
                    continue
                }
                val currentInputEpoch = recognitionInputEpoch.get()
                if (currentInputEpoch != activeInputEpoch) {
                    window.clear()
                    resetSileroPreRollState()
                    if (sileroVadEnabled) resetSileroVadProcessor()
                    silenceMs = 0
                    voicedMs = 0
                    listeningSpeechSeen = false
                    listeningTrailingSilenceMs = 0
                    lastStreamingDecodeAtMs = 0L
                    activeInputEpoch = currentInputEpoch
                }
                for (sample in recognitionBuf) {
                    window.add(sample)
                }
                if (sileroVadEnabled) {
                    acceptSileroVadWaveform(recognitionBuf)
                    val speechDetected = isSileroSpeechDetected()
                    updateSileroPreRollState(recognitionBuf, speechDetected)
                    val segments = drainSileroVadSegments(flush = false)
                    if (segments.isNotEmpty()) {
                        streamingDecodeGeneration.incrementAndGet()
                        window.clear()
                        lastStreamingDecodeAtMs = 0L
                        listeningSpeechSeen = false
                        listeningTrailingSilenceMs = 0
                    }
                    segments.forEach { segment ->
                        val audio = prependPendingSileroPreRoll(segment)
                        if (classicVadEnabled && !passesClassicVadGate(audio)) return@forEach
                        processRecognizedSegment(audio)
                    }
                    val forceListeningBoundary = ListeningEndpointPolicy.shouldForceBoundary(
                        listeningEnabled = listeningCaptureActive(),
                        speechDetected = speechDetected,
                        windowSamples = window.size,
                        sampleRate = sampleRate,
                        maxSpeechDurationMs = activeMaxSpeechDurationMs(),
                        preRollSamples =
                            SILERO_WINDOW_SIZE_SAMPLES * LISTENING_PREVIEW_PREROLL_WINDOWS
                    )
                    if (forceListeningBoundary) {
                        val forcedSegments = drainSileroVadSegments(flush = true)
                        val forcedAudio = if (forcedSegments.isNotEmpty()) {
                            forcedSegments.map(::prependPendingSileroPreRoll)
                        } else {
                            listOf(window.toFloatArray())
                        }
                        streamingDecodeGeneration.incrementAndGet()
                        window.clear()
                        lastStreamingDecodeAtMs = 0L
                        resetSileroVadProcessor()
                        var submitted = false
                        forcedAudio.forEach { audio ->
                            if (classicVadEnabled && !passesClassicVadGate(audio)) {
                                return@forEach
                            }
                            submitted = processRecognizedSegment(audio) || submitted
                        }
                        if (!submitted) {
                            // A forced boundary must still clear the visible live row.
                            notifyListeningResult(nextResultId(), "")
                        }
                        listeningSpeechSeen = false
                        listeningTrailingSilenceMs = 0
                    }
                    if (listeningCaptureActive()) {
                        val stepMs = recognitionBuf.size * 1000 / sampleRate
                        if (speechDetected) {
                            listeningSpeechSeen = true
                            listeningTrailingSilenceMs = 0
                        } else if (listeningSpeechSeen) {
                            listeningTrailingSilenceMs += stepMs
                        }
                        if (
                            ListeningEndpointPolicy.shouldFinalizeAfterSilence(
                                listeningEnabled = true,
                                speechSeen = listeningSpeechSeen,
                                trailingSilenceMs = listeningTrailingSilenceMs,
                                requiredSilenceMs = LISTENING_ENDPOINT_FALLBACK_SILENCE_MS
                            )
                        ) {
                            val trailingSegments = drainSileroVadSegments(flush = true)
                            val trailingAudio = if (trailingSegments.isNotEmpty()) {
                                trailingSegments.map(::prependPendingSileroPreRoll)
                            } else {
                                listOf(window.toFloatArray())
                            }
                            streamingDecodeGeneration.incrementAndGet()
                            window.clear()
                            lastStreamingDecodeAtMs = 0L
                            resetSileroVadProcessor()
                            var submitted = false
                            trailingAudio.forEach { audio ->
                                if (classicVadEnabled && !passesClassicVadGate(audio)) {
                                    return@forEach
                                }
                                submitted = processRecognizedSegment(audio) || submitted
                            }
                            if (!submitted) {
                                notifyListeningResult(nextResultId(), "")
                            }
                            listeningSpeechSeen = false
                            listeningTrailingSilenceMs = 0
                        }
                    } else {
                        listeningSpeechSeen = false
                        listeningTrailingSilenceMs = 0
                    }
                    val maxStreamingWindowSamples = when {
                        listeningCaptureActive() && speechDetected -> {
                            sampleRate * activeMaxSpeechDurationMs() / 1000 +
                                SILERO_WINDOW_SIZE_SAMPLES * LISTENING_PREVIEW_PREROLL_WINDOWS
                        }
                        listeningCaptureActive() ->
                            SILERO_WINDOW_SIZE_SAMPLES * LISTENING_PREVIEW_PREROLL_WINDOWS
                        else -> sampleRate * 3
                    }
                    if (window.size > maxStreamingWindowSamples) {
                        val overflow = window.size - maxStreamingWindowSamples
                        if (overflow > 0) {
                            window.subList(0, overflow).clear()
                        }
                    }
                } else {
                    resetSileroPreRollState()
                }
                maybeDecodeStreamingSenseVoice(window, now)
                if (classicVadEnabled && !sileroVadEnabled) {
                    val maxSpeechMs = activeMaxSpeechDurationMs()
                    val endpointSilenceMs = if (listeningCaptureActive()) 100 else 400
                    val energy = sqrt(window.takeLast(min(400, window.size)).map { it * it }.average())
                    val stepMs = recognitionBuf.size * 1000 / sampleRate
                    if (energy < experimentalEndpointSilenceThreshold(0.015)) {
                        silenceMs += stepMs
                    } else {
                        silenceMs = 0
                    }
                    if (energy > experimentalClassicVadThreshold(0.03)) {
                        voicedMs += stepMs
                    }
                    val durMs = window.size * 1000 / sampleRate
                    if (
                        silenceMs > endpointSilenceMs &&
                        durMs in minSpeechMs..maxSpeechMs &&
                        !player.isPlaying
                    ) {
                        val voicedRatio = if (durMs > 0) voicedMs.toDouble() / durMs else 0.0
                        if (voicedMs < minVoicedMs || voicedRatio < minVoicedRatio) {
                            window.clear()
                            silenceMs = 0
                            voicedMs = 0
                            continue
                        }
                        val audio = window.toFloatArray()
                        streamingDecodeGeneration.incrementAndGet()
                        window.clear()
                        silenceMs = 0
                        voicedMs = 0
                        processRecognizedSegment(audio)
                    }
                    if (durMs > maxSpeechMs) {
                        flushRecognitionWindow()
                    }
                }
                completePendingFlushRequest()
            }
        } finally {
            if (flushOnStop.get()) {
                flushRecognitionWindow()
            }
            pendingRecognitionFlush.getAndSet(null)?.complete(Unit)
        }
    }

    private fun applyInputRoutePreference(rec: AudioRecord) {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            val manager = audioManager ?: return
            val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val preferred = pickPreferredInputDevice(devices, preferredInputType)
            if (preferred != null) {
                val ok = rec.setPreferredDevice(preferred)
                AppLogger.i("Prefer input device: ${formatInputDeviceLabel(preferred)} result=$ok")
            } else if (preferredInputType != AudioRoutePreference.INPUT_AUTO) {
                AppLogger.i("Prefer input route: target type=$preferredInputType not found")
            }
        } catch (e: Exception) {
            AppLogger.e("Prefer input route failed", e)
        }
    }

    private fun reportInputDevice(rec: AudioRecord) {
        if (Build.VERSION.SDK_INT < 24) {
            notifyInputDevice("未知")
            return
        }
        val device = try {
            rec.routedDevice
        } catch (_: Exception) {
            null
        }
        notifyInputDevice(formatInputDeviceLabel(device))
    }

    private fun registerAudioDeviceCallback() {
        if (audioDeviceCallback != null) return
        if (Build.VERSION.SDK_INT < 23) return
        val manager = audioManager ?: return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                recorder?.let { reportInputDevice(it) }
                updateOutputDeviceFromSystem()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                recorder?.let { reportInputDevice(it) }
                updateOutputDeviceFromSystem()
            }
        }
        audioDeviceCallback = callback
        val handler = Handler(Looper.getMainLooper())
        manager.registerAudioDeviceCallback(callback, handler)
    }

    private fun unregisterAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT < 23) return
        val manager = audioManager ?: return
        val callback = audioDeviceCallback ?: return
        try {
            manager.unregisterAudioDeviceCallback(callback)
        } catch (_: Exception) {
        }
        audioDeviceCallback = null
    }

    private fun updateOutputDeviceFromSystem() {
        val manager = audioManager ?: return
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        notifyOutputDevice(pickOutputDeviceLabel(devices))
    }

    private fun rmsEnergy(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            sum += (s * s)
        }
        return sqrt(sum / samples.size)
    }

    private fun filterAsrText(
        raw: String,
        rms: Double
    ): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val letters = text.count { it.isLetterOrDigit() }
        if (letters == 0) return ""
        if (letters <= 1 && rms < effectiveMinSegmentRms()) return ""
        return text
    }

    private fun buildAec3Diag(nowMs: Long): String {
        val renderAge = nowMs - lastRenderMs.get()
        val captureAge = nowMs - lastCaptureMs.get()
        val renderCount = renderFrames.get()
        val captureCount = captureFrames.get()
        val renderLabel = if (renderCount == 0L) "未收到" else "${renderAge}ms前"
        val captureLabel = if (captureCount == 0L) "未收到" else "${captureAge}ms前"
        return "AEC3 诊断：渲染=$renderLabel($renderCount) 采集=$captureLabel($captureCount)"
    }

}

private fun pickPreferredInputDevice(devices: Array<AudioDeviceInfo>, pref: Int): AudioDeviceInfo? {
    if (pref == AudioRoutePreference.INPUT_AUTO) return null
    fun find(types: Set<Int>): AudioDeviceInfo? = devices.firstOrNull { it.type in types }
    return when (pref) {
        AudioRoutePreference.INPUT_BUILTIN_MIC -> find(
            setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_TELEPHONY)
        )
        AudioRoutePreference.INPUT_USB -> find(
            setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
        )
        AudioRoutePreference.INPUT_BLUETOOTH -> find(
            setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        )
        AudioRoutePreference.INPUT_WIRED -> find(
            setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG)
        )
        else -> null
    }
}

private fun pickPreferredOutputDevice(devices: Array<AudioDeviceInfo>, pref: Int): AudioDeviceInfo? {
    if (pref == AudioRoutePreference.OUTPUT_AUTO) return null
    fun find(types: Set<Int>): AudioDeviceInfo? = devices.firstOrNull { it.type in types }
    return when (pref) {
        AudioRoutePreference.OUTPUT_SPEAKER -> find(
            setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
        )
        AudioRoutePreference.OUTPUT_EARPIECE -> find(
            setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        )
        AudioRoutePreference.OUTPUT_BLUETOOTH -> find(
            setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        )
        AudioRoutePreference.OUTPUT_USB -> find(
            setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
        )
        AudioRoutePreference.OUTPUT_WIRED -> find(
            setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG)
        )
        else -> null
    }
}

private fun formatInputDeviceLabel(device: AudioDeviceInfo?): String {
    if (device == null) return "未知"
    val typeName = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
        AudioDeviceInfo.TYPE_TELEPHONY -> "话筒"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB麦克风"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙麦克风"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG -> "有线麦克风"
        else -> "设备(${device.type})"
    }
    val name = device.productName?.toString()?.trim().orEmpty()
    return if (name.isNotEmpty()) "$typeName - $name" else typeName
}

private fun formatOutputDeviceLabel(device: AudioDeviceInfo?): String {
    if (device == null) return "未知"
    val typeName = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG -> "有线耳机"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙耳机"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB音频"
        else -> "设备(${device.type})"
    }
    val name = device.productName?.toString()?.trim().orEmpty()
    return if (name.isNotEmpty()) "$typeName - $name" else typeName
}

private fun pickOutputDeviceLabel(devices: Array<AudioDeviceInfo>): String {
    if (devices.isEmpty()) return "未知"
    fun find(types: Set<Int>): AudioDeviceInfo? = devices.firstOrNull { it.type in types }
    val device = find(setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        ?: find(setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        ?: find(setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET))
        ?: find(setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        ?: find(setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        ?: devices.first()
    return formatOutputDeviceLabel(device)
}
