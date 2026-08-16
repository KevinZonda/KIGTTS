package com.lhtstudio.kigtts.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.util.KeyboardHotkeyEntry
import com.lhtstudio.kigtts.app.util.KeyboardHotkeys
import kotlin.math.max

@Composable
internal fun KeyboardHotkeyEditorDialog(
    entry: KeyboardHotkeyEntry?,
    existingEntries: List<KeyboardHotkeyEntry>,
    onDismiss: () -> Unit,
    onSave: (KeyboardHotkeyEntry) -> Unit
) {
    var keyCode by remember(entry) { mutableStateOf(entry?.keyCode ?: KeyEvent.KEYCODE_UNKNOWN) }
    var modifiers by remember(entry) { mutableStateOf(entry?.modifiers ?: 0) }
    var text by remember(entry) { mutableStateOf(entry?.text.orEmpty()) }
    var enabled by remember(entry) { mutableStateOf(entry?.enabled ?: true) }
    var keyPickerOpen by remember(entry) { mutableStateOf(false) }

    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "添加键盘热键" else "编辑键盘热键") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("按键", fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(UiTokens.Radius)
                        )
                        .clickable { keyPickerOpen = true }
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                                "从列表选择按键"
                            } else {
                                KeyboardHotkeys.labelOf(keyCode, modifiers)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MsIcon("arrow_drop_down", contentDescription = null)
                    }
                }
                Md2DialogOutlinedField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    label = "发送内容",
                    singleLine = false,
                    maxLines = 5,
                    topPadding = 4.dp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用这个键盘热键", modifier = Modifier.weight(1f))
                    Md2Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(
                    "仅支持实体键盘；部分系统快捷键可能无法使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Md2TextButton(
                enabled = keyCode != KeyEvent.KEYCODE_UNKNOWN && text.isNotBlank(),
                onClick = {
                    val nextId = entry?.id ?: max(
                        System.currentTimeMillis(),
                        (existingEntries.maxOfOrNull { it.id } ?: 0L) + 1L
                    )
                    onSave(
                        KeyboardHotkeyEntry(
                            id = nextId,
                            keyCode = keyCode,
                            modifiers = modifiers,
                            text = text.trim(),
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { Md2TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (keyPickerOpen) {
        KeyboardKeyPickerDialog(
            initialKeyCode = keyCode,
            initialModifiers = modifiers,
            onDismiss = { keyPickerOpen = false },
            onConfirm = { selectedKeyCode, selectedModifiers ->
                keyCode = selectedKeyCode
                modifiers = selectedModifiers
                keyPickerOpen = false
            }
        )
    }
}
