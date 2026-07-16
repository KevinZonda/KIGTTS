package com.lhtstudio.kigtts.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal class AppFontRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, FontsDirectoryName)

    suspend fun listInstalledFonts(): List<InstalledAppFont> = withContext(Dispatchers.IO) {
        root.mkdirs()
        val installed = root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull(::readInstalledFont)
            .sortedBy { it.displayName.lowercase() }
            .toList()
        listOf(systemFont()) + installed
    }

    suspend fun importFont(
        uri: Uri,
        resolver: ContentResolver
    ): InstalledAppFont = withContext(Dispatchers.IO) {
        root.mkdirs()
        val sourceName = resolver.displayName(uri).ifBlank { "imported-font.ttf" }
        val extension = sourceName.substringAfterLast('.', "ttf").lowercase()
        if (extension !in SupportedExtensions) {
            throw IOException("仅支持 TTF 和 OTF 字体文件")
        }
        val temp = File(root, ".import-${UUID.randomUUID()}.$extension")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output, CopyBufferSize) }
            } ?: throw IOException("无法读取所选字体文件")
            val parsed = validateFont(temp)
            val sha256 = sha256(temp)
            val id = "import-${sha256.take(16)}"
            val existing = readInstalledFont(File(root, id))
            if (existing != null) throw IOException("该字体已经导入")
            val staging = File(root, ".staging-${UUID.randomUUID()}")
            staging.mkdirs()
            val fontFile = File(staging, "font.$extension")
            if (!temp.renameTo(fontFile)) temp.copyTo(fontFile, overwrite = true)
            val preferredWeight = parsed.weightAxis?.default ?: AppFontDefaults.DefaultWeight
            AppFontMetadataStore.write(
                staging,
                StoredAppFontMetadata(
                    id = id,
                    displayName = parsed.familyName.ifBlank { sourceName.substringBeforeLast('.') },
                    origin = AppFontOrigin.Imported,
                    fontFileName = fontFile.name,
                    sha256 = sha256,
                    version = "本地导入",
                    licenseName = "未提供许可证",
                    licenseFileName = "",
                    licenseUrl = "",
                    sourceUrl = "",
                    weightAxis = parsed.weightAxis,
                    fontFiles = listOf(
                        StoredAppFontFile(preferredWeight, fontFile.name, sha256)
                    ),
                    defaultWeight = preferredWeight,
                    preferredWeight = preferredWeight,
                    installedAt = System.currentTimeMillis()
                )
            )
            moveStagingIntoPlace(staging, File(root, id))
            AppFontChangeBus.notifyChanged()
            readInstalledFont(File(root, id)) ?: throw IOException("字体导入后无法读取")
        } finally {
            temp.delete()
        }
    }

    suspend fun fetchCatalog(repositoryBaseUrl: String): List<RemoteAppFont> =
        withContext(Dispatchers.IO) {
            val raw = readUrlText(
                assetUrl(repositoryBaseUrl, AppFontDefaults.ManifestFileName),
                MaxManifestBytes
            )
            AppFontCatalogParser.parse(raw)
        }

    suspend fun installRemoteFont(
        font: RemoteAppFont,
        repositoryBaseUrl: String,
        onProgress: (AppFontInstallProgress) -> Unit
    ): InstalledAppFont = withContext(Dispatchers.IO) {
        root.mkdirs()
        val safeId = sanitizeId(font.id)
        val extension = font.fontPath.substringAfterLast('.', "ttf").lowercase()
        if (extension !in SupportedExtensions) throw IOException("远端字体格式不受支持")
        val staging = File(root, ".staging-${UUID.randomUUID()}")
        staging.mkdirs()
        try {
            val declaredPrimary = font.weightFiles.firstOrNull { it.path == font.fontPath }
            val primarySpec = RemoteAppFontWeightFile(
                weight = declaredPrimary?.weight ?: font.defaultWeight,
                path = font.fontPath,
                sha256 = font.fontSha256,
                sizeBytes = font.sizeBytes
            )
            val specs = (listOf(primarySpec) + font.weightFiles)
                .distinctBy { it.path }
            val storedFiles = mutableListOf<StoredAppFontFile>()
            lateinit var fontFile: File
            lateinit var primaryParsed: OpenTypeFontInfo
            specs.forEachIndexed { index, spec ->
                val fileExtension = spec.path.substringAfterLast('.', "ttf").lowercase()
                if (fileExtension !in SupportedExtensions) {
                    throw IOException("远端字体格式不受支持")
                }
                val target = if (index == 0) {
                    File(staging, "font.$fileExtension")
                } else {
                    File(staging, "font-${spec.weight}.$fileExtension")
                }
                val stage = if (specs.size == 1) {
                    "正在下载 ${font.displayName}"
                } else {
                    "正在下载 ${font.displayName}（${index + 1}/${specs.size}）"
                }
                onProgress(AppFontInstallProgress(0f, stage))
                downloadFile(assetUrl(repositoryBaseUrl, spec.path), target) { current, total ->
                    val fraction = if (total > 0L) current.toFloat() / total.toFloat() else null
                    onProgress(AppFontInstallProgress(fraction, stage))
                }
                onProgress(AppFontInstallProgress(null, "正在校验 ${spec.weight} 字重"))
                if (target.length() != spec.sizeBytes) throw IOException("字体文件大小校验失败")
                val hash = sha256(target)
                if (!hash.equals(spec.sha256, ignoreCase = true)) {
                    throw IOException("字体文件校验失败")
                }
                val parsed = validateFont(target)
                if (index == 0) {
                    fontFile = target
                    primaryParsed = parsed
                }
                storedFiles += StoredAppFontFile(spec.weight, target.name, hash)
            }
            val licenseFile = font.licensePath.takeIf { it.isNotBlank() }?.let { path ->
                onProgress(AppFontInstallProgress(null, "正在下载许可证"))
                File(staging, "LICENSE.txt").also { target ->
                    downloadFile(assetUrl(repositoryBaseUrl, path), target)
                    if (font.licenseSha256.isNotBlank() &&
                        !sha256(target).equals(font.licenseSha256, ignoreCase = true)
                    ) {
                        throw IOException("许可证文件校验失败")
                    }
                }
            }
            val axis = primaryParsed.weightAxis?.let { parsedAxis ->
                font.weightAxis?.let { declaredAxis ->
                    parsedAxis.withDefault(declaredAxis.default)
                } ?: parsedAxis
            } ?: font.weightAxis
            val availableWeights = storedFiles.map { it.weight }
            val defaultWeight = axis?.default
                ?: availableWeights.nearestTo(font.defaultWeight)
                ?: AppFontDefaults.DefaultWeight
            AppFontMetadataStore.write(
                staging,
                StoredAppFontMetadata(
                    id = safeId,
                    displayName = font.displayName,
                    origin = AppFontOrigin.Downloaded,
                    fontFileName = fontFile.name,
                    sha256 = storedFiles.first().sha256,
                    version = font.version,
                    licenseName = font.licenseName,
                    licenseFileName = licenseFile?.name.orEmpty(),
                    licenseUrl = font.licenseUrl,
                    sourceUrl = font.sourceUrl,
                    weightAxis = axis,
                    fontFiles = storedFiles,
                    defaultWeight = defaultWeight,
                    preferredWeight = defaultWeight,
                    installedAt = System.currentTimeMillis()
                )
            )
            onProgress(AppFontInstallProgress(null, "正在安装字体"))
            moveStagingIntoPlace(staging, File(root, safeId))
            AppFontChangeBus.notifyChanged()
            readInstalledFont(File(root, safeId)) ?: throw IOException("字体安装后无法读取")
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    suspend fun updatePreferredWeight(id: String, weight: Int): InstalledAppFont =
        withContext(Dispatchers.IO) {
            val dir = checkedFontDirectory(id)
            val current = AppFontMetadataStore.read(dir) ?: throw IOException("字体不存在")
            val normalized = current.weightAxis?.clamp(weight)
                ?: current.fontFiles.map { it.weight }.nearestTo(weight)
                ?: AppFontDefaults.DefaultWeight
            AppFontMetadataStore.write(dir, current.copy(preferredWeight = normalized))
            readInstalledFont(dir) ?: throw IOException("无法更新字体字重")
        }

    suspend fun deleteFont(id: String) = withContext(Dispatchers.IO) {
        if (id == AppFontDefaults.SystemFontId) throw IOException("系统字体不可删除")
        val dir = checkedFontDirectory(id)
        if (dir.exists() && !dir.deleteRecursively()) throw IOException("字体删除失败")
    }

    suspend fun readLicense(font: InstalledAppFont): String = withContext(Dispatchers.IO) {
        val file = font.licenseFile ?: return@withContext "此字体没有随附许可证文件。"
        if (!file.isFile) return@withContext "许可证文件不存在。"
        file.readText(Charsets.UTF_8)
    }

    private fun readInstalledFont(dir: File): InstalledAppFont? {
        val meta = AppFontMetadataStore.read(dir) ?: return null
        val fontFile = File(dir, meta.fontFileName).takeIf { it.isFile } ?: return null
        val weightFiles = meta.fontFiles.mapNotNull { stored ->
            File(dir, stored.fileName).takeIf { it.isFile }?.let { file ->
                InstalledAppFontWeightFile(stored.weight, file, stored.sha256)
            }
        }.ifEmpty {
            listOf(InstalledAppFontWeightFile(meta.defaultWeight, fontFile, meta.sha256))
        }
        val licenseFile = meta.licenseFileName.takeIf { it.isNotBlank() }
            ?.let { File(dir, it) }
            ?.takeIf { it.isFile }
        val availableWeights = weightFiles.map { it.weight }
        val defaultWeight = meta.weightAxis?.default
            ?: availableWeights.nearestTo(meta.defaultWeight)
            ?: AppFontDefaults.DefaultWeight
        val preferredWeight = meta.weightAxis?.clamp(meta.preferredWeight)
            ?: availableWeights.nearestTo(meta.preferredWeight)
            ?: defaultWeight
        return InstalledAppFont(
            id = meta.id,
            displayName = meta.displayName,
            origin = meta.origin,
            fontFile = fontFile,
            sha256 = meta.sha256,
            version = meta.version,
            licenseName = meta.licenseName,
            licenseFile = licenseFile,
            licenseUrl = meta.licenseUrl,
            sourceUrl = meta.sourceUrl,
            weightAxis = meta.weightAxis,
            weightFiles = weightFiles,
            defaultWeight = defaultWeight,
            preferredWeight = preferredWeight,
            installedAt = meta.installedAt
        )
    }

    private fun validateFont(file: File): OpenTypeFontInfo {
        val parsed = OpenTypeFontParser.parse(file)
        runCatching { Typeface.Builder(file).build() }
            .getOrElse { throw IOException("Android 无法加载该字体", it) }
        return parsed
    }

    private fun moveStagingIntoPlace(staging: File, target: File) {
        val backup = File(root, ".backup-${UUID.randomUUID()}")
        if (target.exists() && !target.renameTo(backup)) throw IOException("无法替换旧字体")
        try {
            if (!staging.renameTo(target)) throw IOException("无法完成字体安装")
            if (backup.exists()) backup.deleteRecursively()
        } catch (error: Throwable) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    private fun checkedFontDirectory(id: String): File {
        val safeId = sanitizeId(id)
        val dir = File(root, safeId).canonicalFile
        if (dir.parentFile != root.canonicalFile) throw IOException("字体目录无效")
        return dir
    }

    private fun readUrlText(url: String, maxBytes: Int): String {
        val connection = openConnection(url)
        return try {
            connection.inputStream.use { input ->
                val buffer = ByteArray(maxBytes + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count > maxBytes) throw IOException("字体清单过大")
                buffer.copyOf(count).toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadFile(
        url: String,
        target: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        val connection = openConnection(url)
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(CopyBufferSize)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("User-Agent", "KIGTTS-Android/0.1")
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException("下载请求失败：HTTP $code")
        }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CopyBufferSize)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeId(value: String): String {
        val normalized = value.trim().lowercase()
        if (!IdPattern.matches(normalized)) throw IOException("字体 ID 无效")
        return normalized
    }

    private fun assetUrl(repositoryBaseUrl: String, path: String): String {
        val normalizedBaseUrl = repositoryBaseUrl.trim().trimEnd('/')
        val encoded = path.split('/').joinToString("/") { segment ->
            URI(null, null, segment, null).rawPath
        }
        return "$normalizedBaseUrl/$encoded"
    }

    private fun ContentResolver.displayName(uri: Uri): String = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    companion object {
        private const val FontsDirectoryName = "fonts"
        private const val CopyBufferSize = 64 * 1024
        private const val MaxManifestBytes = 1024 * 1024
        private val SupportedExtensions = setOf("ttf", "otf")
        private val IdPattern = Regex("^[a-z0-9][a-z0-9._-]{0,79}$")

        fun systemFont(): InstalledAppFont = InstalledAppFont(
            id = AppFontDefaults.SystemFontId,
            displayName = "系统字体",
            origin = AppFontOrigin.System,
            fontFile = null,
            sha256 = "",
            version = "Android 系统默认",
            licenseName = "由系统提供",
            licenseFile = null,
            licenseUrl = "",
            sourceUrl = "",
            weightAxis = null,
            weightFiles = emptyList(),
            defaultWeight = AppFontDefaults.DefaultWeight,
            preferredWeight = AppFontDefaults.DefaultWeight,
            installedAt = 0L
        )

        fun resolveFontFamilySource(context: Context, id: String): AppFontFamilySource? {
            if (id == AppFontDefaults.SystemFontId || !IdPattern.matches(id)) return null
            val dir = File(File(context.filesDir, FontsDirectoryName), id)
            val metadata = AppFontMetadataStore.read(dir) ?: return null
            val files = metadata.fontFiles.mapNotNull { stored ->
                File(dir, stored.fileName).takeIf { it.isFile }?.let { file ->
                    AppFontFileSource(file.absolutePath, stored.weight)
                }
            }.ifEmpty {
                File(dir, metadata.fontFileName).takeIf { it.isFile }?.let { file ->
                    listOf(AppFontFileSource(file.absolutePath, metadata.defaultWeight))
                }.orEmpty()
            }
            if (files.isEmpty()) return null
            return AppFontFamilySource(
                files = files.distinctBy { it.path }.sortedBy { it.weight },
                defaultWeight = metadata.weightAxis?.default ?: metadata.defaultWeight
            )
        }
    }
}
