package com.lhtstudio.kigtts.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import com.lhtstudio.kigtts.app.data.RecognitionResourceRepository
import com.lhtstudio.kigtts.app.util.AppLogger
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class VoiceModuleSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN) return
        val pendingResult = goAsync()
        thread(name = "voice-module-smoke") {
            val report = mutableListOf<String>()
            try {
                runSmoke(context.applicationContext, report)
                report += "RESULT=PASS"
            } catch (t: Throwable) {
                report += "RESULT=FAIL"
                report += "ERROR=${t.stackTraceToString()}"
                AppLogger.e("VOICE_SMOKE failed", t)
            } finally {
                File(context.cacheDir, REPORT_FILE_NAME).writeText(
                    report.joinToString(System.lineSeparator()),
                    Charsets.UTF_8
                )
                report.forEach { AppLogger.i("VOICE_SMOKE $it") }
                pendingResult.finish()
            }
        }
    }

    private fun runSmoke(context: Context, report: MutableList<String>) {
        val repository = RecognitionResourceRepository(context)
        val asrDir = requireNotNull(repository.installedAsrDir()) { "ASR resource is missing" }
        val wavFile = File(context.cacheDir, TEST_WAV_NAME)
        require(wavFile.isFile) { "Test WAV is missing: ${wavFile.absolutePath}" }
        val (samples, sampleRate) = readPcm16Wave(wavFile)

        val asrEngine = AsrEngine(
            context,
            asrDir,
            AsrRecognitionLanguage.MANDARIN
        )
        try {
            val text = asrEngine.transcribe(samples, sampleRate)
            report += "ASR_TEXT=$text"
            require(text == EXPECTED_TEXT) { "Unexpected ASR text: $text" }
            report += "BUILT_IN_PUNCTUATION=PASS"
        } finally {
            asrEngine.close()
        }

        val confirmationModel = File(context.cacheDir, SPEAKER_CONFIRMATION_MODEL_NAME)
        require(confirmationModel.isFile) {
            "Speaker confirmation model is missing: ${confirmationModel.absolutePath}"
        }
        val confirmationExtractor = SpeakerEmbeddingExtractor(
            null,
            SpeakerEmbeddingExtractorConfig(
                confirmationModel.absolutePath,
                2,
                false,
                "cpu"
            )
        )
        try {
            val stream = confirmationExtractor.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                stream.inputFinished()
                require(confirmationExtractor.isReady(stream)) {
                    "ERes2NetV2 stream is not ready"
                }
                val startedAt = SystemClock.elapsedRealtime()
                val confirmationEmbedding = confirmationExtractor.compute(stream)
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                require(
                    confirmationEmbedding.size == 192 &&
                        confirmationEmbedding.all(Float::isFinite)
                ) { "ERes2NetV2 embedding is invalid" }
                report += "ERES2NETV2_DIM=${confirmationEmbedding.size}"
                report += "ERES2NETV2_MS=$elapsedMs"
            } finally {
                stream.release()
            }
        } finally {
            confirmationExtractor.release()
        }

        val tone = FloatArray(sampleRate) { index ->
            (sin(2.0 * PI * 220.0 * index / sampleRate) * 0.02).toFloat()
        }
        listOf(
            SpeechEnhancementMode.GTCRN_OFFLINE,
            SpeechEnhancementMode.GTCRN_STREAMING,
            SpeechEnhancementMode.DPDFNET2_STREAMING,
            SpeechEnhancementMode.DPDFNET4_STREAMING
        ).forEach { mode ->
            try {
                val (enhanced, enhancedRate) = SherpaSpeechEnhancer.processPreview(
                    context,
                    mode,
                    tone,
                    sampleRate
                )
                require(enhanced.isNotEmpty() && enhanced.all(Float::isFinite)) {
                    "Speech enhancement mode $mode returned invalid audio"
                }
                report += "DENOISER_$mode=${enhanced.size}@$enhancedRate"
            } finally {
                SherpaSpeechEnhancer.release()
            }
        }

        requireNotNull(RecognitionResourceRepository.resolveSileroVadModel(context)) {
            "Silero VAD model is missing"
        }
        report += "SILERO_VAD=FOUND"

        val neural = requireNotNull(
            RecognitionResourceRepository.resolveNeuralSpeakerFilterResources(context)
        ) { "Neural speaker resources are missing" }
        val embedding = try {
            requireNotNull(
                SpeechBrainEcapaEmbedder.compute(context, neural.ecapaModel, tone, sampleRate)
            ) { "ECAPA embedding failed" }
        } finally {
            SpeechBrainEcapaEmbedder.releaseModel()
        }
        require(embedding.size == 192 && embedding.all(Float::isFinite)) {
            "ECAPA embedding is invalid"
        }
        report += "ECAPA_DIM=${embedding.size}"

        NeuralTargetSpeakerSeparator(neural.tseModel).use { separator ->
            val result = requireNotNull(separator.separate(tone, sampleRate, embedding)) {
                "TSE inference failed"
            }
            require(result.audio.size == tone.size && result.audio.all(Float::isFinite)) {
                "TSE output is invalid"
            }
            report += "TSE_RTF=${String.format(Locale.US, "%.3f", result.realtimeFactor)}"
        }
    }

    private fun readPcm16Wave(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF") { "Not a RIFF file" }
            input.readLittleEndianInt()
            require(input.readAscii(4) == "WAVE") { "Not a WAVE file" }
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = 0
            while (input.filePointer + 8 <= input.length()) {
                val chunk = input.readAscii(4)
                val size = input.readLittleEndianInt()
                val next = input.filePointer + size + (size and 1)
                when (chunk) {
                    "fmt " -> {
                        require(input.readLittleEndianShort() == 1) { "Only PCM WAV is supported" }
                        channels = input.readLittleEndianShort()
                        sampleRate = input.readLittleEndianInt()
                        input.seek(input.filePointer + 6)
                        bitsPerSample = input.readLittleEndianShort()
                    }
                    "data" -> {
                        dataOffset = input.filePointer
                        dataSize = size
                    }
                }
                input.seek(next)
                if (dataOffset >= 0 && sampleRate > 0) break
            }
            require(channels == 1 && bitsPerSample == 16 && dataOffset >= 0)
            input.seek(dataOffset)
            return FloatArray(dataSize / 2) {
                input.readLittleEndianShort().toShort() / 32768f
            } to sampleRate
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readLittleEndianInt(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)

    private companion object {
        const val ACTION_RUN = "com.lhtstudio.kigtts.app.action.RUN_VOICE_MODULE_SMOKE"
        const val TEST_WAV_NAME = "sensevoice-v4-device-test.wav"
        const val SPEAKER_CONFIRMATION_MODEL_NAME = "speaker-confirmation-device-test.onnx"
        const val REPORT_FILE_NAME = "voice-module-smoke.txt"
        const val EXPECTED_TEXT = "今天是二零二六年八月八日，下午三点十五分。"
    }
}
