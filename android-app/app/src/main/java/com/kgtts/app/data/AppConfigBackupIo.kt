package com.lhtstudio.kigtts.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AppConfigBackupOptions(
    val includeQuickSubtitlePresets: Boolean = true,
    val includeQuickCardImages: Boolean = true,
    val includeCurrentFont: Boolean = true,
    val includeSoundboard: Boolean = false,
    val includeVoicePacks: Boolean = false
)

data class AppConfigRestoreResult(
    val restoredPreferenceCount: Int,
    val components: Set<String>,
    val restoredFileCount: Int
)

object AppConfigBackupIo {
    private const val ManifestEntry = "manifest.json"
    private const val PreferencesEntry = "preferences.json"
    private const val PackageType = "kigtts_config_backup"
    private const val LegacyPackageVersion = 1
    private const val PackageVersion = 2
    private const val MaxMetadataBytes = 16L * 1024L * 1024L
    private const val MaxBackupBytes = 32L * 1024L * 1024L * 1024L

    const val ComponentQuickSubtitle = "quick_subtitle_presets"
    const val ComponentQuickCardImages = "quick_card_images"
    const val ComponentCurrentFont = "current_font"
    const val ComponentSoundboard = "soundboard"
    const val ComponentVoicePacks = "voice_packs"
    const val ComponentLockScreenWallpaper = "lock_screen_wallpaper"

