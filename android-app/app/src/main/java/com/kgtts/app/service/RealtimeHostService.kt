package com.lhtstudio.kigtts.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.lhtstudio.kigtts.app.audio.RealtimeController
import com.lhtstudio.kigtts.app.audio.SimulatedAudioRunResult
import com.lhtstudio.kigtts.app.audio.SoundboardManager
import com.lhtstudio.kigtts.app.audio.shouldSuppressTtsForSoundboardTrigger
import com.lhtstudio.kigtts.app.audio.SpeakerEnrollResult
import com.lhtstudio.kigtts.app.audio.SpeakerVerificationTolerance
import com.lhtstudio.kigtts.app.data.KOKORO_VOICE_NAME
import com.lhtstudio.kigtts.app.data.ModelRepository
import com.lhtstudio.kigtts.app.data.SYSTEM_TTS_VOICE_NAME
import com.lhtstudio.kigtts.app.data.AsrRecognitionLanguage
import com.lhtstudio.kigtts.app.data.ListeningModeSettings
import com.lhtstudio.kigtts.app.data.SpeechButtonActionMode
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.data.resolveQuickSubtitleStartupText
import com.lhtstudio.kigtts.app.data.isSystemTtsVoiceDir
import com.lhtstudio.kigtts.app.overlay.OverlayBridge
import com.lhtstudio.kigtts.app.overlay.RealtimeOwnerGate
import com.lhtstudio.kigtts.app.overlay.RealtimeRuntimeBridge
import com.lhtstudio.kigtts.app.overlay.ListeningCaptionItem
import com.lhtstudio.kigtts.app.ui.ExternalQuickSubtitleRequest
import com.lhtstudio.kigtts.app.ui.RecognizedItem
import com.lhtstudio.kigtts.app.util.AppLogger
import com.lhtstudio.kigtts.app.util.BluetoothMediaTitleBridge
import com.lhtstudio.kigtts.app.util.LiveSubtitleNotificationBridge
import com.lhtstudio.kigtts.app.lan.LanCastRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class RealtimeHostState(
    val asrDir: File? = null,
    val voiceDir: File? = null,
    val running: Boolean = false,
    val status: String = "",
    val recognized: List<RecognizedItem> = emptyList(),
    val inputLevel: Float = 0f,
    val playbackProgress: Float = 0f,
    val inputDeviceLabel: String = "",
    val outputDeviceLabel: String = "",
    val pushToTalkPressed: Boolean = false,
    val pushToTalkStreamingText: String = "",
    val listeningEnabled: Boolean = false,
    val listeningItems: List<ListeningCaptionItem> = emptyList(),
    val listeningStreamingText: String = "",
    val listeningInputDeviceLabel: String = "",
    val aec3Status: String = "未启用",
    val aec3Diag: String = "AEC3 诊断：未启用",
    val speakerLastSimilarity: Float = -1f,
    val quickSubtitleRequestId: Long = 0L,
    val quickSubtitleText: String = "",
    val quickSubtitleConfigRevision: Long = 0L,
    val quickSubtitleConfigJson: String = ""
)

class RealtimeHostService : Service(), RealtimeRuntimeBridge.AppDelegate, LanCastRuntime.CommandHandler {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: ModelRepository

    private var controller: RealtimeController? = null
    private var settingsJob: Job? = null
    private var initializationJob: Job? = null
    private val listeningConfigurationMutex = Mutex()
    @Volatile
    private var currentSettings = UserPrefs.AppSettings()
    private var speakerProfiles = mutableListOf<UserPrefs.SpeakerVerifyProfile>()
    private val lastProgressUpdateAtMs = mutableMapOf<Long, Long>()
    private var lastLevelUpdateAtMs = 0L
    private var pttSessionLastText = ""
    private var pttSessionCommitConsumed = false
    @Volatile private var pttCommitRequested = false
    private var pttCommitJob: Job? = null
    @Volatile private var simplePttReleasePending = false
    private var simplePttReleaseJob: Job? = null
    private var lastPttHistoryTextKey = ""
    private var lastPttHistoryAtMs = 0L
    private var manualRecognizedIdSeed = -1L
    private val listeningIdAllocator = ListeningCaptionIdAllocator()
    private var listeningPreviewCommitJob: Job? = null
    private var listeningPreviewRevision = 0L
    private var fallbackCommittedListeningKey = ""
    private var quickSubtitlePlayOnSend = true
    private var committedQuickSubtitleText = ""
    private var lastQuickSubtitleRequestId = 0L
    private var lastQuickSubtitleConfigRevision = 0L
    @Volatile private var quickSubtitlePersistRevision = 0L

    private val _state = MutableStateFlow(RealtimeHostState())
    private val _quickSubtitleRequests = MutableStateFlow<ExternalQuickSubtitleRequest?>(null)

