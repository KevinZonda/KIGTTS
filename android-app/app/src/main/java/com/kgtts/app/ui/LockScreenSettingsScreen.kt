package com.lhtstudio.kigtts.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.lhtstudio.kigtts.app.data.AppFontDefaults
import com.lhtstudio.kigtts.app.data.AppFontRepository
import com.lhtstudio.kigtts.app.data.LockScreenSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class OverlaySettingsPage {
    Main,
    QuickTextGestures,
    LockScreen,
    ClockFont
}

@Composable
internal fun LockScreenSettingsEntryCard(
    enabled: Boolean,
    hasWallpaper: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    permissionEntryLabel: String?,
    onOpenPermissionGuide: () -> Unit
) {
    Md2SettingsCard(title = "自定义锁屏") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Md2Switch(checked = enabled, onCheckedChange = onEnabledChange)
            Text("启用自定义锁屏")
        }
        Text(
            if (enabled) {
                "已开启${if (hasWallpaper) " · 使用自定义壁纸" else " · 透出系统锁屏壁纸"}"
            } else {
                "开启后可在锁屏上查看时间并使用快捷操作；普通悬浮窗可保持关闭。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsIcon("lock", contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("锁屏样式设置", fontWeight = FontWeight.SemiBold)
                Text(
                    "壁纸、时间日期、农历和字体",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MsIcon("chevron_right", contentDescription = "打开锁屏设置")
        }
        if (permissionEntryLabel != null) {
            Md2OutlinedButton(onClick = onOpenPermissionGuide) {
                Text(permissionEntryLabel)
            }
        }
    }
}