    suspend fun exportPackage(context: Context, options: AppConfigBackupOptions): File {
        val settings = UserPrefs.getSettings(context)
        val preferences = UserPrefs.exportPreferencesForBackup(
            context = context,
            includeQuickSubtitlePresets = options.includeQuickSubtitlePresets,
            includeSoundboard = options.includeSoundboard
        )
        val selectedFontIds = selectedFontIdsForBackup(settings, options.includeCurrentFont)
        val lockScreenWallpaper = settings.lockScreenSettings.wallpaperPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
        val components = buildSet {
            if (options.includeQuickSubtitlePresets) add(ComponentQuickSubtitle)
            if (options.includeQuickCardImages) add(ComponentQuickCardImages)
            if (options.includeSoundboard) add(ComponentSoundboard)
            if (options.includeVoicePacks) add(ComponentVoicePacks)
            if (selectedFontIds.isNotEmpty()) {
                add(ComponentCurrentFont)
            }
            if (lockScreenWallpaper != null) add(ComponentLockScreenWallpaper)
        }
        ResourceStorageCleaner.cleanupShareCache(context)
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val output = File(shareDir, "kigtts_config_$timestamp.kigconfig")
        val manifest = JSONObject()
            .put("type", PackageType)
            .put("version", PackageVersion)
            .put("createdAt", System.currentTimeMillis())
            .put("components", JSONArray(components.toList().sorted()))
            .put("fontIds", JSONArray(selectedFontIds.toList().sorted()))

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeTextEntry(zip, ManifestEntry, manifest.toString(2))
            writeTextEntry(zip, PreferencesEntry, preferences.toString())
            if (ComponentQuickCardImages in components) {
                writeDirectory(zip, File(context.filesDir, "quick_cards"), "resources/quick_cards")
            }
            if (ComponentSoundboard in components) {
                writeDirectory(zip, SoundboardPresetIo.audioDir(context), "resources/soundboard")
            }
            if (ComponentVoicePacks in components) {
                writeDirectory(zip, File(context.filesDir, "models/voice"), "resources/voice")
                writeDirectory(zip, File(context.filesDir, "models/kokoro"), "resources/kokoro")
            }
            if (ComponentCurrentFont in components) {
                selectedFontIds.forEach { selectedFontId ->
                    val fontId = sanitizePathSegment(selectedFontId)
                    writeDirectory(
                        zip,
                        File(File(context.filesDir, "fonts"), fontId),
                        "resources/font/$fontId"
                    )
                }
            }
            if (ComponentLockScreenWallpaper in components && lockScreenWallpaper != null) {
                writeFileEntry(zip, lockScreenWallpaper, "resources/lock_screen/wallpaper")
            }
        }
        return output
    }

    suspend fun importPackage(context: Context, uri: Uri): AppConfigRestoreResult {
        val staging = File(context.cacheDir, "config_restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var manifestBytes: ByteArray? = null
            var preferencesBytes: ByteArray? = null
            var totalBytes = 0L
            var restoredFiles = 0
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = normalizeEntryName(entry.name)
                        if (!entry.isDirectory) {
                            when {
                                name == ManifestEntry -> {
                                    manifestBytes = readLimited(zip, MaxMetadataBytes)
                                    totalBytes += manifestBytes?.size ?: 0
                                }
                                name == PreferencesEntry -> {
                                    preferencesBytes = readLimited(zip, MaxMetadataBytes)
                                    totalBytes += preferencesBytes?.size ?: 0
                                }
                                name.startsWith("resources/") -> {
                                    val target = checkedStagingFile(staging, name)
                                    target.parentFile?.mkdirs()
                                    target.outputStream().buffered().use { output ->
                                        totalBytes += copyLimited(zip, output, MaxBackupBytes - totalBytes)
                                    }
                                    restoredFiles += 1
                                }
                            }
                        }
                        require(totalBytes <= MaxBackupBytes) { "配置备份包过大" }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法打开配置备份")

            val manifest = manifestBytes?.toString(Charsets.UTF_8)?.let(::JSONObject)
                ?: error("配置备份缺少 manifest.json")
            require(manifest.optString("type") == PackageType) { "不是 KIGTTS 配置备份" }
            val packageVersion = manifest.optInt("version", 0)
            require(isSupportedPackageVersion(packageVersion)) {
                "不支持的配置备份版本"
            }
            val preferences = preferencesBytes?.toString(Charsets.UTF_8)?.let(::JSONObject)
                ?: error("配置备份缺少 preferences.json")
            val components = manifest.optJSONArray("components").toStringSet()

            if (ComponentQuickCardImages in components) {
                restoreDirectory(staging, "resources/quick_cards", File(context.filesDir, "quick_cards"))
            }
            if (ComponentSoundboard in components) {
                restoreDirectory(staging, "resources/soundboard", File(context.filesDir, "soundboard/audio"))
            }
            if (ComponentVoicePacks in components) {
                restoreDirectory(staging, "resources/voice", File(context.filesDir, "models/voice"))
                restoreDirectory(staging, "resources/kokoro", File(context.filesDir, "models/kokoro"))
            }
            if (ComponentCurrentFont in components) {
                restoreDirectory(staging, "resources/font", File(context.filesDir, "fonts"))
            }
            if (ComponentLockScreenWallpaper in components) {
                restoreDirectory(staging, "resources/lock_screen", File(context.filesDir, "lock_screen"))
            }
            val preferenceCount = UserPrefs.importPreferencesFromBackup(
                context = context,
                payload = preferences,
                replaceExisting = usesFullSettingsSnapshot(packageVersion),
                preserveQuickSubtitlePresets =
                    usesFullSettingsSnapshot(packageVersion) && ComponentQuickSubtitle !in components,
                preserveSoundboard =
                    usesFullSettingsSnapshot(packageVersion) && ComponentSoundboard !in components
            )
            normalizeRestoredResourceReferences(context, components)
            AppFontChangeBus.notifyChanged()
            return AppConfigRestoreResult(
                restoredPreferenceCount = preferenceCount,
                components = components,
                restoredFileCount = restoredFiles
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun writeTextEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeDirectory(zip: ZipOutputStream, root: File, prefix: String) {
        if (!root.isDirectory) return
        val canonicalRoot = root.canonicalFile
        canonicalRoot.walkTopDown()
            .filter { file ->
                val segments = file.relativeTo(canonicalRoot).invariantSeparatorsPath.split('/')
                file.isFile &&
                    !file.name.endsWith(".tmp", ignoreCase = true) &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    segments.none { it.startsWith(".import-") || it.startsWith(".staging-") }
            }
            .forEach { file ->
                val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                val entryName = "$prefix/$relative"
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
    }

    private fun restoreDirectory(staging: File, sourcePath: String, targetRoot: File) {
        val sourceRoot = checkedStagingFile(staging, sourcePath)
        if (!sourceRoot.isDirectory) return
        targetRoot.mkdirs()
        sourceRoot.walkTopDown().filter { it.isFile }.forEach { source ->
            val relative = source.relativeTo(sourceRoot).invariantSeparatorsPath
            val target = checkedChildFile(targetRoot, relative)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun normalizeEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." }) {
            "配置备份包含不安全路径"
        }
        return normalized
    }

    private fun checkedStagingFile(staging: File, relative: String): File =
        checkedChildFile(staging, relative)

    private fun checkedChildFile(root: File, relative: String): File {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, relative).canonicalFile
        require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
            "配置备份包含越界路径"
        }
        return target
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyLimited(input, output, maxBytes)
        return output.toByteArray()
    }

    private fun copyLimited(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long
    ): Long {
        require(maxBytes >= 0L) { "配置备份包过大" }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "配置备份包过大" }
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun sanitizePathSegment(raw: String): String {
        require(Regex("^[A-Za-z0-9._-]{1,96}$").matches(raw)) { "当前字体标识无效" }
        return raw
    }

    internal fun selectedFontIdsForBackup(
        settings: UserPrefs.AppSettings,
        includeCurrentFont: Boolean
    ): Set<String> {
        if (!includeCurrentFont) return emptySet()
        return buildSet {
            settings.appFontId
                .takeUnless { it == AppFontDefaults.SystemFontId }
                ?.let(::add)
            settings.lockScreenSettings
                .takeIf { it.useSeparateClockFont }
                ?.clockFontId
                ?.takeUnless { it == AppFontDefaults.SystemFontId }
                ?.let(::add)
        }
    }

    internal fun isSupportedPackageVersion(version: Int): Boolean =
        version in LegacyPackageVersion..PackageVersion

    internal fun usesFullSettingsSnapshot(version: Int): Boolean = version >= PackageVersion

    private suspend fun normalizeRestoredResourceReferences(
        context: Context,
        components: Set<String>
    ) {
        var settings = UserPrefs.getSettings(context)
        if (
            settings.appFontId != AppFontDefaults.SystemFontId &&
            AppFontRepository.resolveFontFamilySource(context, settings.appFontId) == null
        ) {
            UserPrefs.setAppFont(
                context,
                AppFontDefaults.SystemFontId,
                AppFontDefaults.DefaultWeight
            )
            settings = settings.copy(
                appFontId = AppFontDefaults.SystemFontId,
                appFontWeight = AppFontDefaults.DefaultWeight
            )
        }

        val restoredWallpaper = File(context.filesDir, "lock_screen/wallpaper")
            .takeIf { ComponentLockScreenWallpaper in components && it.isFile }
        val originalLockSettings = settings.lockScreenSettings
        var lockSettings = originalLockSettings.copy(
            wallpaperPath = restoredWallpaper?.absolutePath
                ?: originalLockSettings.wallpaperPath.takeIf { File(it).isFile }.orEmpty()
        )
        if (
            lockSettings.clockFontId != AppFontDefaults.SystemFontId &&
            AppFontRepository.resolveFontFamilySource(context, lockSettings.clockFontId) == null
        ) {
            lockSettings = lockSettings.copy(
                clockFontId = AppFontDefaults.SystemFontId,
                clockFontWeight = AppFontDefaults.DefaultWeight
            )
        }
        if (lockSettings != originalLockSettings) {
            UserPrefs.setLockScreenSettings(context, lockSettings)
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
