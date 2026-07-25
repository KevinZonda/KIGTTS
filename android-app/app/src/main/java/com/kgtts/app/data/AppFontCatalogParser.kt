package com.lhtstudio.kigtts.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal object AppFontCatalogParser {
    fun parse(raw: String): List<RemoteAppFont> {
        val root = JSONObject(raw)
        if (root.optInt("schemaVersion", 0) != 1) {
            throw IOException("字体清单版本不受支持")
        }
        val entries = root.optJSONArray("fonts") ?: throw IOException("字体清单缺少 fonts")
        if (entries.length() > MaxFonts) throw IOException("字体清单条目过多")
        val fonts = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: throw IOException("字体清单条目无效")
                add(parseFont(item))
            }
        }
        if (fonts.map { it.id }.distinct().size != fonts.size) {
            throw IOException("字体清单包含重复 ID")
        }
        return fonts
    }

    private fun parseFont(json: JSONObject): RemoteAppFont {
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
        val weightFiles = parseWeightFiles(json.optJSONArray("weightFiles"))
        val declaredDefault = axis?.default
            ?: json.optInt("weightDefault", AppFontDefaults.DefaultWeight)
                .coerceIn(AppFontDefaults.MinWeight, AppFontDefaults.MaxWeight)
        val defaultWeight = if (weightFiles.isEmpty()) {
            declaredDefault
        } else {
            weightFiles.map { it.weight }.nearestTo(declaredDefault) ?: declaredDefault
        }
        val licensePath = json.optString("licensePath").let {
            if (it.isBlank()) "" else checkedRelativePath(it)
        }
        return RemoteAppFont(
            id = sanitizeId(json.getString("id")),
            displayName = json.getString("displayName").trim().take(100),
            version = json.optString("version"),
            description = json.optString("description"),
            fontPath = checkedRelativePath(json.getString("fontPath")),
            fontSha256 = checkedSha256(json.getString("fontSha256")),
            sizeBytes = checkedSize(json.optLong("sizeBytes", 0L)),
            licenseName = json.optString("licenseName", "未标注"),
            licensePath = licensePath,
            licenseSha256 = json.optString("licenseSha256").let {
                if (licensePath.isBlank() && it.isBlank()) "" else checkedSha256(it)
            },
            licenseUrl = json.optString("licenseUrl"),
            sourceUrl = json.optString("sourceUrl"),
            weightAxis = axis,
            weightFiles = weightFiles,
            defaultWeight = defaultWeight
        )
    }

    private fun parseWeightFiles(array: JSONArray?): List<RemoteAppFontWeightFile> {
        if (array == null) return emptyList()
        if (array.length() > MaxWeightFiles) throw IOException("字体字重文件过多")
        val files = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IOException("字体字重文件条目无效")
                val weight = item.optInt("weight", 0)
                if (weight !in AppFontDefaults.MinWeight..AppFontDefaults.MaxWeight) {
                    throw IOException("字体字重无效")
                }
                add(
                    RemoteAppFontWeightFile(
                        weight = weight,
                        path = checkedRelativePath(item.getString("fontPath")),
                        sha256 = checkedSha256(item.getString("fontSha256")),
                        sizeBytes = checkedSize(item.optLong("sizeBytes", 0L))
                    )
                )
            }
        }
        if (files.map { it.weight }.distinct().size != files.size ||
            files.map { it.path }.distinct().size != files.size
        ) {
            throw IOException("字体字重文件重复")
        }
        return files.sortedBy { it.weight }
    }

    private fun sanitizeId(value: String): String {
        val normalized = value.trim().lowercase()
        if (!IdPattern.matches(normalized)) throw IOException("字体 ID 无效")
        return normalized
    }

    private fun checkedRelativePath(value: String): String {
        val normalized = value.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.split('/').any {
                it.isBlank() || it == "." || it == ".."
            }
        ) {
            throw IOException("字体清单路径无效")
        }
        return normalized
    }

    private fun checkedSha256(value: String): String {
        val normalized = value.trim().lowercase()
        if (!Sha256Pattern.matches(normalized)) throw IOException("字体清单哈希无效")
        return normalized
    }

    private fun checkedSize(value: Long): Long {
        if (value !in 1..MaxFileBytes) throw IOException("字体文件大小无效")
        return value
    }

    private const val MaxFonts = 100
    private const val MaxWeightFiles = 32
    private const val MaxFileBytes = 128L * 1024L * 1024L
    private val IdPattern = Regex("^[a-z0-9][a-z0-9._-]{0,79}$")
    private val Sha256Pattern = Regex("^[0-9a-f]{64}$")
}
