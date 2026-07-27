package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun QuickSubtitleModeSelectionDialog(
    selectedCompact: Boolean,
    onSelectedCompactChange: (Boolean) -> Unit,
    onPostpone: () -> Unit,
    onStartGuide: () -> Unit
) {
    Md2ScrollableDialog(
        onDismissRequest = onPostpone,
        title = { Text("选择快捷文本布局") },
        dismissButton = {
            TextButton(onClick = onPostpone) { Text("稍后") }
        },
        confirmButton = {
            TextButton(onClick = onStartGuide) { Text("开始引导") }
        },
        contentSpacing = 12.dp,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Text(
            text = "选择更适合当前设备和使用习惯的布局，之后仍可在设置中修改。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        QuickSubtitleModeChoice(
            title = "常规模式",
            description = "分组栏和编辑入口始终显示，信息更完整。",
            compact = false,
            selected = !selectedCompact,
            onClick = { onSelectedCompactChange(false) }
        )
        QuickSubtitleModeChoice(
            title = "紧凑模式",
            description = "压缩快捷文本区域，适合小屏幕或希望扩大字幕区域。",
            compact = true,
            selected = selectedCompact,
            onClick = { onSelectedCompactChange(true) }
        )
    }
}

@Composable
private fun QuickSubtitleModeChoice(
    title: String,
    description: String,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(UiTokens.Radius),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.subtitle1)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            QuickSubtitleModePreview(compact = compact)
        }
    }
}

@Composable
private fun QuickSubtitleModePreview(compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 82.dp else 96.dp)
            .clip(RoundedCornerShape(UiTokens.Radius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(if (compact) 3 else 2) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("文本", style = MaterialTheme.typography.caption)
                    }
                }
            }
            if (compact) {
                Column(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    MsIcon("keyboard_arrow_up", contentDescription = null, iconSize = 16.dp)
                    MsIcon("emoji_emotions", contentDescription = null, iconSize = 18.dp)
                    MsIcon("keyboard_arrow_down", contentDescription = null, iconSize = 16.dp)
                }
            }
        }
        if (!compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ModePreviewTab("emoji_emotions", "通用", selected = true, Modifier.weight(1f))
                ModePreviewTab("person_add", "扩列", selected = false, Modifier.weight(1f))
                ModePreviewTab("photo_camera", "拍照", selected = false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModePreviewTab(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MsIcon(icon, contentDescription = null, iconSize = 14.dp)
        Spacer(Modifier.size(3.dp))
        Text(label, style = MaterialTheme.typography.caption)
    }
}
