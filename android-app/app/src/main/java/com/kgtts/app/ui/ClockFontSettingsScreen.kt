package com.lhtstudio.kigtts.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhtstudio.kigtts.app.data.AppFontRemoteSource
import com.lhtstudio.kigtts.app.data.InstalledAppFont
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private enum class ClockFontCatalogTab(val label: String, val icon: String) {
    Clock("时钟字体", "schedule"),
    Chinese("中文字体", "translate")
}

@Composable
internal fun ClockFontSettingsScreen(
    selectedFontId: String,
    selectedWeight: Int,
    onSelect: (InstalledAppFont, Int) -> Unit,
    onTopBarActionsChange: (FontTopBarActions?) -> Unit,
    useBuiltinFileManager: Boolean,
    fontViewModel: FontSettingsViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by fontViewModel.state.collectAsState()
    var weightFont by remember { mutableStateOf<InstalledAppFont?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showBuiltinFontPicker by rememberSaveable { mutableStateOf(false) }
    var catalogTabIndex by rememberSaveable { mutableIntStateOf(ClockFontCatalogTab.Clock.ordinal) }
    val currentTime = rememberClockPreviewText()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) fontViewModel.importFont(uri)
    }

    fun openSystemFontPicker() {
        importLauncher.launch(fontPickerMimeTypes())
    }

    fun repositoryUrl(source: AppFontRemoteSource, tabIndex: Int): String =
        if (tabIndex == ClockFontCatalogTab.Clock.ordinal) {
            source.clockRepositoryBaseUrl
        } else {
            when (source) {
                AppFontRemoteSource.ModelScope -> state.modelScopeRepositoryBaseUrl
                AppFontRemoteSource.HuggingFace -> state.huggingFaceRepositoryBaseUrl
            }
        }

    LaunchedEffect(fontViewModel) {
        fontViewModel.events.collectLatest { message -> toast(context, message) }
    }
    DisposableEffect(importLauncher, fontViewModel, useBuiltinFileManager) {
        onTopBarActionsChange(
            FontTopBarActions(
                onImport = {
                    if (useBuiltinFileManager) {
                        showBuiltinFontPicker = true
                    } else {
                        openSystemFontPicker()
                    }
                },
                onDownload = { showDownloadDialog = true }
            )
        )
        onDispose { onTopBarActionsChange(null) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = UiTokens.WideListMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = UiTokens.PageTopBlank,
                end = 16.dp,
                bottom = pageBottomBlankPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "clock-font-download") {
                Md2SettingsCard(title = null) {
                    Text(
                        "所选字体只用于锁屏上的数字时间。已安装的中文字体也可以直接选择。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Md2OutlinedButton(onClick = { showDownloadDialog = true }) {
                        MsIcon("download", contentDescription = null, iconSize = 18.dp)
                        Text("下载字体")
                    }
                }
            }
            if (state.refreshing) {
                item(key = "clock-font-loading") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            items(state.fonts, key = { "clock-${it.id}" }) { font ->
                val selected = font.id == selectedFontId
                ClockFontListItem(
                    font = font,
                    previewText = currentTime,
                    selected = selected,
                    previewWeight = if (selected) {
                        font.normalizeWeight(selectedWeight)
                    } else {
                        font.preferredWeight
                    },
                    busy = state.operationBusy,
                    onSelect = { onSelect(font, font.normalizeWeight(font.preferredWeight)) },
                    onWeightSettings = { weightFont = font },
                    onLicense = { fontViewModel.showLicense(font) }
                )
            }
        }
    }

    weightFont?.let { font ->
        FontWeightDialog(
            font = font.copy(preferredWeight = font.normalizeWeight(selectedWeight)),
            onDismiss = { weightFont = null },
            onConfirm = { weight ->
                onSelect(font, font.normalizeWeight(weight))
                weightFont = null
            }
        )
    }
    if (showDownloadDialog) {
        LaunchedEffect(showDownloadDialog) {
            catalogTabIndex = ClockFontCatalogTab.Clock.ordinal
            fontViewModel.loadCatalog(
                AppFontRemoteSource.ModelScope,
                AppFontRemoteSource.ModelScope.clockRepositoryBaseUrl
            )
        }
        val nextSource = when (state.catalogSource) {
            AppFontRemoteSource.ModelScope -> AppFontRemoteSource.HuggingFace
            AppFontRemoteSource.HuggingFace -> AppFontRemoteSource.ModelScope
        }
        FontDownloadDialog(
            state = state,
            installedFonts = state.fonts.associateBy { it.id },
            onOpenSources = {
                fontViewModel.loadCatalog(nextSource, repositoryUrl(nextSource, catalogTabIndex))
            },
            sourceActionLabel = "切换到${nextSource.displayName}",
            catalogTabs = ClockFontCatalogTab.entries.map {
                FontDownloadCatalogTab(it.label, it.icon)
            },
            selectedCatalogTab = catalogTabIndex,
            onCatalogTabSelected = { index ->
                catalogTabIndex = index
                fontViewModel.loadCatalog(
                    state.catalogSource,
                    repositoryUrl(state.catalogSource, index)
                )
            },
            onInstall = { font ->
                fontViewModel.installRemoteFont(
                    font,
                    repositoryUrl(state.catalogSource, catalogTabIndex)
                )
            },
            onDismiss = { showDownloadDialog = false }
        )
    }
    if (showBuiltinFontPicker) {
        BuiltinFilePickerDialog(
            title = "导入字体",
            allowedExtensions = BuiltinFontFileExtensions,
            onDismiss = { showBuiltinFontPicker = false },
            onPicked = { uri ->
                showBuiltinFontPicker = false
                fontViewModel.importFont(uri)
            },
            onOpenSystemPicker = {
                showBuiltinFontPicker = false
                openSystemFontPicker()
            }
        )
    }
    val licenseTitle = state.licenseTitle
    if (licenseTitle != null) {
        Md2ScrollableDialog(
            onDismissRequest = fontViewModel::dismissLicense,
            title = { Text(licenseTitle) },
            content = {
                SelectionContainer { Text(state.licenseText ?: "正在读取许可证…") }
            },
            confirmButton = {
                Md2TextButton(onClick = fontViewModel::dismissLicense) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ClockFontListItem(
    font: InstalledAppFont,
    previewText: String,
    selected: Boolean,
    previewWeight: Int,
    busy: Boolean,
    onSelect: () -> Unit,
    onWeightSettings: () -> Unit,
    onLicense: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Md2ControlShape,
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ProgressiveFontName(font, previewWeight, previewText, 38)
                Text(
                    font.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Md2IconButton(
                    icon = if (selected) "check_circle" else "schedule",
                    contentDescription = if (selected) "当前时钟字体" else "使用该时钟字体",
                    onClick = onSelect,
                    enabled = !selected && !busy
                )
                if (font.supportsWeightSelection) {
                    Md2IconButton(
                        icon = "tune",
                        contentDescription = "设置时钟字重",
                        onClick = onWeightSettings,
                        enabled = !busy
                    )
                }
                if (font.licenseFile != null) {
                    Md2IconButton(
                        icon = "article",
                        contentDescription = "查看许可证",
                        onClick = onLicense
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberClockPreviewText(): String {
    var preview by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            preview = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return preview
}
