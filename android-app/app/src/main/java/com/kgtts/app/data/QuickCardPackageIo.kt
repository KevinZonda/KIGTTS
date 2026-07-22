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

data class QuickCardPackageCard(
    val id: Long,
    val type: String,
    val title: String,
    val note: String,
    val themeColor: String,
    val link: String,
    val portraitImagePath: String,
    val landscapeImagePath: String
)

data class QuickCardPackageSummary(
    val id: Long,
    val title: String
)

object QuickCardPackageIo {
    private const val ManifestEntry = "manifest.json"
    private const val PackageType = "kigtts_quick_cards"
    private const val PackageVersion = 1
    private const val MaxManifestBytes = 4L * 1024L * 1024L
    private const val MaxImageBytes = 128L * 1024L * 1024L

    fun inspectPackage(context: Context, uri: Uri): List<QuickCardPackageSummary> {
        val cards = parseCards(readManifest(context, uri))
        require(cards.isNotEmpty()) { "名片包中没有可导入的名片" }
        return cards.map { card ->
            QuickCardPackageSummary(card.id, card.title.ifBlank { "未命名名片" })
        }
    }

    fun exportPackage(context: Context, cards: List<QuickCardPackageCard>): File {
        require(cards.isNotEmpty()) { "未选择需要导出的名片" }
        ResourceStorageCleaner.cleanupShareCache(context)
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val output = File(shareDir, "quick_cards_$timestamp.kigcard")
        val imageFiles = linkedMapOf<String, File>()
        val manifestCards = JSONArray()
        cards.forEachIndexed { index, card ->
            fun addImage(path: String, orientation: String): String {
                val file = File(path)
                if (!file.isFile) return ""
                val extension = file.extension.lowercase(Locale.US).takeIf { it.length in 2..5 } ?: "png"
                val entryName = "images/card_${index}_${orientation}_${UUID.randomUUID()}.$extension"
                imageFiles[entryName] = file
                return entryName
            }
            manifestCards.put(
                JSONObject()
                    .put("id", card.id)
                    .put("type", card.type)
                    .put("title", card.title)
                    .put("note", card.note)
                    .put("themeColor", card.themeColor)
                    .put("link", card.link)
                    .put("portraitImage", addImage(card.portraitImagePath, "portrait"))
                    .put("landscapeImage", addImage(card.landscapeImagePath, "landscape"))
            )
        }
        val manifest = JSONObject()
            .put("type", PackageType)
            .put("version", PackageVersion)
            .put("cards", manifestCards)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ManifestEntry))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            imageFiles.forEach { (entryName, file) ->
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output
    }

    fun importPackage(
        context: Context,
        uri: Uri,
        selectedIds: Set<Long>,
        nextId: Long,
        existingTitles: Set<String>
    ): List<QuickCardPackageCard> {
        require(selectedIds.isNotEmpty()) { "未选择需要导入的名片" }
        val selected = parseCards(readManifest(context, uri)).filter { it.id in selectedIds }
        require(selected.isNotEmpty()) { "名片包中没有选中的名片" }
        val requestedImages = selected
            .flatMap { listOf(it.portraitImageEntry, it.landscapeImageEntry) }
            .filter { it.isNotBlank() }
            .toSet()
        val targetDir = File(context.filesDir, "quick_cards").apply { mkdirs() }
        val extracted = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val safeName = normalizeEntryName(entry.name)
                    if (!entry.isDirectory && safeName in requestedImages) {
                        val extension = safeName.substringAfterLast('.', "png")
                            .lowercase(Locale.US)
                            .takeIf { it.length in 2..5 } ?: "png"
                        val output = File(targetDir, "imported_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
                        output.outputStream().buffered().use { out ->
                            copyLimited(zip, out, MaxImageBytes)
                        }
                        extracted[safeName] = output.absolutePath
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("无法打开名片包")

        var allocatedId = nextId.coerceAtLeast(1L)
        val usedTitles = existingTitles.toMutableSet()
        return selected.map { card ->
            val uniqueTitle = uniqueImportedTitle(card.title, usedTitles)
            usedTitles += uniqueTitle
            QuickCardPackageCard(
                id = allocatedId++,
                type = card.type,
                title = uniqueTitle,
                note = card.note,
                themeColor = card.themeColor,
                link = card.link,
                portraitImagePath = extracted[card.portraitImageEntry].orEmpty(),
                landscapeImagePath = extracted[card.landscapeImageEntry].orEmpty()
            )
        }
    }

    private data class StoredCard(
        val id: Long,
        val type: String,
        val title: String,
        val note: String,
        val themeColor: String,
        val link: String,
        val portraitImageEntry: String,
        val landscapeImageEntry: String
    )

    private fun readManifest(context: Context, uri: Uri): JSONObject {
        var payload: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use zipUse@ { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && normalizeEntryName(entry.name) == ManifestEntry) {
                        payload = readLimited(zip, MaxManifestBytes)
                        zip.closeEntry()
                        return@zipUse
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("无法打开名片包")
        val root = payload?.toString(Charsets.UTF_8)?.let(::JSONObject)
            ?: error("名片包缺少 manifest.json")
        require(root.optString("type") == PackageType) { "不是 KIGTTS 名片包" }
        require(root.optInt("version", 0) == PackageVersion) { "不支持的名片包版本" }
        return root
    }

    private fun parseCards(root: JSONObject): List<StoredCard> {
        val cards = root.optJSONArray("cards") ?: JSONArray()
        val usedIds = hashSetOf<Long>()
        return buildList {
            for (index in 0 until cards.length()) {
                val item = cards.optJSONObject(index) ?: continue
                val id = item.optLong("id", index.toLong() + 1L).coerceAtLeast(1L)
                if (!usedIds.add(id)) continue
                add(
                    StoredCard(
                        id = id,
                        type = item.optString("type", "text").takeIf { it in setOf("image", "qr", "text") }
                            ?: "text",
                        title = item.optString("title", "未命名名片").trim().ifBlank { "未命名名片" },
                        note = item.optString("note", ""),
                        themeColor = item.optString("themeColor", "#038387"),
                        link = item.optString("link", ""),
                        portraitImageEntry = normalizeOptionalImageEntry(item.optString("portraitImage", "")),
                        landscapeImageEntry = normalizeOptionalImageEntry(item.optString("landscapeImage", ""))
                    )
                )
            }
        }
    }

    private fun normalizeOptionalImageEntry(raw: String): String {
        if (raw.isBlank()) return ""
        val normalized = normalizeEntryName(raw)
        return normalized.takeIf { it.startsWith("images/") } ?: ""
    }

    private fun normalizeEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." }) {
            "名片包包含不安全路径"
        }
        return normalized
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyLimited(input, output, maxBytes)
        return output.toByteArray()
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "名片包内文件过大" }
            output.write(buffer, 0, read)
        }
    }

    private fun uniqueImportedTitle(raw: String, existing: Set<String>): String {
        val base = raw.trim().ifBlank { "未命名名片" }
        if (base !in existing) return base
        var index = 1
        while (true) {
            val suffix = if (index == 1) "（导入）" else "（导入$index）"
            val candidate = "$base$suffix"
            if (candidate !in existing) return candidate
            index += 1
        }
    }
}
