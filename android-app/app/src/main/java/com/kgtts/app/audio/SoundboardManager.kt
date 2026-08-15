package com.lhtstudio.kigtts.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import com.lhtstudio.kigtts.app.data.SoundboardConfig
import com.lhtstudio.kigtts.app.data.SoundboardItem
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.data.defaultSoundboardConfig
import com.lhtstudio.kigtts.app.data.parseSoundboardConfig
import com.lhtstudio.kigtts.app.lan.LanCastAudioBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random
import kotlin.math.log10
import kotlin.math.roundToInt

data class SoundboardPlaybackState(
    val playing: Boolean = false,
    val progress: Float = 0f
)

internal fun shouldSuppressTtsForSoundboardTrigger(
    fromQuickText: Boolean,
    keywordTriggerEnabled: Boolean,
    allowQuickTextTrigger: Boolean,
    suppressTtsOnKeyword: Boolean,
    hasTriggerMatch: Boolean
): Boolean {
    val triggerAllowed = keywordTriggerEnabled && (!fromQuickText || allowQuickTextTrigger)
    return triggerAllowed && suppressTtsOnKeyword && hasTriggerMatch
}

object SoundboardManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateMutex = Mutex()
    private val playbackStates = MutableStateFlow<Map<Long, SoundboardPlaybackState>>(emptyMap())
    private val players = linkedMapOf<Long, ActivePlayback>()

    @Volatile
    private var cachedConfig: SoundboardConfig = defaultSoundboardConfig()
    @Volatile
    private var hasLoadedConfig = false
    @Volatile
    private var playbackGainPercent = 100
    @Volatile
    private var interruptOnNewPlayback = true
    @Volatile
    private var audioFocusController: PlaybackAudioFocusController? = null

    private data class ActivePlayback(
        val itemId: Long,
        val player: MediaPlayer,
        val enhancer: LoudnessEnhancer?,
        val audioFocusLease: PlaybackAudioFocusController.Lease?,
        val localPlayback: Boolean,
        val webMediaId: Long?,
        val pollJob: Job,
        val stopJob: Job?
    )

    fun playbackState(): StateFlow<Map<Long, SoundboardPlaybackState>> = playbackStates.asStateFlow()

    suspend fun loadConfig(context: Context): SoundboardConfig {
        val parsed = parseSoundboardConfig(UserPrefs.getSoundboardConfig(context))
        updateCachedConfig(parsed)
        return parsed
    }

    fun updateCachedConfig(config: SoundboardConfig) {
        cachedConfig = config
        hasLoadedConfig = true
        scope.launch {
            cleanupStalePlaybacks(config)
        }
    }

    fun setPlaybackGainPercent(percent: Int) {
        playbackGainPercent = percent.coerceIn(0, 1000)
        scope.launch {
            stateMutex.withLock {
                players.values.forEach { active ->
                    if (active.localPlayback) {
                        applyPlaybackGain(
                            player = active.player,
                            enhancer = active.enhancer,
                            percent = playbackGainPercent
                        )
                    } else {
                        runCatching { active.player.setVolume(0f, 0f) }
                    }
                }
            }
        }
    }

    fun setInterruptOnNewPlayback(enabled: Boolean) {
        interruptOnNewPlayback = enabled
    }

    fun interruptForTtsPlayback() {
        if (!interruptOnNewPlayback) return
        scope.launch { stopAll() }
    }

    fun setAudioFocusAvoidanceMode(context: Context, mode: Int) {
        val controller = audioFocusController
            ?: PlaybackAudioFocusController(
                context,
                AudioAttributes.CONTENT_TYPE_MUSIC
            ).also { audioFocusController = it }
        controller.setMode(mode)
    }

    fun cachedOrDefaultConfig(): SoundboardConfig = cachedConfig

    suspend fun stop(itemId: Long) {
        stateMutex.withLock {
            releasePlaybackLocked(itemId)
        }
    }

    suspend fun stopAll() {
        stateMutex.withLock {
            releaseAllPlaybacksLocked()
        }
    }

    suspend fun play(item: SoundboardItem): Boolean {
        val path = item.audioPath.trim()
        if (path.isEmpty()) return false
        val targetFile = File(path)
        if (!targetFile.exists()) return false
        stateMutex.withLock {
            if (interruptOnNewPlayback) {
                releaseAllPlaybacksLocked()
            } else {
                releasePlaybackLocked(item.id)
            }
            val mediaPlayer = MediaPlayer()
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            mediaPlayer.setAudioAttributes(audioAttrs)
            mediaPlayer.setDataSource(targetFile.absolutePath)
            mediaPlayer.prepare()
            val playbackPlan = LanCastAudioBridge.playbackPlan()
            val enhancer = if (playbackPlan.local) createLoudnessEnhancer(mediaPlayer) else null
            if (playbackPlan.local) {
                applyPlaybackGain(mediaPlayer, enhancer, playbackGainPercent)
            } else {
                mediaPlayer.setVolume(0f, 0f)
            }
            val duration = mediaPlayer.duration.coerceAtLeast(0)
            val trimStart = item.trimStartMs.coerceIn(0L, duration.toLong()).toInt()
            val trimEnd = when {
                item.trimEndMs > item.trimStartMs -> item.trimEndMs.coerceIn(item.trimStartMs, duration.toLong()).toInt()
                else -> duration
            }
            if (trimStart > 0) {
                mediaPlayer.seekTo(trimStart)
            }
            val webMediaId = if (playbackPlan.web) {
                LanCastAudioBridge.beginFile(
                    targetFile,
                    trimStart.toLong(),
                    trimEnd.toLong(),
                    playbackGainPercent
                )
            } else {
                null
            }
            val pollJob = scope.launch {
                while (true) {
                    val progress = runCatching {
                        val current = mediaPlayer.currentPosition
                        val denom = (trimEnd - trimStart).coerceAtLeast(1)
                        ((current - trimStart).toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                    }.getOrDefault(0f)
                    updatePlaybackState(item.id, SoundboardPlaybackState(playing = true, progress = progress))
                    delay(48L)
                }
            }
            val stopJob = if (trimEnd in 1 until duration) {
                scope.launch {
                    while (true) {
                        delay(24L)
                        val current = runCatching { mediaPlayer.currentPosition }.getOrDefault(trimEnd)
                        if (current >= trimEnd) {
                            stop(item.id)
                            break
                        }
                    }
                }
            } else {
                null
            }
            mediaPlayer.setOnCompletionListener {
                scope.launch { stop(item.id) }
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                scope.launch { stop(item.id) }
                true
            }
            val audioFocusLease = if (playbackPlan.local) audioFocusController?.acquire() else null
            try {
                mediaPlayer.start()
            } catch (t: Throwable) {
                audioFocusLease?.release()
                LanCastAudioBridge.endFile(webMediaId)
                throw t
            }
            players[item.id] = ActivePlayback(
                itemId = item.id,
                player = mediaPlayer,
                enhancer = enhancer,
                audioFocusLease = audioFocusLease,
                localPlayback = playbackPlan.local,
                webMediaId = webMediaId,
                pollJob = pollJob,
                stopJob = stopJob
            )
            setPlaybackStateLocked(item.id, SoundboardPlaybackState(playing = true, progress = 0f))
        }
        return true
    }

    suspend fun hasTriggerMatch(context: Context, text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        val config = if (!hasLoadedConfig) loadConfig(context) else cachedConfig
        return config.groups.any { group ->
            group.keywordWakeEnabled && group.items.any { item ->
                val wakeWord = item.wakeWord.trim()
                wakeWord.isNotEmpty() &&
                    normalized.contains(wakeWord) &&
                    item.audioPath.isNotBlank() &&
                    File(item.audioPath).exists()
            }
        }
    }

    suspend fun triggerByText(context: Context, text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        val config = if (!hasLoadedConfig) loadConfig(context) else cachedConfig
        val matchesByWakeWord = linkedMapOf<String, MutableList<SoundboardItem>>()
        config.groups.forEach { group ->
            if (!group.keywordWakeEnabled) return@forEach
            group.items.forEach { item ->
                val wakeWord = item.wakeWord.trim()
                if (wakeWord.isNotEmpty() &&
                    normalized.contains(wakeWord) &&
                    item.audioPath.isNotBlank() &&
                    File(item.audioPath).exists()
                ) {
                    matchesByWakeWord.getOrPut(wakeWord) { mutableListOf() } += item
                }
            }
        }
        var triggered = false
        matchesByWakeWord.values.forEach { candidates ->
            val selected = if (candidates.size == 1) candidates.first() else candidates.random(Random.Default)
            if (play(selected)) triggered = true
        }
        return triggered
    }

    private suspend fun cleanupStalePlaybacks(config: SoundboardConfig) {
        val validItemIds = config.groups.asSequence()
            .flatMap { it.items.asSequence() }
            .map { it.id }
            .toSet()
        stateMutex.withLock {
            players.keys.filterNot { it in validItemIds }.forEach(::releasePlaybackLocked)
            val staleStateIds = playbackStates.value.keys.filterNot { it in validItemIds }
            if (staleStateIds.isNotEmpty()) {
                val next = playbackStates.value.toMutableMap()
                staleStateIds.forEach { next.remove(it) }
                playbackStates.value = next
            }
        }
    }

    private suspend fun updatePlaybackState(itemId: Long, state: SoundboardPlaybackState) {
        stateMutex.withLock {
            if (state.playing && !players.containsKey(itemId)) return
            setPlaybackStateLocked(itemId, state)
        }
    }

    private fun setPlaybackStateLocked(itemId: Long, state: SoundboardPlaybackState) {
        val next = playbackStates.value.toMutableMap()
        next[itemId] = state
        playbackStates.value = next
    }

    private fun releasePlaybackLocked(itemId: Long) {
        val existing = players.remove(itemId) ?: run {
            if (playbackStates.value.containsKey(itemId)) {
                setPlaybackStateLocked(itemId, SoundboardPlaybackState(playing = false, progress = 0f))
            }
            return
        }
        existing.pollJob.cancel()
        existing.stopJob?.cancel()
        runCatching {
            existing.enhancer?.enabled = false
        }
        existing.audioFocusLease?.release()
        LanCastAudioBridge.endFile(existing.webMediaId)
        runCatching {
            existing.enhancer?.release()
        }
        runCatching {
            existing.player.setOnCompletionListener(null)
            existing.player.setOnErrorListener(null)
            if (existing.player.isPlaying) existing.player.stop()
        }
        runCatching { existing.player.release() }
        setPlaybackStateLocked(itemId, SoundboardPlaybackState(playing = false, progress = 0f))
    }

    private fun releaseAllPlaybacksLocked() {
        players.keys.toList().forEach(::releasePlaybackLocked)
    }

    private fun createLoudnessEnhancer(player: MediaPlayer): LoudnessEnhancer? {
        return runCatching {
            LoudnessEnhancer(player.audioSessionId).apply {
                enabled = false
            }
        }.getOrNull()
    }

    private fun applyPlaybackGain(
        player: MediaPlayer,
        enhancer: LoudnessEnhancer?,
        percent: Int
    ) {
        val linearGain = (percent.coerceIn(0, 1000) / 100f).coerceAtLeast(0f)
        val directVolume = linearGain.coerceIn(0f, 1f)
        runCatching {
            player.setVolume(directVolume, directVolume)
        }
        if (enhancer == null) return
        val extraGain = linearGain.coerceAtLeast(1f)
        val targetGainMb = if (linearGain <= 1f) {
            0
        } else {
            (2000f * log10(extraGain)).roundToInt().coerceAtLeast(0)
        }
        runCatching {
            enhancer.setTargetGain(targetGainMb)
            enhancer.enabled = linearGain > 1f
        }
    }
}
