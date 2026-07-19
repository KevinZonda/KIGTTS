package com.lhtstudio.kigtts.app.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

internal object LanCastAudioBridge {
    private sealed interface Event {
        data class Text(val json: JSONObject) : Event
        data class Binary(val bytes: ByteArray) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<Event>(
        capacity = 48,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val streamRevision = AtomicInteger(0)

    @Volatile private var outputMode = LanCastAudioOutputMode.Local

    init {
        scope.launch {
            for (event in events) {
                when (event) {
                    is Event.Text -> LanCastRuntime.broadcastAudioControl(event.json)
                    is Event.Binary -> LanCastRuntime.broadcastAudio(event.bytes)
                }
            }
        }
    }

    fun setOutputMode(mode: LanCastAudioOutputMode) {
        outputMode = mode
    }

    fun outputMode(): LanCastAudioOutputMode = outputMode

    fun playbackPlan(): LanCastPlaybackPlan = resolveLanCastPlaybackPlan(
        mode = outputMode,
        webAvailable = LanCastRuntime.hasAudioClients()
    )

    fun beginPcm(sampleRate: Int): Int? {
        if (!playbackPlan().web) return null
        val streamId = nextStreamId()
        events.trySend(
            Event.Text(
                JSONObject()
                    .put("type", "audioStart")
                    .put("streamId", streamId)
                    .put("sampleRate", sampleRate)
                    .put("channels", 1)
                    .put("encoding", "pcm_s16le")
            )
        )
        return streamId
    }

    fun publishPcm(
        streamId: Int?,
        samples: FloatArray,
        offset: Int,
        length: Int,
        sampleRate: Int
    ) {
        if (streamId == null || length <= 0) return
        val safeOffset = offset.coerceIn(0, samples.size)
        val safeLength = length.coerceIn(0, samples.size - safeOffset)
        if (safeLength <= 0) return
        val buffer = ByteBuffer.allocate(16 + safeLength * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC)
        buffer.putInt(streamId)
        buffer.putInt(sampleRate)
        buffer.putInt(safeLength)
        for (index in safeOffset until safeOffset + safeLength) {
            val pcm = (max(-1f, min(1f, samples[index])) * Short.MAX_VALUE)
                .toInt()
                .toShort()
            buffer.putShort(pcm)
        }
        events.trySend(Event.Binary(buffer.array()))
    }

    fun endPcm(streamId: Int?, interrupted: Boolean) {
        if (streamId == null) return
        events.trySend(
            Event.Text(
                JSONObject()
                    .put("type", "audioEnd")
                    .put("streamId", streamId)
                    .put("interrupted", interrupted)
            )
        )
    }

    fun beginFile(
        file: File,
        startMs: Long,
        endMs: Long,
        gainPercent: Int
    ): Long? {
        if (!playbackPlan().web || !file.isFile) return null
        val mediaId = LanCastRuntime.registerMedia(file)
        events.trySend(
            Event.Text(
                JSONObject()
                    .put("type", "audioFile")
                    .put("mediaId", mediaId)
                    .put("url", "/media/$mediaId")
                    .put("startMs", startMs.coerceAtLeast(0L))
                    .put("endMs", endMs.coerceAtLeast(0L))
                    .put("gain", gainPercent.coerceIn(0, 1000) / 100.0)
            )
        )
        return mediaId
    }

    fun endFile(mediaId: Long?) {
        if (mediaId == null) return
        events.trySend(
            Event.Text(
                JSONObject()
                    .put("type", "audioFileEnd")
                    .put("mediaId", mediaId)
            )
        )
        LanCastRuntime.unregisterMedia(mediaId)
    }

    private fun nextStreamId(): Int {
        val next = streamRevision.incrementAndGet()
        return if (next == Int.MAX_VALUE) {
            streamRevision.set(1)
            1
        } else {
            next
        }
    }

    private val MAGIC = byteArrayOf('K'.code.toByte(), 'I'.code.toByte(), 'G'.code.toByte(), 'A'.code.toByte())
}
