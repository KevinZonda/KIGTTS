package com.lhtstudio.kigtts.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class StoredAppFontFile(
    val weight: Int,
    val fileName: String,
    val sha256: String
)

internal data class StoredAppFontMetadata(
    val id: String,
    val displayName: String,
    val origin: AppFontOrigin,
    val fontFileName: String,
    val sha256: String,
    val version: String,
    val licenseName: String,
    val licenseFileName: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val weightAxis: AppFontWeightAxis?,
    val fontFiles: List<StoredAppFontFile>,
    val defaultWeight: Int,
    val preferredWeight: Int,
    val installedAt: Long
)

internal object AppFontMetadataStore {
    fun read(directory: File): StoredAppFontMetadata? = runCatching {
        val json = JSONObject(
            File(directory, AppFontDefaults.MetadataFileName).readText(Charsets.UTF_8)
        )
        val id = json.getString("id").trim().lowercase()
        require(IdPattern.matches(id))
        val primaryFileName = checkedFileName(json.getString("fontFileName"))
        val min = json.optInt("weightMin", 0)
        val max = json.optInt("weightMax", 0)
        val axis = if (min > 0 && max >= min) {
            AppFontWeightAxis(
                min = min,
                default = json.optInt("weightDefault", AppFontDefaults.DefaultWeight)
                    .coerceIn(min, max),
                max = max
            )
        } else {
            null
        }
        val defaultWeight = axis?.default
            ?: json.optInt("weightDefault", AppFontDefaults.DefaultWeight)
                .coerceIn(AppFontDefaults.MinWeight, AppFontDefaults.MaxWeight)
        val storedFiles = parseFontFiles(json.optJSONArray("fontFiles"))
        val primary = StoredAppFontFile(
            weight = defaultWeight,
            fileName = primaryFileName,
            sha256 = json.optString("sha256")
        )
        val fontFiles = (storedFiles + primary)
            .distinctBy { it.fileName }
            .sortedBy { it.weight }
        StoredAppFontMetadata(
            id = id,
            displayName = json.getString("displayName").trim().take(100),
            origin = AppFontOrigin.fromWireName(json.optString("origin")),
            fontFileName = primaryFileName,
            sha256 = json.optString("sha256"),
            version = json.optString("version"),
            licenseName = json.optString("licenseName", "未提供许可证"),
            licenseFileName = json.optString("licenseFileName")
                .takeIf { it.isNotBlank() }
                ?.let(::checkedFileName)
                .orEmpty(),
            licenseUrl = json.optString("licenseUrl"),
            sourceUrl = json.optString("sourceUrl"),
            weightAxis = axis,
            fontFiles = fontFiles,
            defaultWeight = defaultWeight,
            preferredWeight = json.optInt("preferredWeight", defaultWeight),
            installedAt = json.optLong("installedAt", 0L)
        )
    }.getOrNull()

    fun write(directory: File, metadata: StoredAppFontMetadata) {
        directory.mkdirs()
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", metadata.id)
            put("displayName", metadata.displayName)
            put("origin", metadata.origin.wireName)
            put("fontFileName", metadata.fontFileName)
            put("sha256", metadata.sha256)
            put("version", metadata.version)
            put("licenseName", metadata.licenseName)
            put("licenseFileName", metadata.licenseFileName)
            put("licenseUrl", metadata.licenseUrl)
            put("sourceUrl", metadata.sourceUrl)
            put("weightMin", metadata.weightAxis?.min ?: 0)
            put("weightDefault", metadata.defaultWeight)
            put("weightMax", metadata.weightAxis?.max ?: 0)
            put("preferredWeight", metadata.preferredWeight)
            put("fontFiles", JSONArray().apply {
                metadata.fontFiles.sortedBy { it.weight }.forEach { file ->
                    put(JSONObject().apply {
                        put("weight", file.weight)
                        put("fileName", file.fileName)
                        put("sha256", file.sha256)
                    })
                }
            })
            put("installedAt", metadata.installedAt)
        }
        writeTextAtomically(
            File(directory, AppFontDefaults.MetadataFileName),
            json.toString(2)
        )
    }

    private fun parseFontFiles(array: JSONArray?): List<StoredAppFontFile> = buildList {
        if (array == null || array.length() > MaxWeightFiles) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val weight = item.optInt("weight", 0)
            if (weight !in AppFontDefaults.MinWeight..AppFontDefaults.MaxWeight) continue
            add(
                StoredAppFontFile(
                    weight = weight,
                    fileName = checkedFileName(item.getString("fileName")),
                    sha256 = item.optString("sha256")
                )
            )
        }
    }

    private fun checkedFileName(value: String): String {
        val name = value.trim()
        if (name.isBlank() || name != File(name).name) throw IOException("字体文件名无效")
        return name
    }

    private fun writeTextAtomically(target: File, text: String) {
        val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temp.writeText(text, Charsets.UTF_8)
            if (target.exists() && !target.delete()) throw IOException("无法更新字体元数据")
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
        } finally {
            temp.delete()
        }
    }

    private const val MaxWeightFiles = 32
    private val IdPattern = Regex("^[a-z0-9][a-z0-9._-]{0,79}$")
}
