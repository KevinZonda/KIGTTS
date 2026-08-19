package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.util.KeyboardHotkeyEntry

internal data class KeyboardHotkeyTopBarActions(
    val selectionMode: Boolean,
    val canDelete: Boolean,
    val onAdd: () -> Unit,
    val onDelete: () -> Unit,
    val onClose: () -> Unit
)

@Composable
internal fun KeyboardHotkeyEntryCard(
    entries: List<KeyboardHotkeyEntry>,
    masterEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val enabledCount = entries.count { it.enabled }
    val hapticOnOpen = rememberKigttsHapticClick(onOpen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = hapticOnOpen)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MsIcon("keyboard", contentDescription = null, tint = MaterialTheme.colorScheme.accentText)
                Column(modifier = Modifier.weight(1f)) {
                Text("键盘热键", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            !masterEnabled -> "已关闭"
                    entries.isEmpty() -> "尚未设置"
                    else -> "已启用 $enabledCount / ${entries.size} 项"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            MsIcon("chevron_right", contentDescription = "管理键盘热键")
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("启用键盘热键", modifier = Modifier.weight(1f))
                Md2Switch(
                    checked = masterEnabled,
                    onCheckedChange = onMasterEnabledChange
                )
            }
            Text(
                "连接实体键盘后，按下设定的按键即可发送常用内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun KeyboardHotkeySettingsScreen(
    viewModel: MainViewModel,
    state: UiState,
    onTopBarActionsChange: (KeyboardHotkeyTopBarActions?) -> Unit
) {
    val context = LocalContext.current
    val entries = state.keyboardHotkeys
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<KeyboardHotkeyEntry?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>?>(null) }

    LaunchedEffect(entries) {
        val validIds = entries.mapTo(hashSetOf()) { it.id }
        selectedIds = selectedIds.filterTo(hashSetOf()) { it in validIds }
        if (selectionMode && entries.isEmpty()) selectionMode = false
    }

    LaunchedEffect(selectionMode, selectedIds, entries) {
        onTopBarActionsChange(
            KeyboardHotkeyTopBarActions(
                selectionMode = selectionMode,
                canDelete = selectedIds.isNotEmpty(),
                onAdd = {
                    editingEntry = null
                    editorOpen = true
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) pendingDeleteIds = selectedIds
                },
                onClose = {
                    selectionMode = false
                    selectedIds = emptySet()
                }
            )
        )
    }
    DisposableEffect(Unit) {
        onDispose { onTopBarActionsChange(null) }
    }

    CenteredPageColumn(maxWidth = UiTokens.WideListMaxWidth, contentSpacing = 0.dp) {
        Spacer(Modifier.height(UiTokens.PageTopBlank))
        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
            Md2SettingsCard(title = null) {
                Md2SettingSwitchRow(
                    title = "启用键盘热键",
                    icon = "keyboard",
                    checked = state.keyboardHotkeysEnabled,
                    onCheckedChange = viewModel::setKeyboardHotkeysMasterEnabled,
                    supportingText = "关闭后保留已设置的按键和内容，但不会再触发。"
                )
            }
        }
        if (selectionMode) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                shape = RoundedCornerShape(UiTokens.Radius),
                backgroundColor = md2CardContainerColor(),
                elevation = UiTokens.CardElevation
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "已选择 ${selectedIds.size} 项。调整其中一项的开关，会同步应用到所选内容。"
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有添加键盘热键", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            KeyboardHotkeyRecyclerList(
                modifier = Modifier.fillMaxWidth().weight(1f),
                entries = entries,
                bottomBlankHeight = pageBottomBlankPadding(),
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onEdit = {
                    editingEntry = it
                    editorOpen = true
                },
                onEnterSelection = {
                    selectionMode = true
                    selectedIds = selectedIds + it
                },
                onToggleSelection = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onToggleEnabled = { entry ->
                    val enabled = !entry.enabled
                    if (selectionMode && entry.id in selectedIds) {
                        viewModel.setKeyboardHotkeysEnabled(selectedIds, enabled)
                    } else {
                        viewModel.saveKeyboardHotkey(entry.copy(enabled = enabled))
                    }
                },
                onReorder = viewModel::reorderKeyboardHotkeys
            )
        }
    }

    if (editorOpen) {
        KeyboardHotkeyEditorDialog(
            entry = editingEntry,
            existingEntries = entries,
            onDismiss = { editorOpen = false },
            onSave = { entry ->
                val duplicate = entries.any {
                    it.id != entry.id && it.keyCode == entry.keyCode && it.modifiers == entry.modifiers
                }
                if (duplicate) {
                        toast(context, "这个按键已经设置过了")
                } else {
                    viewModel.saveKeyboardHotkey(entry)
                    editorOpen = false
                }
            }
        )
    }

    pendingDeleteIds?.let { ids ->
        KigttsAlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text("删除键盘热键") },
            text = { Text(if (ids.size > 1) "确定删除选中的 ${ids.size} 项？" else "确定删除这个键盘热键？") },
            confirmButton = {
                Md2TextButton(onClick = {
                    viewModel.deleteKeyboardHotkeys(ids)
                    selectedIds = emptySet()
                    selectionMode = false
                    pendingDeleteIds = null
                }) { Text("删除") }
            },
            dismissButton = {
                Md2TextButton(onClick = { pendingDeleteIds = null }) { Text("取消") }
            }
        )
    }
}
