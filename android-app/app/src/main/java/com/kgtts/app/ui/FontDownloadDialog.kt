package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.InstalledAppFont
import com.lhtstudio.kigtts.app.data.RemoteAppFont

internal data class FontDownloadCatalogTab(val label: String, val icon: String)

@Composable
internal fun FontDownloadDialog(
    state: FontSettingsUiState,
    installedFonts: Map<String, InstalledAppFont>,
    onOpenSources: () -> Unit,
    sourceActionLabel: String = "管理下载源",
    catalogTabs: List<FontDownloadCatalogTab> = emptyList(),
    selectedCatalogTab: Int = 0,
    onCatalogTabSelected: (Int) -> Unit = {},
    showInstalledClockFonts: Boolean? = null,
    onShowInstalledClockFontsChange: (Boolean) -> Unit = {},
    onInstall: (RemoteAppFont) -> Unit,
    onDismiss: () -> Unit
) {
    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载字体") },
        contentSpacing = 10.dp,
        content = {
            Text(
                "下载完成后即可在字体列表中使用，相关授权信息会一并保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showInstalledClockFonts != null) {
                Md2SettingSwitchRow(
                    title = "显示已安装的时钟字体",
                    checked = showInstalledClockFonts,
                    onCheckedChange = onShowInstalledClockFontsChange
                )
                Text(
                    "时钟字体不包含中文字体，中文字体将保持系统默认字体。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (catalogTabs.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = selectedCatalogTab.coerceIn(catalogTabs.indices),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    catalogTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedCatalogTab == index,
                            onClick = { onCatalogTabSelected(index) },
                            enabled = state.installingFontId == null,
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MsIcon(tab.icon, contentDescription = null, iconSize = 18.dp)
                                    Spacer(Modifier.size(6.dp))
                                    Text(tab.label)
                                }
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "当前下载源：${state.catalogSource.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
                Md2TextButton(
                    onClick = onOpenSources,
                    enabled = state.installingFontId == null
                ) {
                    MsIcon("settings", contentDescription = null, iconSize = 18.dp)
                    Text(sourceActionLabel)
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
