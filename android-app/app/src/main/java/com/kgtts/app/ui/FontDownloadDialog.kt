package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.AppFontRemoteSource
import com.lhtstudio.kigtts.app.data.InstalledAppFont
import com.lhtstudio.kigtts.app.data.RemoteAppFont

@Composable
internal fun FontDownloadDialog(
    state: FontSettingsUiState,
    installedFonts: Map<String, InstalledAppFont>,
    onSelectSource: (AppFontRemoteSource) -> Unit,
    onInstall: (RemoteAppFont) -> Unit,
    onDismiss: () -> Unit
) {
    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载字体") },
        contentSpacing = 10.dp,
        content = {
            Text(
                "默认使用魔搭源。字体文件会保存在软件私有目录，并同时保存许可证。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppFontRemoteSource.entries.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.catalogSource == source,
                        onClick = { onSelectSource(source) }
                    )
                    Text(source.displayName)
                }
            }
            if (state.catalogLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "正在从 ${state.catalogSource.displayName} 获取字体列表",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state.catalog.isEmpty()) {
                Text(
                    "暂时没有可用字体，请检查网络或切换下载源。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.catalog.forEachIndexed { index, font ->
                    RemoteFontDownloadRow(
                        font = font,
                        installedFont = installedFonts[font.id],
                        installing = state.installingFontId == font.id,
                        progress = if (state.installingFontId == font.id) state.installProgress else null,
                        enabled = state.installingFontId == null,
                        onInstall = { onInstall(font) }
                    )
                    if (index < state.catalog.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                    }
                }
            }
        },
        confirmButton = {
            Md2TextButton(onClick = onDismiss, enabled = state.installingFontId == null) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun RemoteFontDownloadRow(
    font: RemoteAppFont,
    installedFont: InstalledAppFont?,
    installing: Boolean,
    progress: com.lhtstudio.kigtts.app.data.AppFontInstallProgress?,
    enabled: Boolean,
    onInstall: () -> Unit
) {
    val installed = installedFont != null
    val updateAvailable = installedFont?.let { local ->
        local.version != font.version ||
            (font.weightAxis != null && local.weightAxis == null) ||
            font.weightFiles.map { it.weight }.any { it !in local.availableWeights }
    } == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(font.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(font.licenseName)
                        if (font.downloadSizeBytes > 0L) {
                            append(" · ${formatFontSize(font.downloadSizeBytes)}")
                        }
                        font.weightAxis?.let { append(" · 可变 ${it.min}-${it.max}") }
                        if (font.weightAxis == null && font.weightFiles.size > 1) {
                            append(" · ${font.weightFiles.size} 档字重")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Md2TextButton(
                onClick = onInstall,
                enabled = enabled && (!installed || updateAvailable)
            ) {
                MsIcon(
                    when {
                        updateAvailable -> "update"
                        installed -> "check"
                        else -> "download"
                    },
                    contentDescription = null,
                    iconSize = 18.dp
                )
                Text(
                    when {
                        updateAvailable -> "更新"
                        installed -> "已安装"
                        else -> "安装"
                    }
                )
            }
        }
        if (font.description.isNotBlank()) {
            Text(
                font.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (installing) {
            val fraction = progress?.fraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(progress?.stage ?: "正在安装", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatFontSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024f)
}
