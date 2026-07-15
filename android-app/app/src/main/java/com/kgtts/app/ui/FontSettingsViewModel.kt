package com.lhtstudio.kigtts.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhtstudio.kigtts.app.data.AppFontDefaults
import com.lhtstudio.kigtts.app.data.AppFontInstallProgress
import com.lhtstudio.kigtts.app.data.AppFontRemoteSource
import com.lhtstudio.kigtts.app.data.AppFontRepository
import com.lhtstudio.kigtts.app.data.InstalledAppFont
import com.lhtstudio.kigtts.app.data.RemoteAppFont
import com.lhtstudio.kigtts.app.data.UserPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FontSettingsUiState(
    val fonts: List<InstalledAppFont> = listOf(AppFontRepository.systemFont()),
    val selectedFontId: String = AppFontDefaults.SystemFontId,
    val selectedWeight: Int = AppFontDefaults.DefaultWeight,
    val refreshing: Boolean = true,
    val operationBusy: Boolean = false,
    val catalogSource: AppFontRemoteSource = AppFontRemoteSource.ModelScope,
    val catalog: List<RemoteAppFont> = emptyList(),
    val catalogLoading: Boolean = false,
    val installingFontId: String? = null,
    val installProgress: AppFontInstallProgress? = null,
    val licenseTitle: String? = null,
    val licenseText: String? = null
)

internal class FontSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppFontRepository(application)
    private val _state = MutableStateFlow(FontSettingsUiState())
    val state: StateFlow<FontSettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            UserPrefs.observeSettings(application).collectLatest { settings ->
                _state.update {
                    it.copy(
                        selectedFontId = settings.appFontId,
                        selectedWeight = settings.appFontWeight
                    )
                }
            }
        }
        refreshFonts()
    }

    fun refreshFonts() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            runCatching { repository.listInstalledFonts() }
                .onSuccess { fonts ->
                    val selected = _state.value.selectedFontId
                    if (fonts.none { it.id == selected }) {
                        UserPrefs.setAppFont(
                            getApplication(),
                            AppFontDefaults.SystemFontId,
                            AppFontDefaults.DefaultWeight
                        )
                    }
                    _state.update { it.copy(fonts = fonts, refreshing = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(refreshing = false) }
                    notify(error.userMessage("字体列表加载失败"))
                }
        }
    }

    fun importFont(uri: Uri) {
        if (_state.value.operationBusy) return
        viewModelScope.launch {
            _state.update { it.copy(operationBusy = true) }
            runCatching {
                repository.importFont(uri, getApplication<Application>().contentResolver)
            }.onSuccess { font ->
                UserPrefs.setAppFont(getApplication(), font.id, font.preferredWeight)
                notify("已导入并使用 ${font.displayName}")
                refreshFonts()
            }.onFailure { error ->
                notify(error.userMessage("字体导入失败"))
            }
            _state.update { it.copy(operationBusy = false) }
        }
    }

    fun selectFont(font: InstalledAppFont) {
        viewModelScope.launch {
            val weight = font.weightAxis?.clamp(font.preferredWeight)
                ?: AppFontDefaults.DefaultWeight
            UserPrefs.setAppFont(getApplication(), font.id, weight)
            notify("已使用 ${font.displayName}")
        }
    }

    fun updateFontWeight(font: InstalledAppFont, weight: Int) {
        if (!font.isVariable || _state.value.operationBusy) return
        viewModelScope.launch {
            _state.update { it.copy(operationBusy = true) }
            runCatching { repository.updatePreferredWeight(font.id, weight) }
                .onSuccess { updated ->
                    if (_state.value.selectedFontId == updated.id) {
                        UserPrefs.setAppFont(getApplication(), updated.id, updated.preferredWeight)
                    }
                    refreshFonts()
                }
                .onFailure { error -> notify(error.userMessage("字重更新失败")) }
            _state.update { it.copy(operationBusy = false) }
        }
    }

    fun deleteFont(font: InstalledAppFont) {
        if (!font.isRemovable || _state.value.operationBusy) return
        viewModelScope.launch {
            _state.update { it.copy(operationBusy = true) }
            runCatching {
                if (_state.value.selectedFontId == font.id) {
                    UserPrefs.setAppFont(
                        getApplication(),
                        AppFontDefaults.SystemFontId,
                        AppFontDefaults.DefaultWeight
                    )
                }
                repository.deleteFont(font.id)
            }.onSuccess {
                notify("已删除 ${font.displayName}")
                refreshFonts()
            }.onFailure { error -> notify(error.userMessage("字体删除失败")) }
            _state.update { it.copy(operationBusy = false) }
        }
    }

    fun loadCatalog(source: AppFontRemoteSource = _state.value.catalogSource) {
        if (_state.value.catalogLoading) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    catalogSource = source,
                    catalogLoading = true,
                    catalog = if (source == it.catalogSource) it.catalog else emptyList()
                )
            }
            runCatching { repository.fetchCatalog(source) }
                .onSuccess { catalog -> _state.update { it.copy(catalog = catalog) } }
                .onFailure { error -> notify(error.userMessage("字体清单加载失败")) }
            _state.update { it.copy(catalogLoading = false) }
        }
    }

    fun installRemoteFont(font: RemoteAppFont) {
        if (_state.value.installingFontId != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    installingFontId = font.id,
                    installProgress = AppFontInstallProgress(0f, "准备下载")
                )
            }
            runCatching {
                repository.installRemoteFont(font, _state.value.catalogSource) { progress ->
                    _state.update { it.copy(installProgress = progress) }
                }
            }.onSuccess {
                notify("已安装 ${font.displayName}")
                refreshFonts()
            }.onFailure { error -> notify(error.userMessage("字体安装失败")) }
            _state.update { it.copy(installingFontId = null, installProgress = null) }
        }
    }

    fun showLicense(font: InstalledAppFont) {
        viewModelScope.launch {
            _state.update { it.copy(licenseTitle = "${font.displayName} 许可证", licenseText = null) }
            val text = runCatching { repository.readLicense(font) }
                .getOrElse { it.userMessage("许可证读取失败") }
            _state.update { it.copy(licenseText = text) }
        }
    }

    fun dismissLicense() {
        _state.update { it.copy(licenseTitle = null, licenseText = null) }
    }

    private fun notify(message: String) {
        _events.tryEmit(message)
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