    private suspend fun ensureSpeakerBackend(settings: UserPrefs.AppSettings): Boolean {
        val outdated =
            settings.speakerVerifyBackendVersion != UserPrefs.SPEAKER_VERIFY_BACKEND_SHERPA_V1 &&
                    (settings.speakerVerifyEnabled || settings.speakerVerifyProfileCsv.isNotBlank())
        if (outdated) {
            UserPrefs.resetSpeakerVerifyBackend(applicationContext, enabled = false)
        }
        return outdated
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("RealtimeHostService.onCreate")
        repo = ModelRepository(applicationContext)
        RealtimeRuntimeBridge.registerAppDelegate(this)
        LanCastRuntime.registerCommandHandler(this)
        observeSettings()
        initializeSelections()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SUBMIT_QUICK_SUBTITLE -> {
                val target = intent.getStringExtra(EXTRA_QUICK_SUBTITLE_TARGET)
                    ?: OverlayBridge.TARGET_SUBTITLE
                val text = intent.getStringExtra(EXTRA_QUICK_SUBTITLE_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    serviceScope.launch {
                        initializationJob?.join()
                        submitQuickSubtitle(target, text)
                    }
                }
            }
            LiveSubtitleNotificationBridge.ACTION_PLAY_TEXT -> {
                val text = intent.getStringExtra(LiveSubtitleNotificationBridge.EXTRA_TEXT)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { committedQuickSubtitleText }
                if (text.isNotBlank()) {
                    serviceScope.launch {
                        val queued = speakText(text)
                        updateStatus(
                            if (queued != null) {
                                "已加入朗读队列"
                            } else if (currentSettings.ttsDisabled) {
                                "语音朗读已关闭"
                            } else {
                                "播放文本失败，请检查语音包"
                            }
                        )
                    }
                }
            }
            LiveSubtitleNotificationBridge.ACTION_DISABLE -> {
                currentSettings = currentSettings.copy(liveSubtitleNotificationEnabled = false)
                LiveSubtitleNotificationBridge.cancel(applicationContext)
                serviceScope.launch(Dispatchers.IO) {
                    UserPrefs.setLiveSubtitleNotificationEnabled(applicationContext, false)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        settingsJob = null
        initializationJob?.cancel()
        initializationJob = null
        listeningPreviewCommitJob?.cancel()
        listeningPreviewCommitJob = null
        RealtimeRuntimeBridge.unregisterAppDelegate(this)
        LanCastRuntime.unregisterCommandHandler(this)
        val activeController = controller
        controller = null
        if (activeController != null) {
            runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    activeController.stop()
                }
            }.onFailure {
                AppLogger.e("RealtimeHostService controller stop failed", it)
            }
        }
        RealtimeOwnerGate.release(APP_OWNER_TAG)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun stateFlow(): StateFlow<RealtimeHostState> = _state.asStateFlow()

    fun quickSubtitleRequestFlow(): StateFlow<ExternalQuickSubtitleRequest?> = _quickSubtitleRequests.asStateFlow()

    internal suspend fun prepareListeningSimulationForTest() {
        check(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "Listening simulation is available only in debug builds"
        }
        initializationJob?.join()
        val listening = currentSettings.listeningModeSettings.copy(enabled = true).normalized()
        currentSettings = currentSettings.copy(listeningModeSettings = listening)
        val activeController = ensureController()
        activeController.stopMic()
        val asrDir = currentState().asrDir ?: loadInitialAsrDir()
            ?: error("Recognition resource is not installed")
        check(activeController.loadAsr(asrDir)) { "Recognition resource failed to load" }
        activeController.setMinVolumePercent(listening.minVolumePercent)
        activeController.setDenoiserMode(listening.denoiserMode)
        activeController.setSpeechEnhancementMode(listening.speechEnhancementMode)
        activeController.setClassicVadEnabled(listening.classicVadEnabled)
        activeController.setSileroVadEnabled(listening.sileroVadEnabled)
        activeController.setSileroVadThreshold(listening.sileroVadThreshold)
        activeController.setSileroVadPreRollMs(listening.sileroVadPreRollMs)
        activeController.setMainRecognitionEnabled(false)
        activeController.setListeningCapturePaused(false)
        activeController.setListeningRecognitionEnabled(true, listening.recognitionLanguage)
        updateState {
            it.copy(
                asrDir = asrDir,
                running = false,
                listeningEnabled = true,
                listeningItems = emptyList(),
                listeningStreamingText = "",
                pushToTalkPressed = false,
                pushToTalkStreamingText = ""
            )
        }
    }

    internal suspend fun runSimulatedRecognitionForTest(
        samples: FloatArray,
        sourceSampleRate: Int,
        paceAsRealtime: Boolean,
        callbacksSynchronous: Boolean = true
    ): SimulatedAudioRunResult {
        check(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "Recognition simulation is available only in debug builds"
        }
        return ensureController().runSimulatedAudio(
            samples = samples,
            sourceSampleRate = sourceSampleRate,
            paceAsRealtime = paceAsRealtime,
            callbacksSynchronous = callbacksSynchronous
        )
    }

    fun publishQuickSubtitleConfig(json: String) {
        val normalized = json.trim()
        if (normalized.isEmpty()) return
        runCatching {
            JSONObject(normalized).optString("currentText", "").trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { text ->
            committedQuickSubtitleText = text
            syncLiveSubtitleNotification()
        }
        val revision = nextQuickSubtitleConfigRevision()
        updateState {
            it.copy(
                quickSubtitleConfigRevision = revision,
                quickSubtitleConfigJson = normalized
            )
        }
    }

    fun consumeQuickSubtitleRequest(requestId: Long) {
        if (_quickSubtitleRequests.value?.requestId == requestId) {
            _quickSubtitleRequests.value = null
        }
    }

    suspend fun updateSelectedAsrDir(dir: File?, status: String? = null, preload: Boolean = true) {
        updateState {
            it.copy(
                asrDir = dir,
                status = status ?: it.status
            )
        }
        if (preload && dir != null) {
            val loaded = withContext(Dispatchers.IO) { ensureController().loadAsr(dir) }
            if (!loaded && currentState().asrDir?.absolutePath == dir.absolutePath) {
                updateStatus("语音识别资源加载失败")
            }
        }
    }

    suspend fun updateSelectedVoiceDir(dir: File?, status: String? = null, preload: Boolean = true) {
        updateState {
            it.copy(
                voiceDir = dir,
                status = status ?: it.status
            )
        }
        if (preload && dir != null) {
            val loaded = withContext(Dispatchers.IO) { ensureController().loadTts(dir) }
            if (!loaded && currentState().voiceDir?.absolutePath == dir.absolutePath) {
                updateStatus(
                    if (isSystemTtsVoiceDir(dir)) {
                        "系统语音合成初始化失败，请先完成系统语音合成设置"
                    } else {
                        "音色包加载失败"
                    }
                )
            }
        }
    }

    suspend fun speakText(text: String, interruptCurrent: Boolean = false): Long? {
        val message = text.trim()
        if (message.isEmpty()) return null
        if (currentSettings.ttsDisabled) return null
        val voice = currentState().voiceDir ?: return null
        val activeController = ensureController()
        if (!activeController.isTtsReadyFor(voice)) {
            val queuedId = activeController.enqueueSpeakTextPendingTts(
                message,
                interruptCurrent = interruptCurrent
            ) ?: return null
            serviceScope.launch(Dispatchers.IO) {
                val loaded = activeController.loadTts(voice)
                if (!loaded && currentState().voiceDir?.absolutePath == voice.absolutePath) {
                    updateStatus(
                        if (isSystemTtsVoiceDir(voice)) {
                            "系统语音合成初始化失败，请先完成系统语音合成设置"
                        } else {
                            "音色包加载失败"
                        }
                    )
                }
            }
            return queuedId
        }
        return withContext(Dispatchers.IO) {
            activeController.enqueueSpeakText(message, interruptCurrent = interruptCurrent)
        }
    }

    fun recordRecognizedHistory(
        text: String,
        id: Long? = null,
        fromQuickText: Boolean = false
    ) {
        appendRecognizedHistory(text, id, fromQuickText)
    }

    suspend fun enrollSpeaker(
        durationSec: Float,
        onCapture: ((progress: Float, level: Float) -> Unit)? = null
    ): SpeakerEnrollResult {
        return withContext(Dispatchers.IO) {
            ensureController().enrollSpeaker(durationSec) { progress, level ->
                if (onCapture != null) {
                    serviceScope.launch(Dispatchers.Main.immediate) {
                        onCapture(progress, level)
                    }
                }
            }
        }
    }

    fun isMicActive(): Boolean = controller?.isMicActive() == true

    fun setQuickSubtitlePlayOnSend(enabled: Boolean) {
        quickSubtitlePlayOnSend = enabled
    }

    fun setTtsDisabled(enabled: Boolean) {
        currentSettings = currentSettings.copy(ttsDisabled = enabled)
        controller?.setSuppressAsrAutoSpeak(
            enabled || (currentSettings.pushToTalkMode && currentSettings.pushToTalkConfirmInput)
        )
    }

    fun setSuppressWhilePlaying(enabled: Boolean) {
        controller?.setSuppressWhilePlaying(enabled)
    }

    fun setSuppressDelaySec(seconds: Float) {
        controller?.setSuppressDelaySec(seconds)
    }

    fun setMinVolumePercent(percent: Int) {
        controller?.setMinVolumePercent(percent)
    }

    fun setPlaybackGainPercent(percent: Int) {
        controller?.setPlaybackGainPercent(percent)
        SoundboardManager.setPlaybackGainPercent(percent)
    }

    fun setAudioFocusAvoidanceMode(mode: Int) {
        controller?.setAudioFocusAvoidanceMode(mode)
        SoundboardManager.setAudioFocusAvoidanceMode(applicationContext, mode)
    }

    fun setPiperNoiseScale(value: Float) {
        controller?.setPiperNoiseScale(value)
    }

    fun setPiperLengthScale(value: Float) {
        controller?.setPiperLengthScale(value)
    }

    fun setPiperNoiseW(value: Float) {
        controller?.setPiperNoiseW(value)
    }

    fun setPiperSentenceSilenceSec(value: Float) {
        controller?.setPiperSentenceSilenceSec(value)
    }

    fun setKokoroSpeakerId(value: Int) {
        controller?.setKokoroSpeakerId(value)
    }

    fun setUseVoiceCommunication(enabled: Boolean) {
        controller?.setUseVoiceCommunication(enabled)
    }

    fun setCommunicationMode(enabled: Boolean) {
        controller?.setCommunicationMode(enabled)
    }

    fun setPreferredInputType(type: Int) {
        controller?.setPreferredInputType(type)
    }

    fun setPreferredOutputType(type: Int) {
        controller?.setPreferredOutputType(type)
    }

    fun setUseAec3(enabled: Boolean) {
        controller?.setUseAec3(enabled)
    }

    fun setDenoiserMode(mode: Int) {
        controller?.setDenoiserMode(mode)
    }

    fun setSpeechEnhancementMode(mode: Int) {
        controller?.setSpeechEnhancementMode(mode)
    }

    fun setClassicVadEnabled(enabled: Boolean) {
        controller?.setClassicVadEnabled(enabled)
    }

    fun setSileroVadEnabled(enabled: Boolean) {
        controller?.setSileroVadEnabled(enabled)
    }

    fun setSileroVadThreshold(threshold: Float) {
        currentSettings = currentSettings.copy(
            sileroVadThreshold = threshold.coerceIn(
                UserPrefs.SILERO_VAD_MIN_THRESHOLD,
                UserPrefs.SILERO_VAD_MAX_THRESHOLD
            )
        )
        controller?.setSileroVadThreshold(currentSettings.sileroVadThreshold)
    }

    fun setSileroVadPreRollMs(preRollMs: Int) {
        currentSettings = currentSettings.copy(
            sileroVadPreRollMs = preRollMs.coerceIn(
                UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS,
                UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS
            )
        )
        controller?.setSileroVadPreRollMs(currentSettings.sileroVadPreRollMs)
    }

    fun setAsrRecognitionLanguage(language: String) {
        val normalized = AsrRecognitionLanguage.normalize(language)
        currentSettings = currentSettings.copy(asrRecognitionLanguage = normalized)
        serviceScope.launch(Dispatchers.IO) {
            controller?.setAsrRecognitionLanguage(normalized)
        }
    }

    fun setListeningModeEnabled(enabled: Boolean) {
        val next = currentSettings.listeningModeSettings.copy(enabled = enabled).normalized()
        currentSettings = currentSettings.copy(listeningModeSettings = next)
        updateState {
            it.copy(
                listeningEnabled = enabled,
                listeningStreamingText = if (enabled) it.listeningStreamingText else ""
            )
        }
        if (!enabled) {
            cancelListeningPreviewCommit()
            fallbackCommittedListeningKey = ""
            finishSimplePttRelease()
        }
        synchronizeRecognitionOwnership()
        serviceScope.launch {
            configureListeningRecognition(next)
            if (currentSettings.listeningModeSettings.enabled) {
                startRealtimeInternal()
            } else if (currentSettings.pushToTalkMode && !currentState().pushToTalkPressed) {
                stopRealtimeInternal()
            }
        }
    }

    fun updateListeningModeSettings(settings: ListeningModeSettings) {
        val normalized = settings.normalized()
        val previous = currentSettings.listeningModeSettings
        currentSettings = currentSettings.copy(listeningModeSettings = normalized)
        updateState { it.copy(listeningEnabled = normalized.enabled) }
        if (listeningEngineSettingsChanged(previous, normalized)) {
            serviceScope.launch { configureListeningRecognition(normalized) }
        }
    }

    fun setSpeechButtonActionMode(value: Int) {
        val mode = SpeechButtonActionMode.normalize(value)
        val pushToTalk = SpeechButtonActionMode.usesPushToTalk(mode)
        val confirm = SpeechButtonActionMode.usesConfirmation(mode)
        currentSettings = currentSettings.copy(
            speechButtonActionMode = mode,
            pushToTalkMode = pushToTalk,
            pushToTalkConfirmInput = confirm
        )
        if (!pushToTalk) {
            pttCommitJob?.cancel()
            pttCommitJob = null
            pttCommitRequested = false
            finishSimplePttRelease()
            updateState { it.copy(pushToTalkPressed = false, pushToTalkStreamingText = "") }
        }
        controller?.setSuppressAsrAutoSpeak(currentSettings.ttsDisabled || (pushToTalk && confirm))
        synchronizeRecognitionOwnership()
        AppLogger.i("Speech button mode synchronized mode=$mode")
    }

    fun setPushToTalkStreamingEnabled(enabled: Boolean) {
        controller?.setPushToTalkStreamingEnabled(enabled)
    }

    fun setSuppressAsrAutoSpeak(enabled: Boolean) {
        controller?.setSuppressAsrAutoSpeak(enabled)
    }

    fun setSpeakerVerifyEnabled(enabled: Boolean) {
        controller?.setSpeakerVerifyEnabled(enabled)
    }

    fun setSpeakerVerifyThreshold(threshold: Float) {
        setSpeakerVerifyTolerance(SpeakerVerificationTolerance.fromThreshold(threshold).index)
    }

    fun setSpeakerVerifyTolerance(level: Int) {
        val tolerance = SpeakerVerificationTolerance.fromIndex(level)
        currentSettings = currentSettings.copy(
            speakerVerifyThreshold = tolerance.primaryThreshold,
            speakerVerifyToleranceLevel = tolerance.index
        )
        controller?.setSpeakerVerifyTolerance(tolerance.index)
    }

    fun setExperimentalRecognitionSensitivity(sensitivity: Int) {
        controller?.setExperimentalRecognitionSensitivity(sensitivity)
    }

    fun setExperimentalTargetSpeakerBackend(backend: Int) {
        controller?.setExperimentalTargetSpeakerBackend(backend)
    }

    fun getSpeakerLastSimilarity(): Float {
        val controllerSimilarity = controller?.latestSpeakerSimilarity() ?: -1f
        return if (controllerSimilarity >= 0f) controllerSimilarity else currentState().speakerLastSimilarity
    }

    fun setSpeakerProfiles(
        profiles: List<FloatArray>,
        neuralProfiles: List<FloatArray?> = emptyList(),
        confirmationProfiles: List<FloatArray?> = emptyList()
    ) {
        controller?.setSpeakerProfiles(profiles, neuralProfiles, confirmationProfiles)
        updateState { it.copy(speakerLastSimilarity = -1f) }
    }

    fun releaseNeuralSpeakerFilterResources() {
        controller?.releaseNeuralSpeakerFilterResources()
    }

    fun clearSpeakerProfiles() {
        controller?.clearSpeakerProfiles()
        updateState { it.copy(speakerLastSimilarity = -1f) }
    }

    suspend fun restartRecorder() {
        withContext(Dispatchers.IO) {
            controller?.restartRecorder()
        }
    }

    suspend fun stopForVoicePackDeletion() {
        withContext(Dispatchers.IO) {
            controller?.stopMic()
        }
        RealtimeOwnerGate.release(APP_OWNER_TAG)
        KeepAliveService.stop(applicationContext)
        lastProgressUpdateAtMs.clear()
        lastLevelUpdateAtMs = 0L
        pttSessionLastText = ""
        pttSessionCommitConsumed = false
        updateState {
            it.copy(
                running = false,
                status = "当前语音包已删除，麦克风已停止",
                inputLevel = 0f,
                playbackProgress = 0f,
                pushToTalkPressed = false,
                pushToTalkStreamingText = ""
            )
        }
    }

    override fun startRealtime() {
        serviceScope.launch {
            startRealtimeInternal()
        }
    }

    override fun stopRealtime() {
        serviceScope.launch {
            stopRealtimeInternal()
        }
    }

    override fun submitQuickSubtitle(target: String, text: String) {
        val normalized = text.trim()
        if (!isOverlayOpenTarget(target) && normalized.isEmpty()) return
        when (target) {
            OverlayBridge.TARGET_INPUT -> {
                if (normalized.isNotEmpty()) {
                    appendRecognizedHistory(normalized, fromQuickText = true)
                }
            }
            OverlayBridge.TARGET_SUBTITLE -> {
                if (normalized.isNotEmpty()) {
                    if (quickSubtitlePlayOnSend && !currentSettings.ttsDisabled) {
                        serviceScope.launch {
                            enqueueSpeakAndAppendHistory(
                                normalized,
                                fromQuickText = true,
                                interruptCurrent = currentSettings.quickSubtitleInterruptQueue
                            )
                        }
                    } else {
                        appendRecognizedHistory(normalized, fromQuickText = true)
                    }
                }
            }
        }
        emitQuickSubtitleRequest(target, normalized, navigateToPage = false)
    }

    override fun submitSubtitle(text: String, playVoice: Boolean) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        serviceScope.launch {
            if (playVoice && !currentSettings.ttsDisabled) {
                enqueueSpeakAndAppendHistory(
                    normalized,
                    fromQuickText = true,
                    interruptCurrent = currentSettings.quickSubtitleInterruptQueue
                )
            } else {
                appendRecognizedHistory(normalized, fromQuickText = true)
            }
            emitQuickSubtitleRequest(
                OverlayBridge.TARGET_SUBTITLE,
                normalized,
                navigateToPage = false
            )
        }
    }

    override fun clearSubtitle() {
        emitQuickSubtitleRequest(
            OverlayBridge.TARGET_SUBTITLE,
            currentSettings.quickSubtitleClearedPlaceholderText,
            navigateToPage = false
        )
    }

    override fun replaySubtitle(text: String) {
        serviceScope.launch {
            val queued = speakText(text, interruptCurrent = currentSettings.quickSubtitleInterruptQueue)
            updateStatus(if (queued != null) "已加入朗读队列" else "播放文本失败，请检查语音合成设置")
        }
    }

    override fun setRealtimeRunning(running: Boolean) {
        if (running) startRealtime() else stopRealtime()
    }

    override fun openApp() {
        startActivity(
            OverlayBridge.buildOpenPageIntent(
                applicationContext,
                OverlayBridge.TARGET_OPEN_LAN_CAST
            )
        )
    }

    private fun emitQuickSubtitleRequest(
        target: String,
        text: String,
        navigateToPage: Boolean = false
    ) {
        val normalized = text.trim()
        val request = ExternalQuickSubtitleRequest(
            requestId = nextQuickSubtitleRequestId(),
            target = target,
            text = normalized,
            navigateToPage = navigateToPage
        )
        _quickSubtitleRequests.value = request
        if (target == OverlayBridge.TARGET_SUBTITLE) {
            LanCastRuntime.updateSubtitleText(normalized)
            updateState {
                it.copy(
                    quickSubtitleRequestId = request.requestId,
                    quickSubtitleText = normalized
                )
            }
            syncBluetoothMediaTitleToCommittedQuickSubtitle(normalized)
            persistCommittedQuickSubtitleTextAsync(normalized)
        }
    }

    private fun nextQuickSubtitleRequestId(): Long {
        val now = SystemClock.uptimeMillis()
        val next = if (now > lastQuickSubtitleRequestId) now else lastQuickSubtitleRequestId + 1L
        lastQuickSubtitleRequestId = next
        return next
    }

    private fun nextQuickSubtitleConfigRevision(): Long {
        val now = SystemClock.uptimeMillis()
        val next =
            if (now > lastQuickSubtitleConfigRevision) now else lastQuickSubtitleConfigRevision + 1L
        lastQuickSubtitleConfigRevision = next
        return next
    }

    private fun persistCommittedQuickSubtitleTextAsync(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val revision = ++quickSubtitlePersistRevision
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val root = UserPrefs.getQuickSubtitleConfig(applicationContext)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject()) }
                    ?: JSONObject()
                root.put("currentText", normalized)
                if (revision == quickSubtitlePersistRevision) {
                    UserPrefs.setQuickSubtitleConfig(applicationContext, root.toString())
                }
            }.onFailure {
                AppLogger.e("RealtimeHostService.persistCommittedQuickSubtitleText failed", it)
            }
        }
    }

    override fun beginPushToTalkSession() {
        pttCommitJob?.cancel()
        pttCommitJob = null
        pttSessionLastText = ""
        pttSessionCommitConsumed = false
        pttCommitRequested = false
        resetPttHistoryDedup()
        updateState { it.copy(pushToTalkStreamingText = "") }
    }

    override fun setPushToTalkPressed(pressed: Boolean) {
        val wasPressed = currentState().pushToTalkPressed
        if (pressed) {
            finishSimplePttRelease()
            if (currentSettings.listeningModeSettings.enabled) {
                commitListeningCaption(0L, "", "fab-handoff")
            }
        }
        updateState {
            it.copy(
                pushToTalkPressed = pressed,
                pushToTalkStreamingText = if (pressed || pttCommitRequested) {
                    it.pushToTalkStreamingText
                } else {
                    ""
                }
            )
        }
        synchronizeRecognitionOwnership()
        if (
            !pressed &&
            wasPressed &&
            currentSettings.listeningModeSettings.enabled &&
            currentSettings.pushToTalkMode &&
            !currentSettings.pushToTalkConfirmInput
        ) {
            simplePttReleasePending = true
            synchronizeRecognitionOwnership()
            simplePttReleaseJob?.cancel()
            simplePttReleaseJob = serviceScope.launch {
                controller?.flushPendingRecognition()
                delay(SIMPLE_PTT_RELEASE_GRACE_MS)
                finishSimplePttRelease()
            }
            return
        }
        synchronizeRecognitionOwnership()
    }

    override fun commitPushToTalkSession(action: RealtimeRuntimeBridge.PttCommitAction) {
        if (!currentSettings.pushToTalkConfirmInput) return
        if (pttSessionCommitConsumed || pttCommitRequested) return
        pttCommitRequested = true
        synchronizeRecognitionOwnership()
        if (action == RealtimeRuntimeBridge.PttCommitAction.Cancel) {
            finalizePushToTalkCommit(action)
            return
        }
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            controller?.flushPendingRecognition()
            finalizePushToTalkCommit(action)
        }
        pttCommitJob = job
        job.start()
    }

    private fun finalizePushToTalkCommit(action: RealtimeRuntimeBridge.PttCommitAction) {
        if (pttSessionCommitConsumed) return
        pttSessionCommitConsumed = true
        val text = currentState().pushToTalkStreamingText.trim().ifBlank { pttSessionLastText.trim() }
        when (action) {
            RealtimeRuntimeBridge.PttCommitAction.SendToSubtitle -> {
                if (text.isNotEmpty()) {
                    if (!quickSubtitlePlayOnSend || currentSettings.ttsDisabled) {
                        appendRecognizedHistory(text)
                    } else {
                        serviceScope.launch {
                            enqueueSpeakAndAppendHistory(text)
                        }
                    }
                    emitQuickSubtitleRequest(
                        OverlayBridge.TARGET_SUBTITLE,
                        text,
                        navigateToPage = false
                    )
                }
            }
            RealtimeRuntimeBridge.PttCommitAction.SendToInput -> {
                if (text.isNotEmpty()) {
                    appendRecognizedHistory(text)
                    emitQuickSubtitleRequest(
                        OverlayBridge.TARGET_INPUT,
                        text,
                        navigateToPage = false
                    )
                }
            }
            RealtimeRuntimeBridge.PttCommitAction.Cancel -> Unit
        }
        pttSessionLastText = ""
        pttCommitRequested = false
        resetPttHistoryDedup()
        updateState {
            it.copy(
                pushToTalkPressed = false,
                pushToTalkStreamingText = ""
            )
        }
        synchronizeRecognitionOwnership()
    }

    private fun finishSimplePttRelease() {
        simplePttReleasePending = false
        simplePttReleaseJob?.cancel()
        simplePttReleaseJob = null
        synchronizeRecognitionOwnership()
    }

    private fun cancelListeningPreviewCommit() {
        listeningPreviewRevision++
        listeningPreviewCommitJob?.cancel()
        listeningPreviewCommitJob = null
    }

    private fun scheduleListeningPreviewCommit() {
        val revision = ++listeningPreviewRevision
        listeningPreviewCommitJob?.cancel()
        listeningPreviewCommitJob = serviceScope.launch {
            delay(LISTENING_PREVIEW_STABLE_COMMIT_MS)
            if (
                revision == listeningPreviewRevision &&
                currentSettings.listeningModeSettings.enabled &&
                !currentState().pushToTalkPressed
            ) {
                commitListeningCaption(0L, "", "stable-preview")
            }
        }
    }

    private fun commitListeningCaption(id: Long, finalText: String, reason: String) {
        cancelListeningPreviewCommit()
        var committedLength = 0
        updateState { state ->
            val finalized = PttTranscriptMerger.finalizeListeningCaption(
                state.listeningStreamingText,
                finalText.trim()
            )
            committedLength = finalized.length
            val finalizedKey = listeningComparisonKey(finalized)
            val consumesFallback =
                reason == "engine-final" &&
                    finalizedKey.isNotEmpty() &&
                    PttTranscriptMerger.isSameRollingUtterance(
                        finalizedKey,
                        fallbackCommittedListeningKey
                    ) &&
                    state.listeningStreamingText.isBlank() &&
                    state.listeningItems.isNotEmpty()
            val nextItems = if (consumesFallback) {
                state.listeningItems.dropLast(1) +
                    state.listeningItems.last().copy(text = finalized)
            } else if (finalized.isNotEmpty()) {
                val itemId = listeningIdAllocator.allocate(
                    preferredId = id,
                    existingIds = state.listeningItems.mapTo(mutableSetOf()) { it.id }
                )
                state.listeningItems + ListeningCaptionItem(
                    id = itemId,
                    text = finalized
                )
            } else {
                state.listeningItems
            }
            fallbackCommittedListeningKey = if (reason == "stable-preview") {
                finalizedKey
            } else {
                ""
            }
            state.copy(
                listeningItems = nextItems.takeLast(MAX_LISTENING_ITEMS),
                listeningStreamingText = ""
            )
        }
        if (committedLength > 0) {
            AppLogger.i("Listening caption committed reason=$reason chars=$committedLength")
        }
    }

    private fun updateListeningPreviewTranscript(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val incomingKey = listeningComparisonKey(normalized)
        if (
            incomingKey.isNotEmpty() &&
            PttTranscriptMerger.isSameRollingUtterance(
                incomingKey,
                fallbackCommittedListeningKey
            )
        ) {
            return
        }
        if (incomingKey != fallbackCommittedListeningKey) {
            fallbackCommittedListeningKey = ""
        }
        val current = currentState().listeningStreamingText
        val preview = PttTranscriptMerger.updateListeningPreview(current, normalized)
        if (preview == current) return
        updateState { it.copy(listeningStreamingText = preview) }
        scheduleListeningPreviewCommit()
    }

    private fun listeningComparisonKey(text: String): String = buildString(text.length) {
        text.forEach { character ->
            if (!character.isWhitespace() && character !in LISTENING_COMPARISON_PUNCTUATION) {
                append(character)
            }
        }
    }

    private fun synchronizeRecognitionOwnership() {
        val activeController = controller ?: return
        val listeningEnabled = currentSettings.listeningModeSettings.enabled
        val fabOwnsRecognition =
            currentState().pushToTalkPressed || pttCommitRequested || simplePttReleasePending
        if (fabOwnsRecognition) {
            activeController.setListeningCapturePaused(listeningEnabled)
            activeController.setMainRecognitionEnabled(true)
            activeController.setPushToTalkStreamingEnabled(
                currentSettings.pushToTalkMode && currentSettings.pushToTalkConfirmInput
            )
        } else {
            activeController.setPushToTalkStreamingEnabled(false)
            activeController.setMainRecognitionEnabled(!listeningEnabled)
            activeController.setListeningCapturePaused(false)
        }
    }

    private fun initializeSelections() {
        initializationJob = serviceScope.launch {
            val settings = UserPrefs.getSettings(applicationContext)
            currentSettings = settings
            committedQuickSubtitleText = loadCommittedQuickSubtitleText(applyStartupPolicy = true)
            BluetoothMediaTitleBridge.setEnabled(
                applicationContext,
                settings.bluetoothMediaTitleSubtitle,
                committedQuickSubtitleText
            )
            syncBluetoothMediaTitleToCommittedQuickSubtitleConfig()
            val resetBackend = ensureSpeakerBackend(settings)
            speakerProfiles = if (resetBackend) {
                mutableListOf()
            } else {
                UserPrefs.parseSpeakerVerifyProfiles(settings.speakerVerifyProfileCsv).toMutableList()
            }
            quickSubtitlePlayOnSend = loadQuickSubtitlePlayOnSend()
            val asrDir = loadInitialAsrDir()
            val voiceDir = loadInitialVoiceDir()
            updateState {
                it.copy(
                    asrDir = asrDir,
                    voiceDir = voiceDir,
                    status = buildList {
                        if (asrDir != null) add("已加载语音识别资源")
                        if (voiceDir != null) add(if (isSystemTtsVoiceDir(voiceDir)) "已加载系统语音合成" else "已加载音色包")
                    }.joinToString(" / ")
                )
            }
            syncLiveSubtitleNotification()
            if (asrDir != null) {
                withContext(Dispatchers.IO) {
                    ensureController().loadAsr(asrDir)
                }
            }
            if (voiceDir != null) {
                val loaded = withContext(Dispatchers.IO) {
                    ensureController().loadTts(voiceDir)
                }
                if (!loaded) {
                    updateStatus(
                        if (isSystemTtsVoiceDir(voiceDir)) {
                            "系统语音合成初始化失败，请先完成系统语音合成设置"
                        } else {
                            "音色包加载失败"
                        }
                    )
                }
            }
            if (settings.listeningModeSettings.enabled && asrDir != null) {
                updateState { it.copy(listeningEnabled = true) }
                configureListeningRecognition(settings.listeningModeSettings)
                startRealtimeInternal()
            }
        }
    }

    private suspend fun loadQuickSubtitlePlayOnSend(): Boolean {
        return runCatching {
            val raw = UserPrefs.getQuickSubtitleConfig(applicationContext)
            if (raw.isNullOrBlank()) true else JSONObject(raw).optBoolean("playOnSend", true)
        }.getOrDefault(true)
    }

    private suspend fun loadCommittedQuickSubtitleText(
        applyStartupPolicy: Boolean = false
    ): String {
        return runCatching {
            val raw = UserPrefs.getQuickSubtitleConfig(applicationContext)
            val savedText = raw
                ?.takeIf { it.isNotBlank() }
                ?.let { JSONObject(it).optString("currentText", "") }
            if (applyStartupPolicy) {
                resolveQuickSubtitleStartupText(
                    savedText = savedText,
                    clearedPlaceholderText = currentSettings.quickSubtitleClearedPlaceholderText,
                    restoreLastTextOnLaunch =
                        currentSettings.quickSubtitleRestoreLastTextOnLaunch
                )
            } else {
                savedText
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: currentSettings.quickSubtitleClearedPlaceholderText
            }
        }.getOrDefault(currentSettings.quickSubtitleClearedPlaceholderText)
    }

    private suspend fun syncBluetoothMediaTitleToCommittedQuickSubtitleConfig() {
        syncLiveSubtitleNotification()
        if (!currentSettings.bluetoothMediaTitleSubtitle) return
        if (committedQuickSubtitleText.isNotEmpty()) {
            BluetoothMediaTitleBridge.updateSubtitle(applicationContext, committedQuickSubtitleText)
        }
    }

    private fun syncBluetoothMediaTitleToCommittedQuickSubtitle(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        committedQuickSubtitleText = normalized
        syncLiveSubtitleNotification()
        if (currentSettings.bluetoothMediaTitleSubtitle) {
            BluetoothMediaTitleBridge.updateSubtitle(applicationContext, normalized)
        }
    }

    private fun syncLiveSubtitleNotification() {
        LiveSubtitleNotificationBridge.update(
            applicationContext,
            currentSettings.liveSubtitleNotificationEnabled,
            committedQuickSubtitleText,
            currentState().status
        )
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            UserPrefs.observeSettings(this@RealtimeHostService).collectLatest { next ->
                val previous = currentSettings
                currentSettings = next
                if (
                    committedQuickSubtitleText == previous.quickSubtitleClearedPlaceholderText &&
                    next.quickSubtitleClearedPlaceholderText !=
                        previous.quickSubtitleClearedPlaceholderText
                ) {
                    committedQuickSubtitleText = next.quickSubtitleClearedPlaceholderText
                }
                SoundboardManager.setPlaybackGainPercent(next.playbackGainPercent)
                SoundboardManager.setAudioFocusAvoidanceMode(applicationContext, next.audioFocusAvoidanceMode)
                SoundboardManager.setInterruptOnNewPlayback(next.soundboardInterruptOnNewPlayback)
                BluetoothMediaTitleBridge.setEnabled(
                    applicationContext,
                    next.bluetoothMediaTitleSubtitle,
                    committedQuickSubtitleText
                )
                if (next.bluetoothMediaTitleSubtitle) {
                    syncBluetoothMediaTitleToCommittedQuickSubtitleConfig()
                } else {
                    syncLiveSubtitleNotification()
                }
                val resetBackend = ensureSpeakerBackend(next)
                speakerProfiles = if (resetBackend) {
                    mutableListOf()
                } else {
                    UserPrefs.parseSpeakerVerifyProfiles(next.speakerVerifyProfileCsv).toMutableList()
                }
                applySettingsToController(next)
                if (previous.listeningModeSettings != next.listeningModeSettings) {
                    if (
                        listeningEngineSettingsChanged(
                            previous.listeningModeSettings,
                            next.listeningModeSettings
                        )
                    ) {
                        configureListeningRecognition(next.listeningModeSettings)
                    }
                    updateState {
                        it.copy(
                            listeningEnabled = next.listeningModeSettings.enabled,
                            listeningStreamingText = if (next.listeningModeSettings.enabled) {
                                it.listeningStreamingText
                            } else {
                                ""
                            }
                        )
                    }
                }
                if (
                    controller != null &&
                    previous.asrRecognitionLanguage != next.asrRecognitionLanguage
                ) {
                    withContext(Dispatchers.IO) {
                        controller?.setAsrRecognitionLanguage(next.asrRecognitionLanguage)
                    }
                }
                if (
                    controller != null &&
                    (previous.echoSuppression != next.echoSuppression ||
                        previous.communicationMode != next.communicationMode ||
                        previous.preferredInputType != next.preferredInputType ||
                        previous.preferredOutputType != next.preferredOutputType)
                ) {
                    withContext(Dispatchers.IO) {
                        controller?.restartRecorder()
                    }
                }
                if (currentState().running) {
                    if (next.keepAlive) KeepAliveService.start(applicationContext)
                    else KeepAliveService.stop(applicationContext)
                }
            }
        }
    }

    @Synchronized
    private fun ensureController(): RealtimeController {
        controller?.let { return it }
        val created = RealtimeController(
            applicationContext,
            serviceScope,
            onResult = { id, text ->
                val normalized = text.trim()
                val isPttConfirmMode = currentSettings.pushToTalkMode && currentSettings.pushToTalkConfirmInput
                val isPttConfirmSessionOpen = isPttConfirmMode && !pttSessionCommitConsumed
                val acceptsPttResult = isPttConfirmSessionOpen &&
                    (currentState().pushToTalkPressed || pttCommitRequested)
                val acceptsSimplePttRelease = simplePttReleasePending
                if (
                    !isPttConfirmMode &&
                    normalized.isNotEmpty() &&
                    (
                        !currentSettings.listeningModeSettings.enabled ||
                            currentState().pushToTalkPressed ||
                            acceptsSimplePttRelease
                        )
                ) {
                    appendRecognizedHistory(normalized, id)
                    if (currentSettings.asrSendToQuickSubtitle) {
                        emitQuickSubtitleRequest(
                            OverlayBridge.TARGET_SUBTITLE,
                            normalized,
                            navigateToPage = false
                        )
                    }
                    if (acceptsSimplePttRelease) finishSimplePttRelease()
                }
                if (isPttConfirmMode) {
                    if (acceptsPttResult && normalized.isNotEmpty()) {
                        appendPttFinalTranscript(normalized)
                    }
                }
            },
            onStreamingResult = { text ->
                val normalized = text.trim()
                if (normalized.isEmpty()) return@RealtimeController
                if (
                    currentSettings.pushToTalkMode &&
                    currentSettings.pushToTalkConfirmInput &&
                    !pttSessionCommitConsumed &&
                    (currentState().pushToTalkPressed || pttCommitRequested)
                ) {
                    updatePttPreviewTranscript(normalized)
                }
            },
            onListeningResult = { id, text ->
                serviceScope.launch {
                    if (currentSettings.listeningModeSettings.enabled) {
                        commitListeningCaption(id, text, "engine-final")
                    }
                }
            },
            onListeningStreamingResult = { text ->
                val normalized = text.trim()
                serviceScope.launch {
                    if (normalized.isNotEmpty() && currentSettings.listeningModeSettings.enabled) {
                        updateListeningPreviewTranscript(normalized)
                    }
                }
            },
            onProgress = { id, progress ->
                if (progress >= 0.99f) {
                    BluetoothMediaTitleBridge.extendAfterPlaybackEnd(applicationContext)
                }
                val items = currentState().recognized
                val idx = items.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val current = items[idx]
                    val nextProgress = maxOf(current.progress, progress.coerceIn(0f, 1f))
                    val progressDelta = nextProgress - current.progress
                    val now = SystemClock.elapsedRealtime()
                    val last = lastProgressUpdateAtMs[id] ?: 0L
                    val intervalReady = (now - last) >= PROGRESS_UPDATE_INTERVAL_MS || nextProgress >= 0.99f
                    if (progressDelta >= PROGRESS_UPDATE_DELTA && intervalReady) {
                        lastProgressUpdateAtMs[id] = now
                        val next = items.toMutableList()
                        next[idx] = current.copy(progress = nextProgress)
                        updateState {
                            it.copy(
                                recognized = next,
                                playbackProgress = progress.coerceIn(0f, 1f)
                            )
                        }
                    } else {
                        updateState { it.copy(playbackProgress = progress.coerceIn(0f, 1f)) }
                    }
                } else {
                    updateState { it.copy(playbackProgress = progress.coerceIn(0f, 1f)) }
                }
            },
            onLevel = { level ->
                val next = level.coerceIn(0f, 1f)
                val now = SystemClock.elapsedRealtime()
                val prev = currentState().inputLevel
                val delta = abs(prev - next)
                val intervalReady = (now - lastLevelUpdateAtMs) >= LEVEL_UPDATE_INTERVAL_MS
                if (delta >= LEVEL_UPDATE_DELTA || intervalReady) {
                    lastLevelUpdateAtMs = now
                    updateState { it.copy(inputLevel = next) }
                }
            },
            onInputDevice = { label ->
                if (label != currentState().inputDeviceLabel) {
                    updateState {
                        it.copy(
                            inputDeviceLabel = label,
                            listeningInputDeviceLabel = if (it.listeningEnabled) label else it.listeningInputDeviceLabel
                        )
                    }
                }
            },
            onOutputDevice = { label ->
                if (label != currentState().outputDeviceLabel) {
                    updateState { it.copy(outputDeviceLabel = label) }
                }
            },
            onAec3Status = { status ->
                if (status != currentState().aec3Status) {
                    updateState { it.copy(aec3Status = status) }
                }
            },
            onAec3Diag = { diag ->
                if (diag != currentState().aec3Diag) {
                    updateState { it.copy(aec3Diag = diag) }
                }
            },
            onSpeakerVerify = { similarity, passed ->
                updateState { it.copy(speakerLastSimilarity = similarity) }
                if (!passed && currentSettings.speakerVerifyEnabled) {
                    updateStatus("说话人验证未通过(${String.format("%.2f", similarity)})")
                }
            },
            onStatus = { msg ->
                updateState { state ->
                    if (state.running) state.copy(status = msg) else state
                }
            },
            onError = { msg ->
                updateState {
                    it.copy(
                        running = false,
                        status = msg
                    )
                }
            },
            initialSuppressWhilePlaying = currentSettings.muteWhilePlaying,
            initialUseVoiceCommunication = currentSettings.echoSuppression,
            initialCommunicationMode = currentSettings.communicationMode,
            initialMinVolumePercent = currentSettings.minVolumePercent,
            initialPlaybackGainPercent = currentSettings.playbackGainPercent,
            initialAudioFocusAvoidanceMode = currentSettings.audioFocusAvoidanceMode,
            initialPiperNoiseScale = currentSettings.piperNoiseScale,
            initialPiperLengthScale = currentSettings.piperLengthScale,
            initialPiperNoiseW = currentSettings.piperNoiseW,
            initialPiperSentenceSilenceSec = currentSettings.piperSentenceSilence,
            initialKokoroSpeakerId = currentSettings.kokoroSpeakerId,
            initialSuppressDelaySec = currentSettings.muteWhilePlayingDelaySec,
            initialPreferredInputType = currentSettings.preferredInputType,
            initialPreferredOutputType = currentSettings.preferredOutputType,
            initialUseAec3 = currentSettings.aec3Enabled,
            initialDenoiserMode = currentSettings.denoiserMode,
            initialSpeechEnhancementMode = currentSettings.speechEnhancementMode,
            initialClassicVadEnabled = currentSettings.classicVadEnabled,
            initialSileroVadEnabled = currentSettings.sileroVadEnabled,
            initialSileroVadThreshold = currentSettings.sileroVadThreshold,
            initialSileroVadPreRollMs = currentSettings.sileroVadPreRollMs,
            initialAsrRecognitionLanguage = currentSettings.asrRecognitionLanguage,
            initialAllowSystemAecWithAec3 = currentSettings.allowSystemAecWithAec3,
            initialSpeakerVerifyEnabled = currentSettings.speakerVerifyEnabled && speakerProfiles.isNotEmpty(),
            initialSpeakerVerifyThreshold = currentSettings.speakerVerifyThreshold,
            initialSpeakerVerifyToleranceLevel = currentSettings.speakerVerifyToleranceLevel,
            initialExperimentalRecognitionSensitivity =
                currentSettings.experimentalRecognitionSensitivity,
            initialExperimentalTargetSpeakerBackend =
                currentSettings.experimentalTargetSpeakerBackend,
            initialSpeakerProfiles = speakerProfiles.map { it.vector.copyOf() },
            initialNeuralSpeakerProfiles = speakerProfiles.map { it.neuralVector?.copyOf() },
            initialConfirmationSpeakerProfiles =
                speakerProfiles.map { it.confirmationVector?.copyOf() },
            shouldSuppressAutoSpeakForText = { text ->
                currentSettings.soundboardKeywordTriggerEnabled &&
                    currentSettings.soundboardSuppressTtsOnKeyword &&
                    SoundboardManager.hasTriggerMatch(applicationContext, text)
            }
        )
        created.setPushToTalkStreamingEnabled(
            currentSettings.pushToTalkMode &&
                currentSettings.pushToTalkConfirmInput &&
                (
                    currentState().pushToTalkPressed ||
                        pttCommitRequested ||
                        simplePttReleasePending
                    )
        )
        created.setSuppressAsrAutoSpeak(
            currentSettings.ttsDisabled ||
                (currentSettings.pushToTalkMode && currentSettings.pushToTalkConfirmInput)
        )
        created.setMainRecognitionEnabled(
            !currentSettings.listeningModeSettings.enabled ||
                currentState().pushToTalkPressed ||
                pttCommitRequested ||
                simplePttReleasePending
        )
        created.setListeningCapturePaused(
            currentSettings.listeningModeSettings.enabled &&
                (
                    currentState().pushToTalkPressed ||
                        pttCommitRequested ||
                        simplePttReleasePending
                    )
        )
        controller = created
        return created
    }

    private suspend fun configureListeningRecognition(requested: ListeningModeSettings) =
        withContext(Dispatchers.IO) {
            listeningConfigurationMutex.withLock {
                val settings = currentSettings.listeningModeSettings
                if (requested != settings) {
                    AppLogger.i("Listening configuration superseded; applying latest settings")
                }
                val activeController = ensureController()
                val asrDir = currentState().asrDir
                if (settings.enabled && asrDir != null) {
                    activeController.loadAsr(asrDir)
                }
                activeController.setListeningRecognitionEnabled(
                    settings.enabled,
                    settings.recognitionLanguage
                )
                activeController.setListeningCapturePaused(
                    settings.enabled &&
                        (
                            currentState().pushToTalkPressed ||
                                pttCommitRequested ||
                                simplePttReleasePending
                            )
                )
                activeController.setMainRecognitionEnabled(
                    !settings.enabled ||
                        currentState().pushToTalkPressed ||
                        pttCommitRequested ||
                        simplePttReleasePending
                )
                if (settings.enabled) {
                    activeController.setPreferredInputType(settings.preferredInputType)
                    activeController.setMinVolumePercent(settings.minVolumePercent)
                    activeController.setDenoiserMode(settings.denoiserMode)
                    activeController.setSpeechEnhancementMode(settings.speechEnhancementMode)
                    activeController.setClassicVadEnabled(settings.classicVadEnabled)
                    activeController.setSileroVadEnabled(settings.sileroVadEnabled)
                    activeController.setSileroVadThreshold(settings.sileroVadThreshold)
                    activeController.setSileroVadPreRollMs(settings.sileroVadPreRollMs)
                } else {
                    applySettingsToController(currentSettings)
                }
            }
        }

    private fun listeningEngineSettingsChanged(
        previous: ListeningModeSettings,
        next: ListeningModeSettings
    ): Boolean =
        previous.enabled != next.enabled ||
            previous.recognitionLanguage != next.recognitionLanguage ||
            previous.preferredInputType != next.preferredInputType ||
            previous.minVolumePercent != next.minVolumePercent ||
            previous.denoiserMode != next.denoiserMode ||
            previous.speechEnhancementMode != next.speechEnhancementMode ||
            previous.classicVadEnabled != next.classicVadEnabled ||
            previous.sileroVadEnabled != next.sileroVadEnabled ||
            previous.sileroVadThreshold != next.sileroVadThreshold ||
            previous.sileroVadPreRollMs != next.sileroVadPreRollMs

    private suspend fun startRealtimeInternal(): Boolean {
        val asr = currentState().asrDir
        val voice = currentState().voiceDir
        val requireVoice =
            !currentSettings.ttsDisabled && !currentSettings.listeningModeSettings.enabled
        if (asr == null || (requireVoice && voice == null)) {
            updateStatus(if (requireVoice) "请先安装语音识别资源并导入语音包" else "请先安装语音识别资源")
            return false
        }
        if (currentState().running) return true
        if (!RealtimeOwnerGate.acquire(APP_OWNER_TAG)) {
            updateStatus("麦克风已被其它入口占用")
            return false
        }
        updateState {
            it.copy(
                running = true,
                status = "启动麦克风中",
                inputLevel = 0f,
                playbackProgress = 0f
            )
        }
        lastProgressUpdateAtMs.clear()
        lastLevelUpdateAtMs = 0L
        val started = withContext(Dispatchers.IO) {
            val activeController = ensureController()
            if (!activeController.loadAsr(asr)) return@withContext false
            if (requireVoice && voice != null && !activeController.loadTts(voice)) return@withContext false
            activeController.startMic()
        }
        if (started && currentState().running) {
            updateStatus("运行中")
            if (currentSettings.keepAlive) {
                KeepAliveService.start(applicationContext)
            }
            return true
        }
        RealtimeOwnerGate.release(APP_OWNER_TAG)
        KeepAliveService.stop(applicationContext)
        updateState {
            it.copy(
                running = false,
                status = "麦克风启动失败",
                inputLevel = 0f,
                playbackProgress = 0f
            )
        }
        return false
    }

    private suspend fun stopRealtimeInternal() {
        val pendingPttCommit = pttCommitJob
        if (pendingPttCommit?.isActive == true) {
            pendingPttCommit.join()
        }
        if (pttCommitJob === pendingPttCommit) {
            pttCommitJob = null
        }
        withContext(Dispatchers.IO) {
            controller?.stopMic()
        }
        RealtimeOwnerGate.release(APP_OWNER_TAG)
        KeepAliveService.stop(applicationContext)
        pttSessionLastText = ""
        pttSessionCommitConsumed = false
        pttCommitRequested = false
        finishSimplePttRelease()
        resetPttHistoryDedup()
        updateState {
            it.copy(
                running = false,
                status = "麦克风已停止",
                inputLevel = 0f,
                playbackProgress = 0f,
                pushToTalkPressed = false,
                pushToTalkStreamingText = ""
            )
        }
    }

    private fun applySettingsToController(settings: UserPrefs.AppSettings) {
        controller?.setSuppressWhilePlaying(settings.muteWhilePlaying)
        controller?.setSuppressDelaySec(settings.muteWhilePlayingDelaySec)
        controller?.setMinVolumePercent(settings.minVolumePercent)
        controller?.setPlaybackGainPercent(settings.playbackGainPercent)
        SoundboardManager.setPlaybackGainPercent(settings.playbackGainPercent)
        SoundboardManager.setInterruptOnNewPlayback(settings.soundboardInterruptOnNewPlayback)
        controller?.setAudioFocusAvoidanceMode(settings.audioFocusAvoidanceMode)
        SoundboardManager.setAudioFocusAvoidanceMode(applicationContext, settings.audioFocusAvoidanceMode)
        controller?.setPiperNoiseScale(settings.piperNoiseScale)
        controller?.setPiperLengthScale(settings.piperLengthScale)
        controller?.setPiperNoiseW(settings.piperNoiseW)
        controller?.setPiperSentenceSilenceSec(settings.piperSentenceSilence)
        controller?.setKokoroSpeakerId(settings.kokoroSpeakerId)
        controller?.setUseAec3(settings.aec3Enabled)
        controller?.setUseVoiceCommunication(settings.echoSuppression)
        controller?.setCommunicationMode(settings.communicationMode)
        controller?.setPreferredInputType(settings.preferredInputType)
        controller?.setPreferredOutputType(settings.preferredOutputType)
        controller?.setDenoiserMode(settings.denoiserMode)
        controller?.setSpeechEnhancementMode(settings.speechEnhancementMode)
        controller?.setClassicVadEnabled(settings.classicVadEnabled)
        controller?.setSileroVadEnabled(settings.sileroVadEnabled)
        controller?.setSileroVadThreshold(settings.sileroVadThreshold)
        controller?.setSileroVadPreRollMs(settings.sileroVadPreRollMs)
        controller?.setAllowSystemAecWithAec3(settings.allowSystemAecWithAec3)
        controller?.setSpeakerVerifyEnabled(settings.speakerVerifyEnabled && speakerProfiles.isNotEmpty())
        controller?.setSpeakerVerifyTolerance(settings.speakerVerifyToleranceLevel)
        controller?.setExperimentalRecognitionSensitivity(settings.experimentalRecognitionSensitivity)
        controller?.setExperimentalTargetSpeakerBackend(settings.experimentalTargetSpeakerBackend)
        controller?.setSpeakerProfiles(
            speakerProfiles.map { it.vector.copyOf() },
            speakerProfiles.map { it.neuralVector?.copyOf() },
            speakerProfiles.map { it.confirmationVector?.copyOf() }
        )
        controller?.setSuppressAsrAutoSpeak(
            settings.ttsDisabled || (settings.pushToTalkMode && settings.pushToTalkConfirmInput)
        )
        controller?.setPushToTalkStreamingEnabled(
            settings.pushToTalkMode &&
                settings.pushToTalkConfirmInput &&
                currentState().pushToTalkPressed
        )
        if (settings.listeningModeSettings.enabled) {
            val listening = settings.listeningModeSettings
            controller?.setPreferredInputType(listening.preferredInputType)
            controller?.setMinVolumePercent(listening.minVolumePercent)
            controller?.setDenoiserMode(listening.denoiserMode)
            controller?.setSpeechEnhancementMode(listening.speechEnhancementMode)
            controller?.setClassicVadEnabled(listening.classicVadEnabled)
            controller?.setSileroVadEnabled(listening.sileroVadEnabled)
            controller?.setSileroVadThreshold(listening.sileroVadThreshold)
            controller?.setSileroVadPreRollMs(listening.sileroVadPreRollMs)
        }
    }

    private suspend fun loadInitialAsrDir(): File? {
        val lastName = UserPrefs.getLastAsrName(applicationContext)
        val resolved = lastName?.let { repo.resolveAsr(it) }
        return resolved ?: withContext(Dispatchers.IO) { repo.ensureBundledAsr() }
    }

    private suspend fun loadInitialVoiceDir(): File? {
        val lastName = UserPrefs.getLastVoiceName(applicationContext)
        val resolved = when (lastName) {
            SYSTEM_TTS_VOICE_NAME -> repo.systemTtsVirtualDir()
            KOKORO_VOICE_NAME -> repo.kokoroVoiceDir().takeIf { repo.kokoroVoiceStatus().installed }
            null -> null
            else -> repo.resolveVoicePack(lastName)
        }
        return resolved
            ?: withContext(Dispatchers.IO) { repo.listVoicePacks().firstOrNull()?.dir }
            ?: repo.kokoroVoiceDir().takeIf { repo.kokoroVoiceStatus().installed }
            ?: repo.systemTtsVirtualDir()
    }

    private fun appendRecognizedHistory(text: String, id: Long? = null, fromQuickText: Boolean = false) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val historyId = id ?: manualRecognizedIdSeed--
        if (id != null && currentState().recognized.any { it.id == id }) return
        val item = RecognizedItem(id = historyId, text = normalized)
        val next = (listOf(item) + currentState().recognized).take(MAX_RECOGNIZED_ITEMS)
        lastProgressUpdateAtMs.keys.retainAll(next.asSequence().map { it.id }.toSet())
        updateState { it.copy(recognized = next) }
        if (currentSettings.soundboardKeywordTriggerEnabled &&
            (!fromQuickText || currentSettings.allowQuickTextTriggerSoundboard)
        ) {
            serviceScope.launch {
                SoundboardManager.triggerByText(applicationContext, normalized)
            }
        }
    }

    private suspend fun enqueueSpeakAndAppendHistory(
        text: String,
        fromQuickText: Boolean = false,
        interruptCurrent: Boolean = false
    ) {
        val message = text.trim()
        if (message.isEmpty()) return
        val hasSoundboardTrigger = SoundboardManager.hasTriggerMatch(applicationContext, message)
        val suppressTtsForSoundboard = shouldSuppressTtsForSoundboardTrigger(
            fromQuickText = fromQuickText,
            keywordTriggerEnabled = currentSettings.soundboardKeywordTriggerEnabled,
            allowQuickTextTrigger = currentSettings.allowQuickTextTriggerSoundboard,
            suppressTtsOnKeyword = currentSettings.soundboardSuppressTtsOnKeyword,
            hasTriggerMatch = hasSoundboardTrigger
        )
        if (suppressTtsForSoundboard) {
            appendRecognizedHistory(message, fromQuickText = fromQuickText)
            updateStatus("已触发音效板，跳过本句朗读")
            return
        }
        val queuedId = speakText(message, interruptCurrent = interruptCurrent)
        if (queuedId != null) {
            appendRecognizedHistory(message, queuedId, fromQuickText = fromQuickText)
            updateStatus("已加入朗读队列")
        } else {
            appendRecognizedHistory(message, fromQuickText = fromQuickText)
        }
    }

    private fun mergePttTranscript(existing: String, incoming: String): String {
        return PttTranscriptMerger.merge(existing, incoming)
    }

    private fun appendPttFinalTranscript(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val merged = mergePttTranscript(pttSessionLastText, normalized)
        pttSessionLastText = merged
        if (merged != currentState().pushToTalkStreamingText) {
            updateState { it.copy(pushToTalkStreamingText = merged) }
        }
    }

    private fun updatePttPreviewTranscript(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val preview = mergePttTranscript(pttSessionLastText, normalized)
        if (preview != currentState().pushToTalkStreamingText) {
            updateState { it.copy(pushToTalkStreamingText = preview) }
        }
    }

    private fun normalizePttHistoryKey(text: String): String {
        return text.trim().trimEnd('。', '！', '？', '!', '?', '，', ',', '；', ';', '、', '.')
    }

    private fun resetPttHistoryDedup() {
        lastPttHistoryTextKey = ""
        lastPttHistoryAtMs = 0L
    }

    private fun updateStatus(status: String) {
        updateState { it.copy(status = status) }
    }

    private fun updateState(transform: (RealtimeHostState) -> RealtimeHostState) {
        val previous = _state.value
        _state.update(transform)
        val snapshot = _state.value
        RealtimeRuntimeBridge.updateAppSnapshot(
            RealtimeRuntimeBridge.Snapshot(
                running = snapshot.running,
                latestRecognizedText = snapshot.recognized.firstOrNull()?.text.orEmpty(),
                inputLevel = snapshot.inputLevel,
                playbackProgress = snapshot.playbackProgress,
                inputDeviceLabel = snapshot.inputDeviceLabel,
                outputDeviceLabel = snapshot.outputDeviceLabel,
                pushToTalkPressed = snapshot.pushToTalkPressed,
                pushToTalkStreamingText = snapshot.pushToTalkStreamingText,
                listeningEnabled = snapshot.listeningEnabled,
                listeningItems = snapshot.listeningItems,
                listeningStreamingText = snapshot.listeningStreamingText,
                listeningInputDeviceLabel = snapshot.listeningInputDeviceLabel
            )
        )
        LanCastRuntime.updateRealtimeState(snapshot.running, snapshot.playbackProgress)
        if (snapshot.status != previous.status) {
            syncLiveSubtitleNotification()
        }
    }

    private fun currentState(): RealtimeHostState = _state.value

    private fun isOverlayOpenTarget(target: String): Boolean {
        return target == OverlayBridge.TARGET_OPEN ||
            target == OverlayBridge.TARGET_INPUT ||
            target == OverlayBridge.TARGET_OPEN_LAN_CAST
    }

    inner class LocalBinder : Binder() {
        fun getService(): RealtimeHostService = this@RealtimeHostService
    }

    companion object {
        private const val APP_OWNER_TAG = RealtimeRuntimeBridge.APP_OWNER_TAG
        private const val MAX_RECOGNIZED_ITEMS = 50
        private const val MAX_LISTENING_ITEMS = 120
        private const val SIMPLE_PTT_RELEASE_GRACE_MS = 800L
        private const val LISTENING_PREVIEW_STABLE_COMMIT_MS = 1_500L
        private val LISTENING_COMPARISON_PUNCTUATION = setOf(
            '，', '。', '！', '？', '；', '：', '、',
            ',', '.', '!', '?', ';', ':'
        )
        private const val LEVEL_UPDATE_INTERVAL_MS = 33L
        private const val LEVEL_UPDATE_DELTA = 0.02f
        private const val PROGRESS_UPDATE_INTERVAL_MS = 48L
        private const val PROGRESS_UPDATE_DELTA = 0.02f
        private const val ACTION_SUBMIT_QUICK_SUBTITLE =
            "com.lhtstudio.kigtts.app.action.SUBMIT_QUICK_SUBTITLE"
        private const val EXTRA_QUICK_SUBTITLE_TARGET = "quick_subtitle_target"
        private const val EXTRA_QUICK_SUBTITLE_TEXT = "quick_subtitle_text"

        fun ensureStarted(context: Context) {
            runCatching {
                context.startService(Intent(context, RealtimeHostService::class.java))
            }.onFailure {
                AppLogger.e("RealtimeHostService.ensureStarted failed", it)
            }
        }

        fun submitQuickSubtitle(
            context: Context,
            target: String,
            text: String
        ) {
            val normalized = text.trim()
            if (normalized.isEmpty()) return
            val intent = Intent(context, RealtimeHostService::class.java).apply {
                action = ACTION_SUBMIT_QUICK_SUBTITLE
                putExtra(EXTRA_QUICK_SUBTITLE_TARGET, target)
                putExtra(EXTRA_QUICK_SUBTITLE_TEXT, normalized)
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                AppLogger.e("RealtimeHostService.submitQuickSubtitle failed", it)
            }
        }
    }
}
