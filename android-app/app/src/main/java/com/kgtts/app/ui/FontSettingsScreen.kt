package com.lhtstudio.kigtts.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhtstudio.kigtts.app.data.AppFontDefaults
import com.lhtstudio.kigtts.app.data.AppFontOrigin
import com.lhtstudio.kigtts.app.data.InstalledAppFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class FontTopBarActions(
    val onImport: () -> Unit,
    val onDownload: () -> Unit
)

internal fun fontPickerMimeTypes(): Array<String> = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/x-font-opentype",
    "application/octet-stream"
)

@Composable
internal fun FontSettingsScreen(
    onTopBarActionsChange: (FontTopBarActions?) -> Unit,
    useBuiltinFileManager: Boolean,
    fontViewModel: FontSettingsViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by fontViewModel.state.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showDownloadSourceDialog by remember { mutableStateOf(false) }
    var showBuiltinFontPicker by rememberSaveable { mutableStateOf(false) }
    var showInstalledClockFonts by rememberSaveable { mutableStateOf(false) }
    var weightFont by remember { mutableStateOf<InstalledAppFont?>(null) }
    var deleteFont by remember { mutableStateOf<InstalledAppFont?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) fontViewModel.importFont(uri)
    }

    fun openSystemFontPicker() {
        importLauncher.launch(fontPickerMimeTypes())
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
    LaunchedEffect(fontViewModel) {
        fontViewModel.events.collectLatest { message -> toast(context, message) }
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
            item(key = "font-page-introduction") {
                SettingsPageIntroduction(
                    title = "字体",
                    description = "管理应用使用的字体"
                )
            }
            if (state.refreshing) {
                item(key = "font-loading") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            val visibleFonts = state.fonts.filter { font ->
                showInstalledClockFonts || !AppFontDefaults.isClockFontId(font.id)
            }
            items(visibleFonts, key = { it.id }) { font ->
                FontListItem(
                    font = font,
                    selected = state.selectedFontId == font.id,
                    selectedWeight = state.selectedWeight,
                    busy = state.operationBusy,
                    onSelect = { fontViewModel.selectFont(font) },
                    onWeightSettings = { weightFont = font },
                    onLicense = { fontViewModel.showLicense(font) },
                    onDelete = { deleteFont = font }
                )
            }
        }
    }

    weightFont?.let { font ->
        FontWeightDialog(
            font = font,
            onDismiss = { weightFont = null },
            onConfirm = { weight ->
                fontViewModel.updateFontWeight(font, weight)
                weightFont = null
            }
        )
    }
    deleteFont?.let { font ->
        KigttsAlertDialog(
            onDismissRequest = { deleteFont = null },
            title = { Text("删除字体") },
            text = { Text("确定删除“${font.displayName}”吗？") },
            confirmButton = {
                Md2TextButton(onClick = {
                    fontViewModel.deleteFont(font)
                    deleteFont = null
                }) { Text("删除") }
            },
            dismissButton = {
                Md2TextButton(onClick = { deleteFont = null }) { Text("取消") }
            }
        )
    }
    if (showDownloadDialog) {
        LaunchedEffect(showDownloadDialog) {
            fontViewModel.loadCatalog(state.catalogSource)
        }
        FontDownloadDialog(
            state = state,
            installedFonts = state.fonts.associateBy { it.id },
            onOpenSources = { showDownloadSourceDialog = true },
            showInstalledClockFonts = showInstalledClockFonts,
            onShowInstalledClockFontsChange = { showInstalledClockFonts = it },
            onInstall = { fontViewModel.installRemoteFont(it) },
            onDismiss = {
                showDownloadSourceDialog = false
                showDownloadDialog = false
            }
        )
    }
    if (showDownloadSourceDialog) {
        FontDownloadSourceDialog(
            modelScopeUrl = state.modelScopeRepositoryBaseUrl,
            huggingFaceUrl = state.huggingFaceRepositoryBaseUrl,
            preferredSource = state.catalogSource,
            onDismiss = { showDownloadSourceDialog = false },
            onConfirm = { modelScopeUrl, huggingFaceUrl, preferredSource ->
                fontViewModel.saveDownloadSources(
                    modelScopeUrl,
                    huggingFaceUrl,
                    preferredSource
                )
                showDownloadSourceDialog = false
            }
        )
    }
    val licenseTitle = state.licenseTitle
    if (licenseTitle != null) {
        Md2ScrollableDialog(
            onDismissRequest = fontViewModel::dismissLicense,
            title = { Text(licenseTitle) },
            content = {
                SelectionContainer {
                    Text(state.licenseText ?: "正在读取许可证…")
                }
            },
            confirmButton = {
                Md2TextButton(onClick = fontViewModel::dismissLicense) { Text("关闭") }
            }
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
}

@Composable
private fun FontListItem(
    font: InstalledAppFont,
    selected: Boolean,
    selectedWeight: Int,
    busy: Boolean,
    onSelect: () -> Unit,
    onWeightSettings: () -> Unit,
    onLicense: () -> Unit,
    onDelete: () -> Unit
) {
    val previewWeight = if (selected && font.supportsWeightSelection) {
        font.normalizeWeight(selectedWeight)
    } else {
        font.preferredWeight
    }
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ProgressiveFontName(font, previewWeight)
                Text(
                    buildString {
                        append(
                            when (font.origin) {
                                AppFontOrigin.System -> "系统内置"
                                AppFontOrigin.Imported -> "本地导入"
                                AppFontOrigin.Downloaded -> font.licenseName
                            }
                        )
                        if (font.supportsWeightSelection) append(" · 字重 $previewWeight")
                        if (selected) append(" · 当前使用")
                    },
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
                    icon = if (selected) "check_circle" else "font_download",
                    contentDescription = if (selected) "当前字体" else "使用该字体",
                    onClick = onSelect,
                    enabled = !selected && !busy
                )
                if (font.supportsWeightSelection) {
                    Md2IconButton(
                        icon = "tune",
                        contentDescription = "设置字重",
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
                if (font.isRemovable) {
                    Md2IconButton(
                        icon = "delete",
                        contentDescription = "删除字体",
                        onClick = onDelete,
                        enabled = !busy
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgressiveFontName(
    font: InstalledAppFont,
    weight: Int,
    text: String = font.displayName,
    fontSizeSp: Int = 30
) {
    val source = font.familySource().takeIf { it.files.isNotEmpty() }
    val lastModified = font.weightFiles.sumOf { it.file.lastModified() }
    val previewFamily by produceState<FontFamily?>(null, source, lastModified, weight) {
        value = withContext(Dispatchers.IO) {
            source?.let {
                runCatching { loadAppFontFamily(it, weight) }.getOrNull()
            }
        }
    }
    Crossfade(
        targetState = previewFamily,
        animationSpec = tween(180),
        label = "font_preview_${font.id}"
    ) { family ->
        Text(
            text = text,
            fontFamily = family ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 8).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun FontWeightDialog(
    font: InstalledAppFont,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!font.supportsWeightSelection) return
    val axis = font.weightAxis
    val availableWeights = font.availableWeights
    val initialPosition = if (axis != null) {
        font.preferredWeight.toFloat()
    } else {
        availableWeights.indexOf(font.normalizeWeight(font.preferredWeight))
            .coerceAtLeast(0)
            .toFloat()
    }
    var sliderPosition by remember(font.id, font.preferredWeight) {
        mutableFloatStateOf(initialPosition)
    }
    val selectedWeight = if (axis != null) {
        sliderPosition.roundToInt().coerceIn(axis.min, axis.max)
    } else {
        availableWeights[sliderPosition.roundToInt().coerceIn(availableWeights.indices)]
    }
    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text("${font.displayName} 字重") },
        contentSpacing = 10.dp,
        content = {
            Text("当前字重：$selectedWeight", style = MaterialTheme.typography.bodyLarge)
            if (axis != null) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = axis.min.toFloat()..axis.max.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "可选字重 ${axis.min}-${axis.max}，字体默认值 ${axis.default}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..availableWeights.lastIndex.toFloat(),
                    steps = (availableWeights.size - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "真实字重 ${availableWeights.joinToString(" / ")}，拖动时按档位吸附",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val previewFont = font.copy(preferredWeight = selectedWeight)
            ProgressiveFontName(previewFont, selectedWeight)
        },
        confirmButton = {
            Md2TextButton(onClick = { onConfirm(selectedWeight) }) { Text("应用") }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(4.dp))
        }
    )
}
