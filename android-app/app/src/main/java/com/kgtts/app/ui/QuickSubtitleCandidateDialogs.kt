package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal data class QuickSubtitleCandidateEditTarget(
    val groupId: Long,
    val displayIndex: Int,
    val text: String,
    val colorArgb: Int?
)

internal data class QuickSubtitleCandidateDeleteTarget(
    val groupId: Long,
    val displayIndex: Int,
    val text: String
)

@Composable
internal fun QuickSubtitleCandidateEditDialog(
    target: QuickSubtitleCandidateEditTarget,
    onDismissRequest: () -> Unit,
    onSave: (String, Int?) -> Unit
) {
    var text by remember(target) { mutableStateOf(target.text) }
    var colorArgb by remember(target) { mutableStateOf(target.colorArgb) }
    var textFocused by remember(target) { mutableStateOf(false) }
    var showColorPicker by remember(target) { mutableStateOf(false) }

    KigttsDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(UiTokens.Radius),
                color = md2CardContainerColor(),
                elevation = UiTokens.CardElevation
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "编辑快捷文本",
                        style = MaterialTheme.typography.h6,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Md2DialogOutlinedField(
                        value = text,
                        onValueChange = { text = it },
                        label = "快捷文本",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .onFocusChanged { textFocused = it.isFocused },
                        singleLine = false,
                        maxLines = 4,
                        trailingIcon = if (textFocused && text.isNotEmpty()) {
                            { Md2ClearFieldButton { text = "" } }
                        } else {
                            null
                        }
                    )
                    QuickSubtitleItemColorEditRow(
                        colorArgb = colorArgb,
                        onClick = { showColorPicker = true }
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Md2TextButton(onClick = onDismissRequest) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(8.dp))
                        Md2TextButton(
                            enabled = text.trim().isNotEmpty(),
                            onClick = { onSave(text.trim(), colorArgb) }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ThemeColorPickerDialog(
            title = "快捷文本颜色",
            initialColor = colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary,
            colorLabel = "候选颜色",
            clearOptionLabel = "清除条目颜色",
            onClear = {
                colorArgb = null
                showColorPicker = false
            },
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                colorArgb = color.toArgb()
                showColorPicker = false
            }
        )
    }
}
