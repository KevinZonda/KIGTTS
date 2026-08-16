package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.AppFontRemoteSource

@Composable
internal fun FontDownloadSourceDialog(
    modelScopeUrl: String,
    huggingFaceUrl: String,
    preferredSource: AppFontRemoteSource,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AppFontRemoteSource) -> Unit
) {
    var modelScope by remember(modelScopeUrl) { mutableStateOf(modelScopeUrl) }
    var huggingFace by remember(huggingFaceUrl) { mutableStateOf(huggingFaceUrl) }
    var preferred by remember(preferredSource) { mutableStateOf(preferredSource) }
    val modelScopeValid = AppFontRemoteSource.ModelScope.isValidRepositoryBaseUrl(modelScope)
    val huggingFaceValid = AppFontRemoteSource.HuggingFace.isValidRepositoryBaseUrl(huggingFace)
    val valuesValid = modelScopeValid && huggingFaceValid

    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text("字体下载源") },
        contentSpacing = 12.dp,
        content = {
            Text(
                "填写包含 font_manifest.json 及字体资源的仓库根地址。保存后会立即从首选源刷新字体列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Md2OutlinedField(
                value = modelScope,
                onValueChange = { modelScope = it },
                label = "魔搭仓库根地址",
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Md2IconButton(
                        icon = "restart_alt",
                        contentDescription = "恢复默认魔搭下载源",
                        onClick = {
                            modelScope = AppFontRemoteSource.ModelScope.defaultRepositoryBaseUrl
                        }
                    )
                }
            )
            Md2OutlinedField(
                value = huggingFace,
                onValueChange = { huggingFace = it },
                label = "Hugging Face 仓库根地址",
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Md2IconButton(
                        icon = "restart_alt",
                        contentDescription = "恢复默认 Hugging Face 下载源",
                        onClick = {
                            huggingFace = AppFontRemoteSource.HuggingFace.defaultRepositoryBaseUrl
                        }
                    )
                }
            )
            if (!valuesValid) {
                Text(
                    "请输入以 http:// 或 https:// 开头的仓库地址，不要附带“?”后的额外内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colors.error
                )
            }
            Text("优先下载源", style = MaterialTheme.typography.bodySmall)
            AppFontRemoteSource.entries.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UiTokens.Radius))
                        .clickable { preferred = source }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RadioButton(
                        selected = preferred == source,
                        onClick = { preferred = source }
                    )
                    Text(source.displayName)
                }
            }
        },
        confirmButton = {
            Md2TextButton(
                onClick = { onConfirm(modelScope, huggingFace, preferred) },
                enabled = valuesValid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
