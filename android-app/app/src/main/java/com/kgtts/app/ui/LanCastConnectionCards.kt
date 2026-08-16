package com.lhtstudio.kigtts.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.lan.LanCastStatus
import com.lhtstudio.kigtts.app.util.QuickCardRenderCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LanCastServiceCard(
    viewModel: MainViewModel,
    status: LanCastStatus,
    onEnableRequested: () -> Unit,
    onBackgroundSettingsRequested: () -> Unit
) {
    Md2StaggeredFloatIn(index = 0) {
        Md2SettingsCard(title = "局域网投屏") {
            Md2SettingSwitchRow(
                title = "启用投屏与网页遥控",
                checked = status.running,
                onCheckedChange = { enabled ->
                    if (enabled) onEnableRequested() else viewModel.stopLanCast()
                },
                supportingText = if (status.running) {
                    "服务仅在本次手动开启后运行；锁屏投屏需要允许应用在后台不受限制地运行。"
                } else {
                    "默认关闭。首次开启时会引导设置后台运行，以保持锁屏后的投屏连接。"
                }
            )
            if (status.running) {
                val connected = status.displayClients + status.remoteClients
                Text(
                    text = "已连接 $connected 个网页端，其中 ${status.audioClients} 个正在接收声音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Md2TextButton(onClick = onBackgroundSettingsRequested) {
                    MsIcon("battery_saver", contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("保持后台连接")
                }
            }
            status.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colors.error)
            }
        }
    }
}

@Composable
internal fun LanCastAddressCard(viewModel: MainViewModel, status: LanCastStatus) {
    Md2StaggeredFloatIn(index = 1) {
        Md2SettingsCard(title = "连接地址") {
            if (status.addresses.isEmpty()) {
                Text("未检测到局域网地址，请确认手机已连接 Wi-Fi 或局域网。")
            } else {
                status.addresses.forEachIndexed { index, address ->
                    val selectAddress = rememberKigttsHapticClick {
                        viewModel.selectLanCastAddress(address.id)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple()
                            ) { selectAddress() }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = status.selectedAddress?.id == address.id,
                            onClick = selectAddress
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(address.address, fontWeight = FontWeight.Medium)
                            Text(
                                address.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index != status.addresses.lastIndex) Divider()
                }
            }
            Md2TextButton(onClick = viewModel::refreshLanCastAddresses) {
                MsIcon("refresh", contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("重新检测")
            }
        }
    }
}

@Composable
internal fun LanCastQrCard(status: LanCastStatus) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var qrTab by rememberSaveable { mutableStateOf(LanCastQrTab.Display) }
    val hapticQrTabChange = rememberKigttsHapticValueChange<LanCastQrTab> { qrTab = it }
    val hapticCopy = rememberKigttsKeyHaptic()
    val selectedUrl = status.url(qrTab.path)
    val qrBitmap by produceState<android.graphics.Bitmap?>(null, selectedUrl) {
        value = selectedUrl?.let { url ->
            withContext(Dispatchers.Default) { QuickCardRenderCache.loadQr(url, 720) }
        }
    }

    Md2StaggeredFloatIn(index = 2) {
        Md2SettingsCard(title = "连接二维码") {
            TabRow(
                selectedTabIndex = qrTab.ordinal,
                backgroundColor = md2CardContainerColor(),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                LanCastQrTab.entries.forEach { tab ->
                    Tab(
                        selected = qrTab == tab,
                        onClick = { hapticQrTabChange(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MsIcon(tab.icon, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(tab.title)
                            }
                        }
                    )
                }
            }
            AnimatedContent(
                targetState = qrTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(180)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                                if (forward) it / 7 else -it / 7
                            },
                        initialContentExit = fadeOut(tween(120)) +
                            slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) {
                                if (forward) -it / 9 else it / 9
                            }
                    )
                },
                label = "lan_cast_qr_tab"
            ) { selectedTab ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = selectedTab.title,
                            modifier = Modifier.size(228.dp)
                        )
                    }
                    Text(
                        text = selectedUrl.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                hapticCopy()
                                selectedUrl?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    toast(context, "连接地址已复制")
                                }
                            }
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (selectedTab == LanCastQrTab.Display) {
                            "用于电视或电脑全屏显示字幕。"
                        } else {
                            "用于输入字幕、选择快捷文本和控制朗读。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class LanCastQrTab(val title: String, val path: String, val icon: String) {
    Display("投屏显示", "display", "cast"),
    Remote("网页遥控器", "remote", "settings_remote")
}
