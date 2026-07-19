package com.lhtstudio.kigtts.app.lan

import com.lhtstudio.kigtts.app.data.LedSubtitleSettings
import android.content.Context
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object LanCastRuntime {
    const val DEFAULT_PORT = 8765
    private const val MAX_COMMAND_TEXT_LENGTH = 4_000

    interface CommandHandler {
        fun submitSubtitle(text: String, playVoice: Boolean)
        fun clearSubtitle()
        fun replaySubtitle(text: String)
        fun setRealtimeRunning(running: Boolean)
        fun openApp()
    }

    private val stateRevision = AtomicLong(0L)
    private val mediaRevision = AtomicLong(0L)
    private val mediaFiles = ConcurrentHashMap<Long, File>()
    private val _status = MutableStateFlow(LanCastStatus())
    private val _presentation = MutableStateFlow(LanCastPresentationState())
    private val _uiCommands = MutableSharedFlow<LanCastUiCommand>(extraBufferCapacity = 32)

    @Volatile private var server: LanCastServer? = null
    @Volatile private var commandHandler: CommandHandler? = null
    @Volatile private var currentFontFile: File? = null

    fun statusFlow(): StateFlow<LanCastStatus> = _status.asStateFlow()
    fun presentationFlow(): StateFlow<LanCastPresentationState> = _presentation.asStateFlow()
    fun uiCommands(): SharedFlow<LanCastUiCommand> = _uiCommands
    fun status(): LanCastStatus = _status.value
    fun presentation(): LanCastPresentationState = _presentation.value

    fun startServer(context: Context): Boolean {
        if (server != null) return true
        val addresses = LanCastNetwork.addresses()
        return runCatching {
            val next = LanCastServer(context.applicationContext, DEFAULT_PORT, this)
            // Keep WebSockets alive between the browser's 10-second heartbeat messages.
            next.start(60_000, false)
            server = next
            val previous = _status.value
            _status.value = previous.copy(
                running = true,
                addresses = addresses,
                selectedAddressId = previous.selectedAddressId
                    ?.takeIf { id -> addresses.any { it.id == id } }
                    ?: addresses.firstOrNull()?.id,
                error = if (addresses.isEmpty()) "未找到可用的局域网 IPv4 地址" else null
            )
            next.broadcastState()
            true
        }.onFailure { error ->
            AppLogger.e("LanCast server start failed", error)
            _status.value = _status.value.copy(
                running = false,
                addresses = addresses,
                error = error.message ?: "投屏服务启动失败"
            )
        }.getOrDefault(false)
    }

    fun stopServer() {
        val active = server
        server = null
        runCatching { active?.stop() }
        mediaFiles.clear()
        _status.value = _status.value.copy(
            running = false,
            displayClients = 0,
            remoteClients = 0,
            audioClients = 0,
            error = null
        )
    }

    fun refreshAddresses() {
        val addresses = LanCastNetwork.addresses()
        val previous = _status.value
        _status.value = previous.copy(
            addresses = addresses,
            selectedAddressId = previous.selectedAddressId
                ?.takeIf { id -> addresses.any { it.id == id } }
                ?: addresses.firstOrNull()?.id,
            error = if (previous.running && addresses.isEmpty()) {
                "未找到可用的局域网 IPv4 地址"
            } else {
                previous.error?.takeUnless { it.startsWith("未找到可用") }
            }
        )
    }

    fun selectAddress(id: String) {
        val current = _status.value
        if (current.addresses.none { it.id == id }) return
        _status.value = current.copy(selectedAddressId = id)
    }

    fun registerCommandHandler(handler: CommandHandler) {
        commandHandler = handler
    }

    fun unregisterCommandHandler(handler: CommandHandler) {
        if (commandHandler === handler) commandHandler = null
    }

    fun updatePresentation(
        state: LanCastPresentationState,
        fontFile: File?
    ) {
        val current = _presentation.value
        val next = state.copy(
            revision = stateRevision.incrementAndGet(),
            displayMode = current.displayMode,
            running = current.running,
            playbackProgress = current.playbackProgress
        )
        currentFontFile = fontFile?.takeIf { it.isFile }
        _presentation.value = next
        server?.broadcastState()
    }

    fun updateSubtitleText(text: String) {
        _presentation.value = _presentation.value.copy(
            revision = stateRevision.incrementAndGet(),
            text = text,
            inputText = ""
        )
        server?.broadcastState()
    }

    fun updateRealtimeState(running: Boolean, playbackProgress: Float) {
        val current = _presentation.value
        val progress = playbackProgress.coerceIn(0f, 1f)
        if (current.running == running && current.playbackProgress == progress) return
        val runningChanged = current.running != running
        _presentation.value = current.copy(
            revision = stateRevision.incrementAndGet(),
            running = running,
            playbackProgress = progress
        )
        if (runningChanged) server?.broadcastState()
    }

    fun currentFontFile(): File? = currentFontFile?.takeIf { it.isFile }

    fun updateClientCounts(display: Int, remote: Int, audio: Int) {
        _status.value = _status.value.copy(
            displayClients = display.coerceAtLeast(0),
            remoteClients = remote.coerceAtLeast(0),
            audioClients = audio.coerceAtLeast(0)
        )
    }

    fun hasAudioClients(): Boolean = _status.value.running && _status.value.audioClients > 0

    fun broadcastAudioControl(message: JSONObject) {
        server?.broadcastAudioText(message.toString())
    }

    fun broadcastAudio(payload: ByteArray) {
        server?.broadcastAudio(payload)
    }

    fun registerMedia(file: File): Long {
        val id = mediaRevision.incrementAndGet()
        mediaFiles[id] = file
        return id
    }

    fun mediaFile(id: Long): File? = mediaFiles[id]?.takeIf { it.isFile }

    fun unregisterMedia(id: Long) {
        mediaFiles.remove(id)
    }

    fun handleCommand(raw: String): JSONObject {
        val response = JSONObject().put("type", "ack")
        return runCatching {
            val command = JSONObject(raw)
            val requestId = command.optString("requestId")
            if (requestId.isNotBlank()) response.put("requestId", requestId)
            when (command.optString("type")) {
                "submit" -> {
                    val handler = commandHandler ?: error("主软件控制服务尚未就绪")
                    val text = command.requireText()
                    handler.submitSubtitle(text, command.optBoolean("playVoice", true))
                }
                "clear" -> {
                    val handler = commandHandler ?: error("主软件控制服务尚未就绪")
                    handler.clearSubtitle()
                }
                "replay" -> {
                    val handler = commandHandler ?: error("主软件控制服务尚未就绪")
                    val text = command.optString("text").trim()
                        .ifBlank { _presentation.value.text }
                    if (text.isBlank()) error("没有可播放的字幕")
                    handler.replaySubtitle(text.take(MAX_COMMAND_TEXT_LENGTH))
                }
                "realtime" -> {
                    val handler = commandHandler ?: error("主软件控制服务尚未就绪")
                    handler.setRealtimeRunning(command.optBoolean("running"))
                }
                "openApp" -> {
                    val handler = commandHandler ?: error("主软件控制服务尚未就绪")
                    handler.openApp()
                }
                "displayMode" -> {
                    val mode = command.optString("mode").takeIf {
                        it == "adaptive" || it == "led"
                    } ?: error("不支持的显示模式")
                    _presentation.value = _presentation.value.copy(
                        revision = stateRevision.incrementAndGet(),
                        displayMode = mode
                    )
                    server?.broadcastState()
                }
                "ledSettings" -> {
                    val settings = command.optJSONObject("settings")
                        ?: error("缺少 LED 设置")
                    val next = parseLedStyle(settings, _presentation.value.led)
                    updateLedStyle(next)
                    _uiCommands.tryEmit(LanCastUiCommand.UpdateDisplaySettings(next))
                }
                "resetLedSettings" -> {
                    val next = LanCastLedStyle()
                    updateLedStyle(next)
                    _uiCommands.tryEmit(LanCastUiCommand.ResetDisplaySettings)
                }
                "playOnSend" -> {
                    val enabled = command.optBoolean("enabled", true)
                    _presentation.value = _presentation.value.copy(
                        revision = stateRevision.incrementAndGet(),
                        playOnSend = enabled
                    )
                    server?.broadcastState()
                    _uiCommands.tryEmit(LanCastUiCommand.SetPlayOnSend(enabled))
                }
                "quickStyle" -> {
                    val current = _presentation.value
                    val next = current.copy(
                        revision = stateRevision.incrementAndGet(),
                        bold = command.optBoolean("bold", current.bold),
                        centered = command.optBoolean("centered", current.centered),
                        rotated180 = command.optBoolean("rotated180", current.rotated180),
                        fontSizeSp = command.optDouble("fontSizeSp", current.fontSizeSp.toDouble())
                            .toFloat().coerceIn(28f, 800f)
                    )
                    _presentation.value = next
                    server?.broadcastState()
                    _uiCommands.tryEmit(
                        LanCastUiCommand.UpdateQuickSubtitleStyle(
                            bold = next.bold,
                            centered = next.centered,
                            rotated180 = next.rotated180,
                            fontSizeSp = next.fontSizeSp
                        )
                    )
                }
                "audioOutputMode" -> {
                    val mode = command.optInt("mode", LanCastAudioOutputMode.Local.preferenceValue)
                        .coerceIn(
                            LanCastAudioOutputMode.Local.preferenceValue,
                            LanCastAudioOutputMode.Both.preferenceValue
                        )
                    _presentation.value = _presentation.value.copy(
                        revision = stateRevision.incrementAndGet(),
                        audioOutputMode = mode
                    )
                    server?.broadcastState()
                    _uiCommands.tryEmit(LanCastUiCommand.SetAudioOutputMode(mode))
                }
                "selectGroup" -> {
                    val groupId = command.optLong("groupId", Long.MIN_VALUE)
                    if (_presentation.value.groups.none { it.id == groupId }) {
                        error("快捷文本分组不存在")
                    }
                    _presentation.value = _presentation.value.copy(
                        revision = stateRevision.incrementAndGet(),
                        selectedGroupId = groupId
                    )
                    server?.broadcastState()
                    _uiCommands.tryEmit(LanCastUiCommand.SelectQuickTextGroup(groupId))
                }
                "addCurrentText" -> {
                    val groupId = command.optLong("groupId", Long.MIN_VALUE)
                    val current = _presentation.value
                    val text = current.text.trim().ifBlank { error("当前字幕为空") }
                    if (current.groups.none { it.id == groupId }) error("快捷文本分组不存在")
                    _presentation.value = current.copy(
                        revision = stateRevision.incrementAndGet(),
                        groups = current.groups.map { group ->
                            if (group.id == groupId) group.copy(items = group.items + text) else group
                        }
                    )
                    server?.broadcastState()
                    _uiCommands.tryEmit(LanCastUiCommand.AddCurrentText(groupId))
                }
                else -> error("不支持的遥控命令")
            }
            response.put("ok", true)
        }.getOrElse { error ->
            response.put("ok", false)
            response.put("error", error.message ?: "命令执行失败")
        }
    }

    private fun JSONObject.requireText(): String {
        val text = optString("text").trim().take(MAX_COMMAND_TEXT_LENGTH)
        if (text.isEmpty()) error("字幕内容不能为空")
        return text
    }

    private fun updateLedStyle(settings: LanCastLedStyle) {
        _presentation.value = _presentation.value.copy(
            revision = stateRevision.incrementAndGet(),
            led = settings
        )
        server?.broadcastState()
    }

    private fun parseLedStyle(source: JSONObject, current: LanCastLedStyle): LanCastLedStyle {
        val density = source.optDouble("dotDensity", current.dotDensity.toDouble())
            .toFloat().coerceIn(0f, 1f)
        return current.copy(
            colorArgb = source.optCssColor("color", current.colorArgb),
            backgroundArgb = source.optCssColor("background", current.backgroundArgb),
            dotMatrix = source.optBoolean("dotMatrix", current.dotMatrix),
            dotShape = source.optInt("dotShape", current.dotShape).coerceIn(0, 1),
            dotDensity = density,
            dotSize = 4f + density * 8f,
            dotGap = 1f + (1f - density) * 5f,
            glowEnabled = source.optBoolean("glowEnabled", current.glowEnabled),
            glowStrength = source.optDouble("glowStrength", current.glowStrength.toDouble())
                .toFloat().coerceIn(0f, 1f),
            displayHeightFraction = source.optDouble(
                "displayHeightFraction",
                current.displayHeightFraction.toDouble()
            ).toFloat().coerceIn(0.35f, 0.92f),
            adaptiveMultiLine = source.optBoolean(
                "adaptiveMultiLine",
                current.adaptiveMultiLine
            ),
            speed = source.optDouble("speed", current.speed.toDouble())
                .toFloat().coerceIn(
                    LedSubtitleSettings.MIN_SCROLL_SPEED_DP_PER_SECOND,
                    LedSubtitleSettings.MAX_SCROLL_SPEED_DP_PER_SECOND
                ),
            direction = source.optInt("direction", current.direction).coerceIn(0, 1),
            quickSwipeOpensQuickText = source.optBoolean(
                "quickSwipeOpensQuickText",
                current.quickSwipeOpensQuickText
            ),
            loopGap = source.optDouble("loopGap", current.loopGap.toDouble())
                .toFloat().coerceIn(24f, 240f),
            shortTextAlignment = source.optInt(
                "shortTextAlignment",
                current.shortTextAlignment
            ).coerceIn(0, 2),
            keepScreenOn = source.optBoolean("keepScreenOn", current.keepScreenOn),
            followSystemBrightness = source.optBoolean(
                "followSystemBrightness",
                current.followSystemBrightness
            ),
            screenBrightness = source.optDouble(
                "screenBrightness",
                current.screenBrightness.toDouble()
            ).toFloat().coerceIn(0.1f, 1f)
        )
    }

    private fun JSONObject.optCssColor(name: String, fallback: Int): Int {
        val raw = optString(name).removePrefix("#")
        val rgb = raw.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return fallback
        return (0xFF000000L or rgb).toInt()
    }
}