@Composable
internal fun LockScreenSettingsScreen(
    viewModel: MainViewModel,
    state: UiState,
    onOpenClockFontSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings = state.lockScreenSettings
    val cropToolbarColor = MaterialTheme.colors.primary.toArgb()
    val cropToolbarContentColor = MaterialTheme.colors.onPrimary.toArgb()
    val scroll = rememberScrollState()
    var showBuiltinGalleryPicker by rememberSaveable { mutableStateOf(false) }
    var permissionGuideOpen by rememberSaveable { mutableStateOf(false) }
    var wallpaperPreviewRevision by rememberSaveable { mutableLongStateOf(0L) }
    val permissionVendor = remember { LockScreenBackgroundPermissionGuide.vendorForDevice() }
    val requiresVendorPermission = permissionVendor != LockScreenBackgroundPermissionVendor.NONE
    val permissionLabel = remember(permissionVendor) {
        LockScreenBackgroundPermissionPolicy.copyFor(permissionVendor).settingsEntryLabel
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (!result.isSuccessful) {
            if (result.error != null) toast(context, "壁纸裁剪失败，请重新选择")
            return@rememberLauncherForActivityResult
        }
        val uri = result.uriContent ?: return@rememberLauncherForActivityResult
        viewModel.importLockScreenWallpaper(uri) { success ->
            if (success) wallpaperPreviewRevision++
            toast(context, if (success) "锁屏壁纸已更新" else "无法使用这张图片，请重新选择")
        }
    }
    fun cropWallpaper(uri: Uri) {
        cropLauncher.launch(
            CropImageContractOptions(
                uri,
                CropImageOptions(
                    fixAspectRatio = false,
                    activityTitle = "调整锁屏壁纸",
                    cropMenuCropButtonTitle = "确认",
                    toolbarColor = cropToolbarColor,
                    toolbarTitleColor = cropToolbarContentColor,
                    toolbarBackButtonColor = cropToolbarContentColor,
                    toolbarTintColor = cropToolbarContentColor,
                    activityMenuIconColor = cropToolbarContentColor,
                    activityMenuTextColor = cropToolbarContentColor,
                    activityBackgroundColor = 0xFF121212.toInt(),
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = 95,
                    outputRequestWidth = 2560,
                    outputRequestHeight = 2560,
                    outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE
                )
            )
        )
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) cropWallpaper(uri)
    }
    val selectedClockFontName by produceState(
        initialValue = clockFontFallbackName(settings),
        settings.clockFontId
    ) {
        value = withContext(Dispatchers.IO) {
            AppFontRepository(context).listInstalledFonts()
                .firstOrNull { it.id == settings.clockFontId }
                ?.displayName
                ?: clockFontFallbackName(settings)
        }
    }

    CenteredPageColumn(maxWidth = UiTokens.WideContentMaxWidth, scroll = scroll) {
        Spacer(Modifier.height(UiTokens.PageTopBlank))

        Md2SettingsCard(title = "自定义锁屏") {
            Md2SettingSwitchRow(
                title = "启用自定义锁屏",
                checked = state.floatingOverlayShowOnLockScreen,
                onCheckedChange = { enabled ->
                    viewModel.setFloatingOverlayShowOnLockScreen(enabled)
                    if (enabled && requiresVendorPermission) permissionGuideOpen = true
                }
            )
            Text(
                "自定义锁屏独立运行；关闭普通悬浮窗后仍可使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (requiresVendorPermission) {
                Md2OutlinedButton(onClick = { permissionGuideOpen = true }) {
                    Text(permissionLabel)
                }
            }
        }

        LockScreenWallpaperSettingsCard(
            settings = settings,
            wallpaperRevision = wallpaperPreviewRevision,
            onChooseWallpaper = {
                if (state.useBuiltinGallery) {
                    showBuiltinGalleryPicker = true
                } else {
                    imagePicker.launch("image/*")
                }
            },
            onClearWallpaper = {
                viewModel.clearLockScreenWallpaper()
                wallpaperPreviewRevision++
                toast(context, "已恢复系统锁屏壁纸")
            },
            onSettingsChange = { updated ->
                viewModel.updateLockScreenSettings { updated }
            }
        )

        Md2SettingsCard(title = "时间与日期") {
            Md2SettingSwitchRow(
                title = "时间和日期居左显示",
                checked = settings.timeAndDateAlignedStart,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings {
                        it.copy(timeAndDateAlignedStart = enabled)
                    }
                }
            )
            Md2SettingSwitchRow(
                title = "在日期后显示农历",
                checked = settings.showLunarDate,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(showLunarDate = enabled) }
                }
            )
            Text(
                "农历会附加在公历日期后方，例如“7月26日 星期日 · 农历六月十三”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Md2SettingsCard(title = "锁屏字体") {
            Md2SettingSwitchRow(
                title = "锁屏使用系统字体",
                checked = settings.useSystemFont,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(useSystemFont = enabled) }
                }
            )
            Text(
                "仅影响时间、日期和滑动解锁提示，不影响锁屏中的快捷操作面板。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Md2SettingSwitchRow(
                title = "使用单独的时钟字体",
                checked = settings.useSeparateClockFont,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(useSeparateClockFont = enabled) }
                    if (enabled) onOpenClockFontSettings()
                }
            )
            Text(
                "只改变数字时间，日期、农历和其它锁屏文字仍使用上方设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.useSeparateClockFont) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenClockFontSettings)
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MsIcon("schedule", contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("时钟字体", fontWeight = FontWeight.SemiBold)
                        Text(
                            selectedClockFontName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MsIcon("chevron_right", contentDescription = "选择时钟字体")
                }
            }
        }
    }

    if (showBuiltinGalleryPicker) {
        BuiltinGalleryPickerDialog(
            title = "选择锁屏壁纸",
            onDismiss = { showBuiltinGalleryPicker = false },
            onPicked = { uri ->
                showBuiltinGalleryPicker = false
                cropWallpaper(uri)
            }
        )
    }
    if (permissionGuideOpen) {
        LockScreenBackgroundPermissionGuideDialog(
            vendor = permissionVendor,
            onOpenSettings = {
                viewModel.setLockScreenBackgroundPermissionGuideShown(true)
                permissionGuideOpen = false
                if (!LockScreenBackgroundPermissionGuide.openSettings(context)) {
                    toast(context, "无法打开权限设置，请在系统设置中找到 KIGTTS")
                }
            },
            onDismiss = {
                viewModel.setLockScreenBackgroundPermissionGuideShown(true)
                permissionGuideOpen = false
            }
        )
    }
}

private fun clockFontFallbackName(settings: LockScreenSettings): String =
    if (settings.clockFontId == AppFontDefaults.SystemFontId) {
        "系统字体"
    } else {
        settings.clockFontId
    }
