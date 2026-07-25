package com.lhtstudio.kigtts.app.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import kotlin.math.roundToInt

internal data class OpenTypeFontInfo(
    val familyName: String,
    val weightAxis: AppFontWeightAxis?
)

internal object OpenTypeFontParser {
    private const val SfntHeaderSize = 12L
    private const val TableRecordSize = 16L
    private const val NameRecordSize = 12L
    private const val MaxTables = 512
    private const val MaxNameBytes = 4096

    fun parse(file: File): OpenTypeFontInfo {
        if (!file.isFile || file.length() < SfntHeaderSize) {
            throw IOException("字体文件为空或不完整")
        }
        RandomAccessFile(file, "r").use { input ->
            val signature = input.readUnsignedInt()
            if (signature == tagValue("ttcf")) {
                throw IOException("暂不支持 TTC 字体集合，请导入单个 TTF 或 OTF 文件")
            }
            if (signature != 0x00010000L && signature != tagValue("OTTO") &&
                signature != tagValue("true") && signature != tagValue("typ1")
            ) {
                throw IOException("不是有效的 TTF 或 OTF 字体")
            }
            val tableCount = input.readUnsignedShort()
            if (tableCount !in 1..MaxTables) throw IOException("字体表数量异常")
            input.skipBytes(6)
            val tables = mutableMapOf<String, TableRange>()
            repeat(tableCount) {
                val tag = input.readTag()
                input.skipBytes(4)
                val offset = input.readUnsignedInt()
                val length = input.readUnsignedInt()
                if (offset <= file.length() && length <= file.length() - offset) {
                    tables[tag] = TableRange(offset, length)
                }
            }
            val familyName = tables["name"]?.let { parseFamilyName(input, it) }
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
            val weightAxis = tables["fvar"]?.let { parseWeightAxis(input, it) }
            return OpenTypeFontInfo(familyName = familyName, weightAxis = weightAxis)
        }
    }

    private fun parseFamilyName(input: RandomAccessFile, table: TableRange): String? {
        input.seek(table.offset)
        input.readUnsignedShort()
        val count = input.readUnsignedShort()
        val stringsOffset = input.readUnsignedShort()
        if (count !in 1..4096) return null
        val recordsStart = table.offset + 6L
        val stringsStart = table.offset + stringsOffset
        val records = ArrayList<NameRecord>(count)
        repeat(count) { index ->
            val recordOffset = recordsStart + index * NameRecordSize
            if (recordOffset + NameRecordSize > table.end) return@repeat
            input.seek(recordOffset)
            records += NameRecord(
                platformId = input.readUnsignedShort(),
                encodingId = input.readUnsignedShort(),
                languageId = input.readUnsignedShort(),
                nameId = input.readUnsignedShort(),
                length = input.readUnsignedShort(),
                offset = input.readUnsignedShort()
            )
        }
        return records
            .asSequence()
            .filter { it.nameId == 16 || it.nameId == 1 }
            .sortedWith(
                compareBy<NameRecord> { if (it.nameId == 16) 0 else 1 }
                    .thenBy { languagePriority(it.languageId) }
                    .thenBy { platformPriority(it.platformId) }
            )
            .mapNotNull { record ->
                if (record.length !in 1..MaxNameBytes) return@mapNotNull null
                val position = stringsStart + record.offset
                if (position < table.offset || position + record.length > table.end) {
                    return@mapNotNull null
                }
                input.seek(position)
                val bytes = ByteArray(record.length)
                input.readFully(bytes)
                decodeName(bytes, record.platformId)?.trim()?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    private fun parseWeightAxis(
        input: RandomAccessFile,
        table: TableRange
    ): AppFontWeightAxis? {
        if (table.length < 16L) return null
        input.seek(table.offset)
        input.readUnsignedShort()
        input.readUnsignedShort()
        val axesOffset = input.readUnsignedShort()
        input.readUnsignedShort()
        val axisCount = input.readUnsignedShort()
        val axisSize = input.readUnsignedShort()
        if (axisCount !in 1..64 || axisSize < 20) return null
        repeat(axisCount) { index ->
            val offset = table.offset + axesOffset + index.toLong() * axisSize
            if (offset + 20L > table.end) return@repeat
            input.seek(offset)
            val tag = input.readTag()
            val min = input.readFixed16_16()
            val default = input.readFixed16_16()
            val max = input.readFixed16_16()
            if (tag == "wght" && min.isFinite() && default.isFinite() && max.isFinite()) {
                val minWeight = min.roundToInt().coerceIn(AppFontDefaults.MinWeight, AppFontDefaults.MaxWeight)
                val maxWeight = max.roundToInt().coerceIn(minWeight, AppFontDefaults.MaxWeight)
                return AppFontWeightAxis(
                    min = minWeight,
                    default = default.roundToInt().coerceIn(minWeight, maxWeight),
                    max = maxWeight
                )
            }
        }
        return null
    }

    private fun decodeName(bytes: ByteArray, platformId: Int): String? = runCatching {
        val charset = when (platformId) {
            0, 3 -> Charsets.UTF_16BE
            1 -> Charset.forName("x-MacRoman")
            else -> Charsets.UTF_8
        }
        bytes.toString(charset).replace("\u0000", "")
    }.getOrNull()

    private fun languagePriority(languageId: Int): Int = when (languageId) {
        0x0804, 0x1004, 0x0004 -> 0
        0x0409, 0x0000 -> 1
        else -> 2
    }

    private fun platformPriority(platformId: Int): Int = when (platformId) {
        3 -> 0
        0 -> 1
        1 -> 2
        else -> 3
    }

    private fun RandomAccessFile.readUnsignedInt(): Long = readInt().toLong() and 0xFFFFFFFFL

    private fun RandomAccessFile.readTag(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return bytes.toString(Charsets.ISO_8859_1)
    }

    private fun RandomAccessFile.readFixed16_16(): Float = readInt() / 65536f

    private fun tagValue(tag: String): Long = tag.toByteArray(Charsets.ISO_8859_1)
        .fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFFL) }

    private data class TableRange(val offset: Long, val length: Long) {
        val end: Long get() = offset + length
    }

    private data class NameRecord(
        val platformId: Int,
        val encodingId: Int,
        val languageId: Int,
        val nameId: Int,
        val length: Int,
        val offset: Int
    )
}
