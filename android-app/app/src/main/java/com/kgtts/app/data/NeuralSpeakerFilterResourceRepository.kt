package com.lhtstudio.kigtts.app.data

import android.content.Context
import com.lhtstudio.kigtts.app.util.AppLogger
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class NeuralSpeakerFilterResourceStatus(
    val installed: Boolean,
    val installedBytes: Long = 0L,
    val tseModel: File? = null,
    val ecapaModel: File? = null,
    val bundledWithRecognitionResources: Boolean = false
)

internal data class NeuralSpeakerFilterRemoteFile(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String
)

internal object NeuralSpeakerFilterResourceSpec {
    const val VERSION = 1
    const val TSE_MODEL_NAME = "tse_prod_48k.onnx"
    const val TSE_DATA_NAME = "tse_prod_48k.onnx.data"
    const val ECAPA_MODEL_NAME = "ecapa_tdnn.onnx"

    private const val TSE_REVISION = "5d8934d48e582dbd00285697bde972c4ec17ba2a"
    private const val ECAPA_REVISION = "57bc773c7cc1a8afa117b38b0b2a38c96ffa99a2"

    val files = listOf(
        NeuralSpeakerFilterRemoteFile(
            name = TSE_MODEL_NAME,
            url = "https://huggingface.co/penta2himajin/tse-conv-tasnet-48k/resolve/" +
                "$TSE_REVISION/$TSE_MODEL_NAME",
            size = 660_552L,
            sha256 = "71490a5a9c66f0693f6ce1990c3e258c0814fed1f970f3efd2a832ddbeccfdc9"
        ),
        NeuralSpeakerFilterRemoteFile(
            name = TSE_DATA_NAME,
            url = "https://huggingface.co/penta2himajin/tse-conv-tasnet-48k/resolve/" +
                "$TSE_REVISION/$TSE_DATA_NAME",
            size = 5_662_720L,
            sha256 = "4b84f54b47beb7904bb39de47a76a9936c560f5a0a09577ff593980692c81a16"
        ),
        NeuralSpeakerFilterRemoteFile(
            name = ECAPA_MODEL_NAME,
            url = "https://huggingface.co/penta2himajin/ecapa-tdnn-onnx/resolve/" +
                "$ECAPA_REVISION/$ECAPA_MODEL_NAME",
            size = 83_481_218L,
            sha256 = "75f5f36d23879c5b2dd73b09221e8727e8e6e6a7cbd1a0655992d7ae81195698"
        )
    )

    val totalBytes: Long = files.sumOf { it.size }
}

class NeuralSpeakerFilterResourceRepository(private val context: Context) {
    private val root = File(context.filesDir, "models/neural_speaker_filter")
    private val activeDir = File(root, "active")
    private val stagingDir = File(root, ".installing")
    private val manifestFile = File(activeDir, MANIFEST_FILE_NAME)

    init {
        root.mkdirs()
        cleanupInterruptedInstall()
    }

    fun status(): NeuralSpeakerFilterResourceStatus {
        val manifest = readManifest()
        if (manifest?.optInt("version", -1) == NeuralSpeakerFilterResourceSpec.VERSION) {
            val valid = NeuralSpeakerFilterResourceSpec.files.all { spec ->
                val file = File(activeDir, spec.name)
                file.isFile && file.length() == spec.size
            }
            if (valid) {
                return NeuralSpeakerFilterResourceStatus(
                    installed = true,
                    installedBytes = NeuralSpeakerFilterResourceSpec.totalBytes,
                    tseModel = File(activeDir, NeuralSpeakerFilterResourceSpec.TSE_MODEL_NAME),
                    ecapaModel = File(activeDir, NeuralSpeakerFilterResourceSpec.ECAPA_MODEL_NAME)
                )
            }
        }

        val bundled = RecognitionResourceRepository.resolveNeuralSpeakerFilterResources(context)
            ?: return NeuralSpeakerFilterResourceStatus(false)
        val bundledFiles = mapOf(
            NeuralSpeakerFilterResourceSpec.TSE_MODEL_NAME to bundled.tseModel,
            NeuralSpeakerFilterResourceSpec.TSE_DATA_NAME to bundled.tseData,
            NeuralSpeakerFilterResourceSpec.ECAPA_MODEL_NAME to bundled.ecapaModel
        )
        val bundledValid = NeuralSpeakerFilterResourceSpec.files.all { spec ->
            val file = bundledFiles[spec.name]
            file?.isFile == true && file.length() == spec.size
        }
        if (!bundledValid) return NeuralSpeakerFilterResourceStatus(false)
        return NeuralSpeakerFilterResourceStatus(
            installed = true,
            installedBytes = NeuralSpeakerFilterResourceSpec.totalBytes,
            tseModel = bundled.tseModel,
            ecapaModel = bundled.ecapaModel,
            bundledWithRecognitionResources = true
        )
    }

