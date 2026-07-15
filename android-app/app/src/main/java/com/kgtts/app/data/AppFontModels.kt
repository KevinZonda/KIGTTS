package com.lhtstudio.kigtts.app.data

import java.io.File

internal object AppFontDefaults {
    const val SystemFontId = "system"
    const val DefaultWeight = 400
    const val MinWeight = 1
    const val MaxWeight = 1000
    const val ManifestFileName = "font_manifest.json"
    const val MetadataFileName = "font.json"
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
}

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
    val preferredWeight: Int,
    val installedAt: Long
) {
    val isSystem: Boolean get() = origin == AppFontOrigin.System
    val isVariable: Boolean get() = weightAxis != null
    val isRemovable: Boolean get() = !isSystem
}

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
    val weightAxis: AppFontWeightAxis?
)

internal enum class AppFontRemoteSource(
    val displayName: String,
    val repositoryBaseUrl: String
) {
    ModelScope(
        displayName = "魔搭",
        repositoryBaseUrl =
            "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_FONTS_Resource/resolve/master"
    ),
    HuggingFace(
        displayName = "Hugging Face",
        repositoryBaseUrl =
            "https://huggingface.co/LHTSTUDIO/KIGTTS_FONTS_Resource/resolve/main"
    );

    val manifestUrl: String get() = "$repositoryBaseUrl/${AppFontDefaults.ManifestFileName}"
}

internal data class AppFontInstallProgress(
    val fraction: Float?,
    val stage: String
)
