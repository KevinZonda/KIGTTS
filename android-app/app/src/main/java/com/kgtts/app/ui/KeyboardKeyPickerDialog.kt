package com.lhtstudio.kigtts.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.util.KeyboardKeyOption
import com.lhtstudio.kigtts.app.util.KeyboardHotkeys

private data class ModifierOption(val mask: Int, val label: String)

private val modifierOptions = listOf(
    ModifierOption(KeyEvent.META_CTRL_ON, "Ctrl"),
    ModifierOption(KeyEvent.META_ALT_ON, "Alt"),
    ModifierOption(KeyEvent.META_SHIFT_ON, "Shift"),
    ModifierOption(KeyEvent.META_META_ON, "Meta")
)

private val keyGroupOrder = listOf(
    "当前按键",
    "常用键",
    "字母",
    "数字",
    "功能键",
    "导航键",
    "符号",
    "小键盘"
)

@Composable
internal fun KeyboardKeyPickerDialog(
    initialKeyCode: Int,
    initialModifiers: Int,
    onDismiss: () -> Unit,
    onConfirm: (keyCode: Int, modifiers: Int) -> Unit
) {
    var selectedKeyCode by remember(initialKeyCode) { mutableIntStateOf(initialKeyCode) }
    var selectedModifiers by remember(initialModifiers) {
        mutableIntStateOf(KeyboardHotkeys.normalizeModifiers(initialModifiers))
    }
    val currentOption = remember(initialKeyCode) {
        if (
            initialKeyCode != KeyEvent.KEYCODE_UNKNOWN &&
            KeyboardHotkeys.keyOptions.none { it.keyCode == initialKeyCode }
        ) {
            KeyboardKeyOption(initialKeyCode, KeyboardHotkeys.labelOf(initialKeyCode, 0), "当前按键")
        } else {
            null
        }
    }
    val options = remember(currentOption) {
        if (currentOption == null) KeyboardHotkeys.keyOptions else listOf(currentOption) + KeyboardHotkeys.keyOptions
    }
    val groupedOptions = remember(options) {
        val grouped = options.groupBy { it.group }
        keyGroupOrder.mapNotNull { group -> grouped[group]?.let { group to it } }
    }
    val gridHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.46f).coerceIn(220.dp, 420.dp)

    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择键盘按键") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("组合键", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modifierOptions.forEach { option ->
                        KeyboardModifierButton(
                            option = option,
                            selected = selectedModifiers and option.mask != 0,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedModifiers = if (selectedModifiers and option.mask != 0) {
                                    selectedModifiers and option.mask.inv()
                                } else {
                                    selectedModifiers or option.mask
                                }
                            }
                        )
                    }
                }
                Text("按键", fontWeight = FontWeight.SemiBold)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier = Modifier.fillMaxWidth().height(gridHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedOptions.forEach { (group, groupOptions) ->
                        item(key = "header-$group", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = group,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(groupOptions, key = { "key-${it.keyCode}" }) { option ->
                            KeyboardKeyOptionTile(
                                option = option,
                                selected = selectedKeyCode == option.keyCode,
                                onClick = { selectedKeyCode = option.keyCode }
                            )
                        }
                    }
                }
                Text(
                    text = if (selectedKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
                        "请选择一个按键"
                    } else {
                        "当前：${KeyboardHotkeys.labelOf(selectedKeyCode, selectedModifiers)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Md2TextButton(
                enabled = selectedKeyCode != KeyEvent.KEYCODE_UNKNOWN,
                onClick = { onConfirm(selectedKeyCode, selectedModifiers) }
            ) { Text("应用") }
        },
        dismissButton = { Md2TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun KeyboardModifierButton(
    option: ModifierOption,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Md2Button(onClick = onClick, modifier = modifier.height(44.dp)) {
            Text(option.label, maxLines = 1)
        }
    } else {
        Md2OutlinedButton(onClick = onClick, modifier = modifier.height(44.dp)) {
            Text(option.label, maxLines = 1)
        }
    }
}

@Composable
private fun KeyboardKeyOptionTile(
    option: KeyboardKeyOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val hapticOnClick = rememberKigttsHapticClick(onClick)
    Card(
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = hapticOnClick),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            md2CardContainerColor()
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        elevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
