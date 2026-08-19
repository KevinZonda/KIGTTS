package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.QuickTextGestureBinding
import com.lhtstudio.kigtts.app.data.QuickTextGestureSettings
import com.lhtstudio.kigtts.app.data.QuickTextGestureTemplate
import com.lhtstudio.kigtts.app.data.QuickTextGestures

@Composable
internal fun QuickTextGestureEntryCard(
    settings: QuickTextGestureSettings,
    onMasterEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val activeCount = settings.activeBindings().size
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = onOpen
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MsIcon(
                    name = "gesture",
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.accentText
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("快捷文本手势", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            !settings.enabled -> "已关闭"
                            activeCount == 0 -> "尚未设置"
                            else -> "已启用 $activeCount 个手势"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MsIcon("chevron_right", contentDescription = "管理手势")
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("使用快捷文本手势", modifier = Modifier.weight(1f))
                Md2Switch(
                    checked = settings.enabled,
                    onCheckedChange = onMasterEnabledChange
                )
            }
            Text(
                "在大字幕区域画出已设置的手势，即可快速发送对应内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun QuickTextGestureSettingsScreen(
    viewModel: MainViewModel,
    state: UiState
) {
    var editingGestureId by remember { mutableStateOf<String?>(null) }
    val settings = state.quickTextGestureSettings

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = UiTokens.WideListMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = UiTokens.PageTopBlank,
                end = 16.dp,
                bottom = pageBottomBlankPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "gesture-master") {
                Md2SettingsCard(title = null) {
                    Md2SettingSwitchRow(
                        title = "启用快捷文本手势",
                        icon = "gesture",
                        checked = settings.enabled,
                        onCheckedChange = viewModel::setQuickTextGestureMasterEnabled,
                        supportingText =
                            "在大字幕区域一笔画出手势，即可发送对应内容；点按预览和长按复制不受影响。"
                    )
                }
            }
            items(QuickTextGestures.templates, key = QuickTextGestureTemplate::id) { template ->
                val binding = settings.binding(template.id) ?: QuickTextGestureBinding(template.id)
                QuickTextGestureListItem(
                    template = template,
                    binding = binding,
                    onClick = { editingGestureId = template.id },
                    onEnabledChange = { enabled ->
                        if (enabled && binding.text.isBlank()) {
                            editingGestureId = template.id
                        } else {
                            viewModel.setQuickTextGestureBindingEnabled(template.id, enabled)
                        }
                    }
                )
            }
        }
    }

    val editingTemplate = editingGestureId?.let(QuickTextGestures::template)
    val editingBinding = editingGestureId?.let(settings::binding)
    if (editingTemplate != null && editingBinding != null) {
        QuickTextGestureEditDialog(
            template = editingTemplate,
            binding = editingBinding,
            onDismiss = { editingGestureId = null },
            onConfirm = { enabled, text ->
                viewModel.updateQuickTextGestureBinding(editingTemplate.id, enabled, text)
                editingGestureId = null
            }
        )
    }
}

@Composable
private fun QuickTextGestureListItem(
    template: QuickTextGestureTemplate,
    binding: QuickTextGestureBinding,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickTextGesturePreview(
                template = template,
                animated = false,
                modifier = Modifier.size(width = 68.dp, height = 56.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(template.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = binding.text.ifBlank { "点按设置内容" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Md2Switch(checked = binding.enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun QuickTextGestureEditDialog(
    template: QuickTextGestureTemplate,
    binding: QuickTextGestureBinding,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String) -> Unit
) {
    var text by remember(binding) { mutableStateOf(binding.text) }
    var enabled by remember(binding) { mutableStateOf(binding.enabled) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置${template.title}手势") },
        dismissButton = { Md2TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Md2TextButton(
                onClick = {
                    if (enabled && text.isBlank()) {
                        validationMessage = "请先填写要发送的内容"
                    } else {
                        onConfirm(enabled, text)
                    }
                }
            ) { Text("保存") }
        },
        contentSpacing = 10.dp
    ) {
        QuickTextGesturePreview(
            template = template,
            animated = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        )
        Text(template.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Md2SettingSwitchRow(
            title = "使用这个手势",
            checked = enabled,
            onCheckedChange = { enabled = it }
        )
        Md2DialogOutlinedField(
            value = text,
            onValueChange = {
                text = it.take(QuickTextGestureSettings.MAX_TEXT_LENGTH)
                validationMessage = null
            },
            label = "发送内容",
            singleLine = false,
            maxLines = 5,
            topPadding = 0.dp,
            trailingIcon = if (text.isNotEmpty()) {
                { Md2ClearFieldButton { text = "" } }
            } else {
                null
            }
        )
        validationMessage?.let { message ->
            Text(message, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
        }
        Spacer(Modifier.height(2.dp))
    }
}
