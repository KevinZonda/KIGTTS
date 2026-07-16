package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class OpenTypeFontParserTest {
    @Test
    fun parsesTypographicFamilyAndVariableWeightAxis() {
        val file = Files.createTempFile("font-parser", ".ttf").toFile()
        file.writeBytes(buildTestFont("测试可变字体"))

        val result = OpenTypeFontParser.parse(file)

        assertEquals("测试可变字体", result.familyName)
        assertEquals(AppFontWeightAxis(40, 400, 900), result.weightAxis)
    }

    @Test
    fun rejectsFontCollections() {
        val file = Files.createTempFile("font-collection", ".ttc").toFile()
        file.writeBytes(byteArrayOf('t'.code.toByte(), 't'.code.toByte(), 'c'.code.toByte(), 'f'.code.toByte()))

        assertThrows(IOException::class.java) { OpenTypeFontParser.parse(file) }
    }

    @Test
    fun catalogDefaultIsClampedToTheRealVariableAxis() {
        val axis = AppFontWeightAxis(min = 200, default = 200, max = 900)

        assertEquals(AppFontWeightAxis(200, 400, 900), axis.withDefault(400))
        assertEquals(AppFontWeightAxis(200, 900, 900), axis.withDefault(1_000))
    }

    private fun buildTestFont(familyName: String): ByteArray {
        val familyBytes = familyName.toByteArray(Charsets.UTF_16BE)
        val nameTable = ByteBuffer.allocate(18 + familyBytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0)
            putShort(1)
            putShort(18)
            putShort(3)
            putShort(1)
            putShort(0x0804.toShort())
            putShort(16)
            putShort(familyBytes.size.toShort())
            putShort(0)
            put(familyBytes)
        }.array()
        val fvarTable = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(1)
            putShort(0)
            putShort(16)
            putShort(2)
            putShort(1)
            putShort(20)
            putShort(0)
            putShort(0)
            put("wght".toByteArray(Charsets.ISO_8859_1))
            putInt(fixed(40))
            putInt(fixed(400))
            putInt(fixed(900))
            putShort(0)
            putShort(256)
        }.array()
        val nameOffset = 44
        val fvarOffset = nameOffset + nameTable.size
        return ByteBuffer.allocate(fvarOffset + fvarTable.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000)
            putShort(2)
            putShort(0)
            putShort(0)
            putShort(0)
            putTableRecord("name", nameOffset, nameTable.size)
            putTableRecord("fvar", fvarOffset, fvarTable.size)
            put(nameTable)
            put(fvarTable)
        }.array()
    }

    private fun ByteBuffer.putTableRecord(tag: String, offset: Int, length: Int) {
        put(tag.toByteArray(Charsets.ISO_8859_1))
        putInt(0)
        putInt(offset)
        putInt(length)
    }

    private fun fixed(value: Int): Int = value shl 16
}
