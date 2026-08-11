package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhtstudio.kigtts.app.data.ListeningModeSettings
import com.lhtstudio.kigtts.app.overlay.ListeningCaptionItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ListeningCaptionPreviewDialog(
    items: List<ListeningCaptionItem>,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeChangeFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(UiTokens.Radius),
                backgroundColor = md2CardContainerColor(),
                elevation = UiTokens.MenuElevation
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .quickSubtitlePinchZoom(
                            enabled = true,
                            fontSizeSp = fontSizeSp,
                            minFontSizeSp = ListeningModeSettings.MIN_FONT_SIZE_SP,
                            maxFontSizeSp = ListeningModeSettings.MAX_FONT_SIZE_SP,
                            onFontSizeChange = onFontSizeChange,
                            onFontSizeChangeFinished = onFontSizeChangeFinished
                        )
                        .clickable(onClick = onDismiss)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(items) { _, item ->
                        Text(
                            text = item.text,
                            modifier = Modifier.combinedClickable(
                                onClick = onDismiss,
                                onLongClick = {
                                    clipboard.setText(AnnotatedString(item.text))
                                    toast(context, "已复制")
                                }
                            ),
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.18f).sp,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
            }
            Md2IconButton(
                icon = "close",
                contentDescription = "关闭预览",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
