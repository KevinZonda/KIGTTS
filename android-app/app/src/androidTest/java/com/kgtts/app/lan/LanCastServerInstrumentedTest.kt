package com.lhtstudio.kigtts.app.lan

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class LanCastServerInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LanCastRuntime.stopServer()
        assertTrue(LanCastRuntime.startServer(context))
    }

    @After
    fun tearDown() {
        LanCastRuntime.stopServer()
    }

    @Test
    fun commandReturnsAckAndUpdatedStateWithoutDisconnecting() {
        WebSocketProbe.connect(LanCastRuntime.DEFAULT_PORT).use { probe ->
            probe.sendJson(
                JSONObject()
                    .put("type", "hello")
                    .put("role", "remote")
            )
            assertNotNull(probe.awaitMessage(2_000L) { it.optString("type") == "state" })

            val requestId = "display-settings-smoke"
            probe.sendJson(
                JSONObject()
                    .put("type", "ledSettings")
                    .put("requestId", requestId)
                    .put(
                        "settings",
                        JSONObject()
                            .put("dotMatrix", false)
                            .put("adaptiveMultiLine", true)
                    )
            )

            val ack = probe.awaitMessage(2_000L) {
                it.optString("type") == "ack" && it.optString("requestId") == requestId
            }
            assertTrue(ack?.optBoolean("ok") == true)

            val state = probe.awaitMessage(2_000L) {
                it.optString("type") == "state" &&
                    it.optJSONObject("led")?.optBoolean("dotMatrix", true) == false &&
                    it.optJSONObject("led")?.optBoolean("adaptiveMultiLine", false) == true
            }
            assertNotNull(state)

            probe.sendJson(JSONObject().put("type", "ping").put("at", 42L))
            assertNotNull(
                probe.awaitMessage(2_000L) {
                    it.optString("type") == "pong" && it.optLong("at") == 42L
                }
            )
        }
    }
}

private class WebSocketProbe private constructor(
    private val socket: Socket,
    private val input: BufferedInputStream,
    private val output: BufferedOutputStream
) : AutoCloseable {
    fun sendJson(json: JSONObject) {
        val payload = json.toString().toByteArray(StandardCharsets.UTF_8)
        require(payload.size <= 65_535)
        val mask = byteArrayOf(0x13, 0x37, 0x42, 0x55)
        output.write(0x81)
        when {
            payload.size < 126 -> output.write(0x80 or payload.size)
            else -> {
                output.write(0x80 or 126)
                output.write(payload.size ushr 8)
                output.write(payload.size and 0xFF)
            }
        }
        output.write(mask)
        payload.forEachIndexed { index, byte ->
            output.write(byte.toInt() xor mask[index % mask.size].toInt())
        }
        output.flush()
    }

    fun awaitMessage(timeoutMs: Long, predicate: (JSONObject) -> Boolean): JSONObject? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val message = try {
                readJsonFrame()
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (message != null && predicate(message)) return message
        }
        return null
    }

    private fun readJsonFrame(): JSONObject? {
        val first = input.read()
        if (first < 0) error("WebSocket closed")
        val second = input.read()
        if (second < 0) error("WebSocket closed")
        val opcode = first and 0x0F
        var length = second and 0x7F
        if (length == 126) {
            length = (input.read() shl 8) or input.read()
        } else if (length == 127) {
            var longLength = 0L
            repeat(8) { longLength = (longLength shl 8) or input.read().toLong() }
            require(longLength <= Int.MAX_VALUE)
            length = longLength.toInt()
        }
        val masked = second and 0x80 != 0
        val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
        val payload = ByteArray(length)
        input.readFully(payload)
        if (mask != null) {
            payload.indices.forEach { index ->
                payload[index] = (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
        }
        if (opcode == 0x8) error("WebSocket closed")
        if (opcode != 0x1) return null
        return JSONObject(String(payload, StandardCharsets.UTF_8))
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        fun connect(port: Int): WebSocketProbe {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.soTimeout = 250
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val key = Base64.encodeToString(
                ByteArray(16) { it.toByte() },
                Base64.NO_WRAP
            )
            output.write(
                (
                    "GET /ws HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: $key\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                    ).toByteArray(StandardCharsets.US_ASCII)
            )
            output.flush()
            val headers = input.readHeaders()
            require(headers.startsWith("HTTP/1.1 101")) { headers.lineSequence().firstOrNull().orEmpty() }
            return WebSocketProbe(socket, input, output)
        }
    }
}

private fun BufferedInputStream.readHeaders(): String {
    val bytes = ArrayList<Byte>()
    while (bytes.size < 16_384) {
        val value = read()
        if (value < 0) error("Connection closed during WebSocket handshake")
        bytes += value.toByte()
        val size = bytes.size
        if (
            size >= 4 &&
            bytes[size - 4] == '\r'.code.toByte() &&
            bytes[size - 3] == '\n'.code.toByte() &&
            bytes[size - 2] == '\r'.code.toByte() &&
            bytes[size - 1] == '\n'.code.toByte()
        ) {
            return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
        }
    }
    error("WebSocket handshake response is too large")
}

private fun BufferedInputStream.readFully(target: ByteArray) {
    var offset = 0
    while (offset < target.size) {
        val count = read(target, offset, target.size - offset)
        if (count < 0) error("WebSocket closed")
        offset += count
    }
}
