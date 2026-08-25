package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lhtstudio.kigtts.app.data.SoundboardItem
import kotlinx.coroutines.delay

internal data class SoundboardItemActionTarget(
    val groupId: Long,
    val itemId: Long
)

@Composable
internal fun SoundboardItemActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var rendered by remember { mutableStateOf(expanded) }
    val positionProvider = rememberSoundboardTopEndPopupPositionProvider(2.dp)
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_action_menu_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.92f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_action_menu_scale"
    )
    LaunchedEffect(expanded) {
        if (expanded) {
            rendered = true
        } else if (rendered) {
            delay(150L)
            rendered = false
        }
    }
    if (!rendered) return

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .padding(6.dp)
                .width(104.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            shape = Md2ControlShape,
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.MenuElevation
        ) {
            Row {
                KigttsIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(52.dp),
                    tooltip = "编辑音效"
                ) {
                    MsIcon("edit", contentDescription = "编辑音效")
                }
                KigttsIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(52.dp),
                    tooltip = "删除音效"
                ) {
                    MsIcon("delete", contentDescription = "删除音效")
                }
            }
        }
    }
}

@Composable
internal fun SoundboardItemEditDialog(
    item: SoundboardItem,
    onDismiss: () -> Unit,
    onSave: (title: String, wakeWord: String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var wakeWord by remember(item.id) { mutableStateOf(item.wakeWord) }
    var titleFocused by remember { mutableStateOf(false) }
    var wakeWordFocused by remember { mutableStateOf(false) }

    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑音效") },
        text = {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Md2DialogOutlinedField(
                    value = title,
                    onValueChange = { title = it },
                    label = "音效名称",
                    modifier = Modifier.onFocusChanged { titleFocused = it.isFocused },
                    singleLine = true,
                    topPadding = 0.dp,
                    trailingIcon = if (titleFocused && title.isNotEmpty()) {
                        { Md2ClearFieldButton { title = "" } }
                    } else {
                        null
                    }
                )
                Md2DialogOutlinedField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    label = "触发词",
                    modifier = Modifier.onFocusChanged { wakeWordFocused = it.isFocused },
                    singleLine = true,
                    topPadding = 0.dp,
                    trailingIcon = if (wakeWordFocused && wakeWord.isNotEmpty()) {
                        { Md2ClearFieldButton { wakeWord = "" } }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            Md2TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), wakeWord.trim()) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
internal fun SoundboardItemDeleteDialog(
    item: SoundboardItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    KigttsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除音效") },
        text = { Text("确定删除“${item.title.ifBlank { "未命名音效" }}”吗？") },
        confirmButton = {
            Md2TextButton(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun rememberSoundboardTopEndPopupPositionProvider(
    verticalMargin: Dp
): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, verticalMargin) {
        object : PopupPositionProvider {
            private val verticalMarginPx = with(density) { verticalMargin.roundToPx() }

            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val preferredX = when (layoutDirection) {
                    LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
                    LayoutDirection.Rtl -> anchorBounds.left
                }
                val x = preferredX.coerceIn(
                    minimumValue = 0,
                    maximumValue = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                )
                val aboveY = anchorBounds.top - verticalMarginPx - popupContentSize.height
                val insideTopY = anchorBounds.top + verticalMarginPx
                val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                return IntOffset(
                    x = x,
                    y = if (aboveY >= 0) aboveY else insideTopY.coerceIn(0, maxY)
                )
            }
        }
    }
}
