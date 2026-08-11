package com.lhtstudio.kigtts.app.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.util.AppLogger
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

internal object SpeakerVerifier {
    private const val PRIMARY_MODEL_ASSET_PATH =
        "speaker_verify/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
    private const val PRIMARY_MODEL_FILE_NAME =
        "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
    private const val MAX_ANALYZE_SAMPLES = 16000 * 8
    private const val REGISTERED_SPEAKER_NAME = "__self__"

    private val lock = Any()
    private var primaryExtractor: SpeakerEmbeddingExtractor? = null
    private var confirmationExtractor: SpeakerEmbeddingExtractor? = null
    private var cachedPrimaryModelFile: File? = null
    private var cachedConfirmationModelPath: String? = null

    fun computeEmbedding(context: Context, samples: FloatArray, sampleRate: Int): FloatArray? {
        return computeWithExtractor(samples, sampleRate) { ensurePrimaryExtractor(context) }
    }

    fun computeConfirmationEmbedding(
        context: Context,
        samples: FloatArray,
        sampleRate: Int
    ): FloatArray? {
        return computeWithExtractor(samples, sampleRate) { ensureConfirmationExtractor(context) }
    }

    fun confirmationModelAvailable(context: Context): Boolean {
        return RecognitionResourceRepository.resolveSpeakerConfirmationModel(context) != null
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val n = min(a.size, b.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            na += av * av
            nb += bv * bv
        }
        if (na <= 1e-12 || nb <= 1e-12) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat().coerceIn(-1f, 1f)
    }

    fun registeredSpeakerName(): String = REGISTERED_SPEAKER_NAME

    fun combineProfilesOfficialStyle(profiles: List<FloatArray>): FloatArray? {
        if (profiles.isEmpty()) return null
        val dim = profiles.minOfOrNull { it.size } ?: return null
        if (dim <= 0) return null
        val out = FloatArray(dim)
        profiles.forEach { profile ->
            for (i in 0 until dim) out[i] += profile[i]
        }
        var sumSq = 0.0
        for (value in out) sumSq += value * value
        val norm = sqrt(sumSq)
        if (norm <= 1e-8) return null
        for (i in out.indices) out[i] = (out[i] / norm).toFloat()
        return out
    }

    fun createManager(context: Context, profiles: List<FloatArray>): SpeakerEmbeddingManager? {
        val normalizedProfiles = profiles.mapNotNull { profile ->
            profile.takeIf { it.isNotEmpty() }?.copyOf()
        }
        if (normalizedProfiles.isEmpty()) return null
        return synchronized(lock) {
            val activeExtractor = ensurePrimaryExtractor(context) ?: return@synchronized null
            val manager = SpeakerEmbeddingManager(activeExtractor.dim())
            val added = runCatching {
                manager.add(REGISTERED_SPEAKER_NAME, normalizedProfiles.toTypedArray())
            }.onFailure {
                AppLogger.e("Speaker manager add failed", it)
            }.getOrDefault(false)
            if (!added) {
                runCatching { manager.release() }
                return@synchronized null
            }
            manager
        }
    }

    fun release() {
        synchronized(lock) {
            primaryExtractor?.release()
            confirmationExtractor?.release()
            primaryExtractor = null
            confirmationExtractor = null
            cachedConfirmationModelPath = null
        }
    }

    private fun computeWithExtractor(
        samples: FloatArray,
        sampleRate: Int,
        extractorProvider: () -> SpeakerEmbeddingExtractor?
    ): FloatArray? {
        if (sampleRate <= 0 || samples.isEmpty()) return null
        val usable = min(samples.size, MAX_ANALYZE_SAMPLES)
        val clipped = if (usable == samples.size) samples else samples.copyOfRange(0, usable)
        return synchronized(lock) {
            val activeExtractor = extractorProvider() ?: return@synchronized null
            val stream = activeExtractor.createStream()
            try {
                stream.acceptWaveform(clipped, sampleRate)
                stream.inputFinished()
                if (!activeExtractor.isReady(stream)) {
                    AppLogger.i(
                        "Speaker embedding stream not ready samples=${clipped.size} sr=$sampleRate"
                    )
                    return@synchronized null
                }
                activeExtractor.compute(stream)
            } catch (t: Throwable) {
                AppLogger.e("Speaker embedding compute failed", t)
                null
            } finally {
                runCatching { stream.release() }
            }
        }
    }

    private fun ensurePrimaryExtractor(context: Context): SpeakerEmbeddingExtractor? {
        primaryExtractor?.let { return it }
        val modelFile = ensurePrimaryModelFile(context) ?: return null
        return createExtractor(modelFile, "primary")?.also { primaryExtractor = it }
    }

    private fun ensureConfirmationExtractor(context: Context): SpeakerEmbeddingExtractor? {
        val modelFile = RecognitionResourceRepository.resolveSpeakerConfirmationModel(context)
            ?: return null
        confirmationExtractor?.takeIf { cachedConfirmationModelPath == modelFile.absolutePath }
            ?.let { return it }
        confirmationExtractor?.release()
        confirmationExtractor = null
        cachedConfirmationModelPath = null
        return createExtractor(modelFile, "confirmation")?.also {
            confirmationExtractor = it
            cachedConfirmationModelPath = modelFile.absolutePath
        }
    }

    private fun createExtractor(modelFile: File, label: String): SpeakerEmbeddingExtractor? {
        return runCatching {
            SpeakerEmbeddingExtractor(
                null,
                SpeakerEmbeddingExtractorConfig(
                    modelFile.absolutePath,
                    2,
                    false,
                    "cpu"
                )
            )
        }.onFailure {
            AppLogger.e("Speaker $label extractor init failed", it)
        }.getOrNull()?.also {
            AppLogger.i(
                "Speaker $label extractor loaded model=${modelFile.absolutePath} dim=${it.dim()}"
            )
        }
    }

    private fun ensurePrimaryModelFile(context: Context): File? {
        cachedPrimaryModelFile?.let { existing ->
            if (existing.exists() && existing.length() > 0L) return existing
        }
        return runCatching {
            val outDir = File(context.filesDir, "models/speaker_verify").apply { mkdirs() }
            val outFile = File(outDir, PRIMARY_MODEL_FILE_NAME)
            if (!outFile.exists() || outFile.length() <= 0L) {
                context.assets.open(PRIMARY_MODEL_ASSET_PATH).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            cachedPrimaryModelFile = outFile
            outFile
        }.onFailure {
            AppLogger.e("Speaker model prepare failed", it)
        }.getOrNull()
    }
}
