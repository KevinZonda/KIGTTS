package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class SettingsDetailPage(
    val routeId: String,
    val title: String,
    val icon: String,
    val summary: String,
    val category: SettingsCategory
) {
    Kokoro(
        routeId = "kokoro",
        title = "Kokoro 离线朗读",
        icon = "groups",
        summary = "下载、安装和管理 Kokoro 离线朗读资源",
        category = SettingsCategory.Audio
    ),
    RecognitionResources(
        routeId = "recognition-resources",
        title = "语音识别资源包",
        icon = "deployed_code",
        summary = "安装、更新或更换离线识别资源",
        category = SettingsCategory.Recognition
    ),
    RecognitionBehavior(
        routeId = "recognition-behavior",
        title = "识别行为",
        icon = "tune",
        summary = "调整识别语言、断句和声音处理",
        category = SettingsCategory.Recognition
    ),
    SpeakerProfile(
        routeId = "speaker-profile",
        title = "本人声纹",
        icon = "record_voice_over",
        summary = "录制本人声纹并调整声音容错度",
        category = SettingsCategory.Recognition
    ),
    Microphone(
        routeId = "microphone",
        title = "麦克风与降噪",
        icon = "mic_external_on",
        summary = "查看设备状态并调整回声、降噪和设备选择",
        category = SettingsCategory.Recognition
    ),
    Appearance(
        routeId = "appearance",
        title = "显示与主题",
        icon = "palette",
        summary = "调整主题、字体、缩放和悬浮窗外观",
        category = SettingsCategory.System
    ),
    Layout(
        routeId = "layout",
        title = "布局与交互",
        icon = "dashboard_customize",
        summary = "调整页面布局和操作反馈",
        category = SettingsCategory.System
    ),
    QuickSubtitleDisplay(
        routeId = "quick-subtitle-display",
        title = "便捷字幕显示",
        icon = "subtitles",
        summary = "调整占位文本、字号、布局和快捷操作",
        category = SettingsCategory.System
    ),
    Files(
        routeId = "files",
        title = "文件与保存",
        icon = "folder",
        summary = "设置画板保存位置和文件选择方式",
        category = SettingsCategory.System
    ),
    AppBehavior(
        routeId = "app-behavior",
        title = "应用行为",
        icon = "apps",
        summary = "调整名片保存和常用应用快捷操作",
        category = SettingsCategory.System
    ),
    Backup(
        routeId = "backup",
        title = "备份与恢复",
        icon = "settings_backup_restore",
        summary = "备份或恢复应用设置与本地内容",
        category = SettingsCategory.System
    ),
    Contributors(
        routeId = "contributors",
        title = "软件制作",
        icon = "groups",
        summary = "查看参与 KIGTTS 制作的成员",
        category = SettingsCategory.About
    );

    companion object {
        fun fromRouteId(routeId: String?): SettingsDetailPage? =
            entries.firstOrNull { it.routeId == routeId }
    }
}

internal data class SettingsEntrySpec(
    val title: String,
    val icon: String,
    val summary: String,
    val onClick: () -> Unit
)

@Composable
internal fun SettingsEntryCards(
    entries: List<SettingsEntrySpec>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEach { entry ->
            SettingsEntryCard(entry = entry)
        }
    }
}

@Composable
internal fun SettingsEntryCard(
    entry: SettingsEntrySpec,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        SettingsEntryRow(entry = entry)
    }
}

@Composable
internal fun SettingsPageIntroduction(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
        )
    }
}

@Composable
internal fun SettingsConnectedEntryAndExpandableCard(
    entry: SettingsEntrySpec,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedTitle: String,
    expandedIcon: String,
    expandedSummary: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsEntryRow(entry = entry)
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.14f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = rememberKigttsHapticClick {
                            onExpandedChange(!expanded)
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MsIcon(
                    name = expandedIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expandedTitle,
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = expandedSummary,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
                    )
                }
                MsIcon(
                    name = if (expanded) "expand_less" else "expand_more",
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(entry: SettingsEntrySpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = rememberKigttsHapticClick(entry.onClick))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsIcon(
            name = entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colors.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
            )
        }
        Spacer(Modifier.width(4.dp))
        MsIcon(
            name = "chevron_right",
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
        )
    }
}
