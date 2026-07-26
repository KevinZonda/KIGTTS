package com.lhtstudio.kigtts.app.lan

import android.content.Context
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.util.AppLogger
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class LanCastServer(
    private val context: Context,
    port: Int,
    private val runtime: LanCastRuntime
) : NanoWSD(port) {
    private val clients = CopyOnWriteArraySet<CastSocket>()
    private val stateBroadcastExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "LanCast-StateBroadcast").apply { isDaemon = true }
        }
    private val commandExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LanCast-Command").apply { isDaemon = true }
    }
    private val stateBroadcastScheduled = AtomicBoolean(false)

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = CastSocket(handshake)

    override fun serveHttp(session: IHTTPSession): Response {
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return textResponse(Response.Status.METHOD_NOT_ALLOWED, "Method not allowed")
        }
        return when {
            session.uri == "/" -> redirect("/display")
            session.uri == "/display" || session.uri == "/remote" -> assetResponse(
                "lan_cast/index.html",
                "text/html; charset=utf-8"
            )
            session.uri == "/assets/app.css" -> assetResponse(
                "lan_cast/app.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/remote-control.css" -> assetResponse(
                "lan_cast/remote-control.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/remote-small.css" -> assetResponse(
                "lan_cast/remote-small.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/remote-landscape-actions.css" -> assetResponse(
                "lan_cast/remote-landscape-actions.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/remote-quick-dialog.css" -> assetResponse(
                "lan_cast/remote-quick-dialog.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/led-display.css" -> assetResponse(
                "lan_cast/led-display.css",
                "text/css; charset=utf-8"
            )
            session.uri == "/assets/app.js" -> assetResponse(
                "lan_cast/app.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/led-renderer.js" -> assetResponse(
                "lan_cast/led-renderer.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/tv-remote.js" -> assetResponse(
                "lan_cast/tv-remote.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/led-display.js" -> assetResponse(
                "lan_cast/led-display.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/remote-controller.js" -> assetResponse(
                "lan_cast/remote-controller.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/remote-drag-scroll.js" -> assetResponse(
                "lan_cast/remote-drag-scroll.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/remote-group-hint.js" -> assetResponse(
                "lan_cast/remote-group-hint.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/remote-quick-dialog.js" -> assetResponse(
                "lan_cast/remote-quick-dialog.js",
                "application/javascript; charset=utf-8"
            )
            session.uri == "/assets/material-symbols.ttf" -> rawFontResponse()
            session.uri == "/assets/kigtts-logo-horizontal.svg" -> assetResponse(
                "lan_cast/kigtts-logo-horizontal.svg",
                "image/svg+xml"
            )
            session.uri == "/font/current" -> currentFontResponse()
            session.uri.startsWith("/media/") -> mediaResponse(session.uri)
            session.uri == "/health" -> textResponse(Response.Status.OK, "ok")
            session.uri == "/favicon.ico" -> textResponse(Response.Status.OK, "")
            else -> textResponse(Response.Status.NOT_FOUND, "Not found")
        }
    }

    fun broadcastState() {
        if (stateBroadcastExecutor.isShutdown) return
        if (stateBroadcastScheduled.compareAndSet(false, true)) {
            runCatching {
                stateBroadcastExecutor.schedule(
                    {
                        stateBroadcastScheduled.set(false)
                        broadcastText(runtime.presentation().toJson().toString())
                    },
                    STATE_BROADCAST_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }.onFailure {
                stateBroadcastScheduled.set(false)
                if (!stateBroadcastExecutor.isShutdown) {
                    AppLogger.e("LanCast state broadcast scheduling failed", it)
                }
            }
        }
    }

    fun broadcastText(message: String) {
        clients.forEach { client ->
            runCatching { client.send(message) }
                .onFailure {
                    AppLogger.e("LanCast state send failed role=${client.role}", it)
                    client.closeQuietly()
                }
        }
    }

    fun broadcastAudioText(message: String) {
        clients.filter { it.audioEnabled }.forEach { client ->
            runCatching { client.send(message) }
                .onFailure { client.closeQuietly() }
        }
    }

    fun broadcastAudio(payload: ByteArray) {
        clients.filter { it.audioEnabled }.forEach { client ->
            runCatching { client.send(payload) }
                .onFailure { client.closeQuietly() }
        }
    }

    override fun stop() {
        stateBroadcastScheduled.set(false)
        stateBroadcastExecutor.shutdownNow()
        commandExecutor.shutdownNow()
        clients.forEach { it.closeQuietly() }
        clients.clear()
        updateClientCounts()
        super.stop()
    }

    private fun updateClientCounts() {
        runtime.updateClientCounts(
            display = clients.count { it.role == ClientRole.Display },
            remote = clients.count { it.role == ClientRole.Remote },
            audio = clients.count { it.audioEnabled }
        )
    }

    private fun assetResponse(path: String, mime: String): Response = runCatching {
        val stream = context.assets.open(path)
        newChunkedResponse(Response.Status.OK, mime, stream).withNoStore()
    }.getOrElse { error ->
        AppLogger.e("LanCast asset missing: $path", error)
        textResponse(Response.Status.INTERNAL_ERROR, "Asset unavailable")
    }

    private fun rawFontResponse(): Response = runCatching {
        val stream = context.resources.openRawResource(R.font.material_symbols_sharp)
        newChunkedResponse(Response.Status.OK, "font/ttf", stream).withPublicCache()
    }.getOrElse {
        textResponse(Response.Status.NOT_FOUND, "Font unavailable")
    }

    private fun currentFontResponse(): Response {
        val file = runtime.currentFontFile()
            ?: return textResponse(Response.Status.NOT_FOUND, "System font")
        return fileResponse(file, fontMime(file))
    }

    private fun mediaResponse(uri: String): Response {
        val id = uri.substringAfterLast('/').toLongOrNull()
            ?: return textResponse(Response.Status.BAD_REQUEST, "Invalid media id")
        val file = runtime.mediaFile(id)
            ?: return textResponse(Response.Status.NOT_FOUND, "Media unavailable")
        return fileResponse(file, mediaMime(file))
    }

    private fun fileResponse(file: File, mime: String): Response = runCatching {
        newFixedLengthResponse(
            Response.Status.OK,
            mime,
            file.inputStream(),
            file.length()
        ).apply {
            addHeader("Accept-Ranges", "none")
            addHeader("Cache-Control", "no-store")
        }
    }.getOrElse {
        textResponse(Response.Status.NOT_FOUND, "File unavailable")
    }

    private fun redirect(location: String): Response =
        newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "").apply {
            addHeader("Location", location)
            addHeader("Cache-Control", "no-store")
        }

    private fun textResponse(status: Response.Status, text: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", text).withNoStore()

    private fun Response.withNoStore(): Response = apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    private fun Response.withPublicCache(): Response = apply {
        addHeader("Cache-Control", "public, max-age=86400")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    private fun fontMime(file: File): String = when (file.extension.lowercase()) {
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "font/ttf"
    }

    private fun mediaMime(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav", "wave" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private enum class ClientRole { Unknown, Display, Remote }

    private inner class CastSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        @Volatile var role: ClientRole = ClientRole.Unknown
        @Volatile var audioEnabled: Boolean = false

        override fun onOpen() {
            clients += this
            updateClientCounts()
            runCatching { send(runtime.presentation().toJson().toString()) }
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean
        ) {
            clients -= this
            updateClientCounts()
            AppLogger.i(
                "LanCast websocket closed role=$role code=$code remote=$initiatedByRemote reason=${reason.orEmpty()}"
            )
        }

        override fun onMessage(message: WebSocketFrame) {
            if (message.opCode != WebSocketFrame.OpCode.Text) return
            val raw = message.textPayload
            if (raw.length > MAX_MESSAGE_LENGTH) {
                runCatching {
                    close(WebSocketFrame.CloseCode.MessageTooBig, "Message too large", false)
                }
                return
            }
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (json?.optString("type") == "ping") {
                runCatching {
                    send(
                        JSONObject()
                            .put("type", "pong")
                            .put("at", json.optLong("at"))
                            .toString()
                    )
                }
                return
            }
            if (json?.optString("type") == "hello") {
                role = when (json.optString("role")) {
                    "remote" -> ClientRole.Remote
                    else -> ClientRole.Display
                }
                audioEnabled = role == ClientRole.Display &&
                    json.optBoolean("audioEnabled", false)
                updateClientCounts()
                runCatching { send(runtime.presentation().toJson().toString()) }
                return
            }
            if (json?.optString("type") == "audioReady") {
                audioEnabled = role == ClientRole.Display &&
                    json.optBoolean("enabled", true)
                updateClientCounts()
                runCatching {
                    send(JSONObject().put("type", "ack").put("ok", true).toString())
                }
                return
            }
            if (commandExecutor.isShutdown) return
            runCatching {
                commandExecutor.execute {
                    runCatching { send(runtime.handleCommand(raw).toString()) }
                        .onFailure {
                            AppLogger.e("LanCast command response failed role=$role", it)
                            closeQuietly()
                        }
                }
            }.onFailure {
                if (!commandExecutor.isShutdown) {
                    AppLogger.e("LanCast command scheduling failed role=$role", it)
                }
            }
        }

        override fun onPong(pong: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            clients -= this
            updateClientCounts()
            AppLogger.e("LanCast websocket closed", exception)
        }

        fun closeQuietly() {
            runCatching {
                close(WebSocketFrame.CloseCode.GoingAway, "Server closing", false)
            }
            clients -= this
            updateClientCounts()
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 64 * 1024
        const val STATE_BROADCAST_INTERVAL_MS = 60L
    }
}
