package com.lhtstudio.kigtts.app.data

import java.io.File
import java.net.URI

internal object AppFontDefaults {
    const val SystemFontId = "system"
    const val DefaultWeight = 400
    const val MinWeight = 1
    const val MaxWeight = 1000
    const val ManifestFileName = "font_manifest.json"
    const val MetadataFileName = "font.json"
    const val ClockFontIdPrefix = "clock-"

    fun isClockFontId(id: String): Boolean = id.startsWith(ClockFontIdPrefix)
}

internal enum class AppFontOrigin(val wireName: String) {
    System("system"),
    Imported("imported"),
    Downloaded("downloaded");

    companion object {
        fun fromWireName(value: String): AppFontOrigin =
            entries.firstOrNull { it.wireName == value } ?: Imported
    }
}

internal data class AppFontWeightAxis(
    val min: Int,
    val default: Int,
    val max: Int
) {
    fun clamp(weight: Int): Int = weight.coerceIn(min, max)

    fun withDefault(weight: Int): AppFontWeightAxis = copy(default = clamp(weight))
}

internal data class InstalledAppFontWeightFile(
    val weight: Int,
    val file: File,
    val sha256: String
)

data class AppFontFileSource(
    val path: String,
    val weight: Int
)

data class AppFontFamilySource(
    val files: List<AppFontFileSource>,
    val defaultWeight: Int
)

internal data class InstalledAppFont(
    val id: String,
    val displayName: String,
    val origin: AppFontOrigin,
    val fontFile: File?,
    val sha256: String,
    val version: String,
    val licenseName: String,
    val licenseFile: File?,
    val licenseUrl: String,
    val sourceUrl: String,
    val weightAxis: AppFontWeightAxis?,
    val weightFiles: List<InstalledAppFontWeightFile>,
    val defaultWeight: Int,
    val preferredWeight: Int,
    val installedAt: Long
) {
    val isSystem: Boolean get() = origin == AppFontOrigin.System
    val isVariable: Boolean get() = weightAxis != null
    val availableWeights: List<Int>
        get() = weightFiles.map { it.weight }.distinct().sorted()
    val supportsWeightSelection: Boolean
        get() = isVariable || availableWeights.size > 1
    val isRemovable: Boolean get() = !isSystem

    fun normalizeWeight(weight: Int): Int = weightAxis?.clamp(weight)
        ?: availableWeights.nearestTo(weight)
        ?: AppFontDefaults.DefaultWeight

    fun familySource(): AppFontFamilySource = AppFontFamilySource(
        files = weightFiles.map { AppFontFileSource(it.file.absolutePath, it.weight) },
        defaultWeight = defaultWeight
    )
}

internal data class RemoteAppFontWeightFile(
    val weight: Int,
    val path: String,
    val sha256: String,
    val sizeBytes: Long
)

internal data class RemoteAppFont(
    val id: String,
    val displayName: String,
    val version: String,
    val description: String,
    val fontPath: String,
    val fontSha256: String,
    val sizeBytes: Long,
    val licenseName: String,
    val licensePath: String,
    val licenseSha256: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val weightAxis: AppFontWeightAxis?,
    val weightFiles: List<RemoteAppFontWeightFile>,
    val defaultWeight: Int
) {
    val downloadSizeBytes: Long
        get() = (listOf(fontPath to sizeBytes) + weightFiles.map { it.path to it.sizeBytes })
            .distinctBy { it.first }
            .sumOf { it.second }
}

internal fun List<Int>.nearestTo(weight: Int): Int? =
    minWithOrNull(compareBy<Int> { kotlin.math.abs(it - weight) }.thenBy { it })

internal enum class AppFontRemoteSource(
    val preferenceValue: Int,
    val displayName: String,
    val defaultRepositoryBaseUrl: String
) {
    ModelScope(
        preferenceValue = 0,
        displayName = "魔搭",
        defaultRepositoryBaseUrl =
            "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_FONTS_Resource/resolve/master"
    ),
    HuggingFace(
        preferenceValue = 1,
        displayName = "Hugging Face",
        defaultRepositoryBaseUrl =
            "https://huggingface.co/LHT02/KIGTTS_FONTS_Resource/resolve/main"
    );

    fun normalizeRepositoryBaseUrl(value: String): String = value
        .trim()
        .trimEnd('/')
        .removeSuffix("/${AppFontDefaults.ManifestFileName}")
        .trimEnd('/')

    fun isValidRepositoryBaseUrl(value: String): Boolean {
        val normalized = normalizeRepositoryBaseUrl(value)
        return runCatching {
            val uri = URI(normalized)
            uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawQuery == null &&
                uri.rawFragment == null
        }.getOrDefault(false)
    }

    fun resolvedRepositoryBaseUrl(value: String): String =
        normalizeRepositoryBaseUrl(value).takeIf(::isValidRepositoryBaseUrl)
            ?: defaultRepositoryBaseUrl

    val clockRepositoryBaseUrl: String
        get() = when (this) {
            ModelScope ->
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_CLOCK_FONTS_Resource/resolve/master"
            HuggingFace ->
                "https://huggingface.co/LHT02/KIGTTS_CLOCK_FONTS_Resource/resolve/main"
        }

    companion object {
        fun fromPreferenceValue(value: Int): AppFontRemoteSource =
            entries.firstOrNull { it.preferenceValue == value } ?: ModelScope
    }
}

internal data class AppFontInstallProgress(
    val fraction: Float?,
    val stage: String
)
