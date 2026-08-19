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
    FloatingOverlay,
    LockScreen,
    QuickTextGestures,
    KeyboardHotkeys,
    VolumeHotkeys,
    LanCast,
    ClockFont
}

@Composable
internal fun LockScreenSettingsEntryCard(
    enabled: Boolean,
    hasWallpaper: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    OverlaySettingsEntryCard(
        iconName = "lock",
        title = "自定义锁屏",
        status = when {
            !enabled -> "已关闭"
            hasWallpaper -> "已开启 · 自定义壁纸"
            else -> "已开启 · 系统锁屏壁纸"
        },
        switchLabel = "使用自定义锁屏",
        checked = enabled,
        supportingText = "在锁屏上查看时间、日期，并使用常用快捷功能。",
        onCheckedChange = onEnabledChange,
        onOpen = onOpen
    )
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

        Md2SettingsCard(title = null) {
            Md2SettingSwitchRow(
                title = "启用自定义锁屏",
                icon = "lock",
                checked = state.floatingOverlayShowOnLockScreen,
                onCheckedChange = { enabled ->
                    viewModel.setFloatingOverlayShowOnLockScreen(enabled)
                    if (enabled && requiresVendorPermission) permissionGuideOpen = true
                },
                supportingText = "在锁屏上查看时间、日期，并使用快捷字幕、名片等常用功能。"
            )
        }

        if (requiresVendorPermission) {
            Md2SettingsCard(title = "设备权限") {
                Md2SettingActionRow(
                    title = permissionLabel,
                    icon = "security",
                    supportingText = "查看设备所需的锁屏显示与后台显示权限。",
                    onClick = { permissionGuideOpen = true }
                )
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
                title = "时间和日期靠左",
                icon = "format_align_left",
                checked = settings.timeAndDateAlignedStart,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings {
                        it.copy(timeAndDateAlignedStart = enabled)
                    }
                }
            )
            Md2SettingSwitchRow(
                title = "显示农历日期",
                icon = "calendar_month",
                checked = settings.showLunarDate,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(showLunarDate = enabled) }
                },
                supportingText = "农历会显示在日期后面，例如“7月26日 星期日 · 农历六月十三”。"
            )
        }

        LockScreenBatterySettingsCard(
            settings = settings,
            onSettingsChange = { updated ->
                viewModel.updateLockScreenSettings { updated }
            }
        )

        Md2SettingsCard(title = "锁屏字体") {
            Md2SettingSwitchRow(
                title = "使用系统字体",
                icon = "font_download_off",
                checked = settings.useSystemFont,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(useSystemFont = enabled) }
                },
                supportingText = "时间、日期、电量和解锁提示使用系统字体；快捷功能面板保持原样。"
            )
            Md2SettingSwitchRow(
                title = "单独设置时钟字体",
                icon = "schedule",
                checked = settings.useSeparateClockFont,
                onCheckedChange = { enabled ->
                    viewModel.updateLockScreenSettings { it.copy(useSeparateClockFont = enabled) }
                    if (enabled) onOpenClockFontSettings()
                },
                supportingText = "仅更改时间数字的字体，日期和其它文字不会变化。"
            )
            if (settings.useSeparateClockFont) {
                Md2SettingActionRow(
                    title = "时钟字体",
                    icon = "schedule",
                    trailingText = selectedClockFontName,
                    onClick = onOpenClockFontSettings
                )
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