    fun downloadAndInstall(
        onProgress: (RecognitionResourceProgress) -> Unit
    ): NeuralSpeakerFilterResourceStatus {
        root.mkdirs()
        stagingDir.mkdirs()
        val reserveBytes = NeuralSpeakerFilterResourceSpec.totalBytes + MIN_FREE_SPACE_AFTER_INSTALL
        if (root.usableSpace in 1 until reserveBytes) {
            throw IOException("存储空间不足，神经分离资源需要约 90 MB 可用空间")
        }
        var completedBytes = 0L
        try {
            NeuralSpeakerFilterResourceSpec.files.forEachIndexed { index, spec ->
                val target = File(stagingDir, spec.name)
                downloadWithResume(spec, target) { fileBytes ->
                    val totalProgress = completedBytes + fileBytes.coerceAtMost(spec.size)
                    onProgress(
                        RecognitionResourceProgress(
                            stage = "下载神经分离资源（${index + 1}/${NeuralSpeakerFilterResourceSpec.files.size}）",
                            fraction = totalProgress.toFloat() /
                                NeuralSpeakerFilterResourceSpec.totalBytes.toFloat()
                        )
                    )
                }
                onProgress(RecognitionResourceProgress("校验神经分离资源", -1f))
                verifyFile(target, spec)
                completedBytes += spec.size
            }
            writeNotice(stagingDir)
            writeManifest(stagingDir)
            replaceActiveDirectory()
            onProgress(RecognitionResourceProgress("神经分离资源安装完成", 1f))
            return status().also {
                if (!it.installed) throw IOException("神经分离资源安装后校验失败")
            }
        } catch (t: Throwable) {
            AppLogger.e("Neural speaker filter resource install failed", t)
            throw t
        }
    }

    fun delete() {
        activeDir.deleteRecursively()
        stagingDir.deleteRecursively()
        if (activeDir.exists() || stagingDir.exists()) {
            throw IOException("神经分离资源正在使用，请停止识别后重试")
        }
    }

    private fun downloadWithResume(
        spec: NeuralSpeakerFilterRemoteFile,
        target: File,
        onProgress: (Long) -> Unit
    ) {
        target.parentFile?.mkdirs()
        if (target.isFile && target.length() == spec.size) {
            onProgress(spec.size)
            return
        }
        if (target.length() > spec.size) target.delete()
        val existing = target.length().coerceAtLeast(0L)
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("下载失败：HTTP $code")
            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            val initial = if (append) existing else 0L
            if (!append && target.exists()) target.delete()
            var copied = initial
            var lastEmitAt = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastEmitAt >= 160L || copied >= spec.size) {
                            lastEmitAt = now
                            onProgress(copied)
                        }
                    }
                    output.fd.sync()
                }
            }
            if (target.length() != spec.size) {
                throw IOException("${spec.name} 下载不完整，请重试")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyFile(file: File, spec: NeuralSpeakerFilterRemoteFile) {
        if (!file.isFile || file.length() != spec.size) {
            throw IOException("${spec.name} 文件大小校验失败")
        }
        val actual = sha256(file)
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            file.delete()
            throw IOException("${spec.name} 完整性校验失败，请重新下载")
        }
    }

    private fun replaceActiveDirectory() {
        val previous = File(root, ".previous")
        previous.deleteRecursively()
        if (activeDir.exists() && !activeDir.renameTo(previous)) {
            activeDir.deleteRecursively()
        }
        if (!stagingDir.renameTo(activeDir)) {
            activeDir.mkdirs()
            stagingDir.copyRecursively(activeDir, overwrite = true)
            stagingDir.deleteRecursively()
        }
        previous.deleteRecursively()
    }

    private fun writeManifest(dir: File) {
        val json = JSONObject().apply {
            put("version", NeuralSpeakerFilterResourceSpec.VERSION)
            put("installedAtMs", System.currentTimeMillis())
            put("source", "penta2himajin/mellonella")
        }
        File(dir, MANIFEST_FILE_NAME).writeText(json.toString(), Charsets.UTF_8)
    }

    private fun writeNotice(dir: File) {
        File(dir, NOTICE_FILE_NAME).writeText(
            """
            Neural target-speaker filter resources used by KIGTTS.

            Target-speaker extraction model:
            penta2himajin/tse-conv-tasnet-48k, revision 5d8934d48e582dbd00285697bde972c4ec17ba2a
            License: Creative Commons Attribution 4.0 International (CC BY 4.0)
            https://huggingface.co/penta2himajin/tse-conv-tasnet-48k

            ECAPA-TDNN ONNX export:
            penta2himajin/ecapa-tdnn-onnx, revision 57bc773c7cc1a8afa117b38b0b2a38c96ffa99a2
            Based on speechbrain/spkrec-ecapa-voxceleb
            License: Apache License 2.0
            https://huggingface.co/penta2himajin/ecapa-tdnn-onnx
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun readManifest(): JSONObject? {
        if (!manifestFile.isFile) return null
        return runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun cleanupInterruptedInstall() {
        if (!stagingDir.exists()) return
        NeuralSpeakerFilterResourceSpec.files.forEach { spec ->
            val file = File(stagingDir, spec.name)
            if (file.length() > spec.size) file.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val MANIFEST_FILE_NAME = "resource.json"
        const val NOTICE_FILE_NAME = "NOTICE.txt"
        const val MIN_FREE_SPACE_AFTER_INSTALL = 64L * 1024L * 1024L
    }
}
