package com.lhtstudio.kigtts.app.util

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardHotkeysTest {
    @Test
    fun encodeDecode_preservesOrderAndFields() {
        val entries = listOf(
            KeyboardHotkeyEntry(
                id = 11L,
                keyCode = KeyEvent.KEYCODE_K,
                modifiers = KeyEvent.META_CTRL_LEFT_ON,
                text = "你好呀~",
                enabled = true
            ),
            KeyboardHotkeyEntry(
                id = 12L,
                keyCode = KeyEvent.KEYCODE_F2,
                modifiers = KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON,
                text = "稍等一下",
                enabled = false
            )
        )

        val decoded = KeyboardHotkeys.decode(KeyboardHotkeys.encode(entries))

        assertEquals(listOf(11L, 12L), decoded.map { it.id })
        assertEquals(KeyEvent.META_CTRL_ON, decoded[0].modifiers)
        assertEquals("你好呀~", decoded[0].text)
        assertTrue(decoded[0].enabled)
        assertEquals(KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON, decoded[1].modifiers)
        assertEquals(false, decoded[1].enabled)
    }

    @Test
    fun decode_skipsInvalidOrBlankEntries() {
        val decoded = KeyboardHotkeys.decode(
            """[
                {"id":1,"keyCode":0,"text":"无效"},
                {"id":2,"keyCode":29,"text":"   "},
                {"id":3,"keyCode":30,"text":"有效","enabled":true}
            ]""".trimIndent()
        )

        assertEquals(listOf(3L), decoded.map { it.id })
        assertEquals("有效", decoded.single().text)
    }

    @Test
    fun decode_corruptPayloadFallsBackToEmptyList() {
        assertTrue(KeyboardHotkeys.decode("not-json").isEmpty())
    }

    @Test
    fun keyOptions_areUniqueAndContainCommonHardwareKeys() {
        val options = KeyboardHotkeys.keyOptions
        val keyCodes = options.map { it.keyCode }

        assertEquals(keyCodes.size, keyCodes.distinct().size)
        assertTrue(KeyEvent.KEYCODE_A in keyCodes)
        assertTrue(KeyEvent.KEYCODE_0 in keyCodes)
        assertTrue(KeyEvent.KEYCODE_F12 in keyCodes)
        assertTrue(KeyEvent.KEYCODE_ENTER in keyCodes)
        assertTrue(KeyEvent.KEYCODE_DPAD_LEFT in keyCodes)
        assertTrue(KeyEvent.KEYCODE_NUMPAD_ENTER in keyCodes)
        assertTrue(options.map { it.group }.containsAll(listOf("字母", "数字", "功能键", "常用键", "导航键", "符号", "小键盘")))
    }
}
