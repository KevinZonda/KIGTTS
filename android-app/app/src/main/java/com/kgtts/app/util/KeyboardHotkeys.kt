package com.lhtstudio.kigtts.app.util

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

data class KeyboardHotkeyEntry(
    val id: Long,
    val keyCode: Int,
    val modifiers: Int,
    val text: String,
    val enabled: Boolean = true
)

data class KeyboardKeyOption(
    val keyCode: Int,
    val label: String,
    val group: String
)

object KeyboardHotkeys {
    private const val MODIFIER_MASK =
        KeyEvent.META_SHIFT_ON or
            KeyEvent.META_CTRL_ON or
            KeyEvent.META_ALT_ON or
            KeyEvent.META_META_ON

    private val modifierKeys = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT,
        KeyEvent.KEYCODE_CAPS_LOCK,
        KeyEvent.KEYCODE_NUM_LOCK,
        KeyEvent.KEYCODE_SCROLL_LOCK
    )

    private val reservedKeys = setOf(
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE
    )

    val keyOptions: List<KeyboardKeyOption> = buildList {
        for (index in 0..25) {
            val keyCode = KeyEvent.KEYCODE_A + index
            add(KeyboardKeyOption(keyCode, ('A'.code + index).toChar().toString(), "字母"))
        }
        for (index in 0..9) {
            add(KeyboardKeyOption(KeyEvent.KEYCODE_0 + index, index.toString(), "数字"))
        }
        for (index in 0..11) {
            add(KeyboardKeyOption(KeyEvent.KEYCODE_F1 + index, "F${index + 1}", "功能键"))
        }
        addAll(
            listOf(
                KeyboardKeyOption(KeyEvent.KEYCODE_ENTER, "Enter", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_SPACE, "Space", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_TAB, "Tab", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_ESCAPE, "Esc", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_DEL, "Backspace", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_FORWARD_DEL, "Delete", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_INSERT, "Insert", "常用键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_MOVE_HOME, "Home", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_MOVE_END, "End", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_PAGE_UP, "Page Up", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_PAGE_DOWN, "Page Down", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_DPAD_UP, "Up", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_DPAD_DOWN, "Down", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_DPAD_LEFT, "Left", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_DPAD_RIGHT, "Right", "导航键"),
                KeyboardKeyOption(KeyEvent.KEYCODE_COMMA, ",", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_PERIOD, ".", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_SLASH, "/", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_SEMICOLON, ";", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_APOSTROPHE, "'", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_LEFT_BRACKET, "[", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_RIGHT_BRACKET, "]", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_BACKSLASH, "\\", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_MINUS, "-", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_EQUALS, "=", "符号"),
                KeyboardKeyOption(KeyEvent.KEYCODE_GRAVE, "`", "符号")
            )
        )
        for (index in 0..9) {
            add(KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_0 + index, "Num $index", "小键盘"))
        }
        addAll(
            listOf(
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_ADD, "Num +", "小键盘"),
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, "Num -", "小键盘"),
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, "Num *", "小键盘"),
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_DIVIDE, "Num /", "小键盘"),
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_DOT, "Num .", "小键盘"),
                KeyboardKeyOption(KeyEvent.KEYCODE_NUMPAD_ENTER, "Num Enter", "小键盘")
            )
        )
    }

    fun normalizeModifiers(metaState: Int): Int {
        var normalized = metaState and MODIFIER_MASK
        if (metaState and (KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON) != 0) {
            normalized = normalized or KeyEvent.META_SHIFT_ON
        }
        if (metaState and (KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON) != 0) {
            normalized = normalized or KeyEvent.META_CTRL_ON
        }
        if (metaState and (KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON) != 0) {
            normalized = normalized or KeyEvent.META_ALT_ON
        }
        if (metaState and (KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_RIGHT_ON) != 0) {
            normalized = normalized or KeyEvent.META_META_ON
        }
        return normalized
    }

    fun isPhysicalKeyboardEvent(event: KeyEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) return false
        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
        return event.device?.isVirtual == false
    }

    fun isAssignable(event: KeyEvent): Boolean =
        isPhysicalKeyboardEvent(event) &&
            event.keyCode !in modifierKeys &&
            event.keyCode !in reservedKeys &&
            event.keyCode != KeyEvent.KEYCODE_UNKNOWN

    fun findMatch(entries: List<KeyboardHotkeyEntry>, event: KeyEvent): KeyboardHotkeyEntry? {
        if (!isAssignable(event)) return null
        val modifiers = normalizeModifiers(event.metaState)
        return entries.firstOrNull { entry ->
            entry.enabled &&
                entry.text.isNotBlank() &&
                entry.keyCode == event.keyCode &&
                entry.modifiers == modifiers
        }
    }

    fun labelOf(keyCode: Int, modifiers: Int): String {
        val parts = buildList {
            if (modifiers and KeyEvent.META_CTRL_ON != 0) add("Ctrl")
            if (modifiers and KeyEvent.META_ALT_ON != 0) add("Alt")
            if (modifiers and KeyEvent.META_SHIFT_ON != 0) add("Shift")
            if (modifiers and KeyEvent.META_META_ON != 0) add("Meta")
            add(keyName(keyCode))
        }
        return parts.joinToString(" + ")
    }

    fun encode(entries: List<KeyboardHotkeyEntry>): String =
        JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("keyCode", entry.keyCode)
                        put("modifiers", normalizeModifiers(entry.modifiers))
                        put("text", entry.text)
                        put("enabled", entry.enabled)
                    }
                )
            }
        }.toString()

    fun decode(raw: String?): List<KeyboardHotkeyEntry> = runCatching {
        val array = JSONArray(raw.orEmpty().ifBlank { "[]" })
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val id = item.optLong("id", Long.MIN_VALUE)
                val keyCode = item.optInt("keyCode", KeyEvent.KEYCODE_UNKNOWN)
                val text = item.optString("text", "").trim()
                if (id == Long.MIN_VALUE || keyCode == KeyEvent.KEYCODE_UNKNOWN || text.isBlank()) {
                    return@repeat
                }
                add(
                    KeyboardHotkeyEntry(
                        id = id,
                        keyCode = keyCode,
                        modifiers = normalizeModifiers(item.optInt("modifiers", 0)),
                        text = text,
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun keyName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        else -> KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.titlecase() }
    }
}
