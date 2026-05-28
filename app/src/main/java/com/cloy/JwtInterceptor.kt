package com.cloy

import android.content.SharedPreferences
import android.util.Log
import org.bson.*
import org.bson.codecs.*
import org.bson.io.BasicOutputBuffer
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JWT Interceptor Proxy
 *
 * How it works:
 * 1. Start local TCP proxy on port 10001 (localhost)
 * 2. Add hosts entry: game-lava.pixelworlds.pw → 127.0.0.1
 *    (via VpnService — NO ROOT needed)
 * 3. Pixel Worlds connects to our proxy instead of real server
 * 4. We relay all traffic transparently, but intercept the RuXN packet
 * 5. Extract PiYM (JWT) field → save → use for bot
 *
 * NO ROOT needed — uses Android VpnService to redirect game traffic.
 *
 * Alternative (shown to user if VPN not possible):
 * - Manual JWT paste from PCAPdroid (already works)
 *
 * Proxy intercepts OUTGOING RuXN {PiYM: <JWT>} packet from game.
 * Token is fresh every time game connects (game refreshes it automatically).
 */
class JwtInterceptor(
    private val prefs: SharedPreferences,
    private val onCaptured: (jwt: String, coID: String, nick: String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val TAG = "JwtProxy"
        const val PROXY_PORT = 10001
        const val REAL_HOST  = "game-lava.pixelworlds.pw"
        const val REAL_PORT  = 10001
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (running.get()) return
        running.set(true)
        executor.execute { acceptLoop() }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
    }

    private fun acceptLoop() {
        try {
            serverSocket = ServerSocket(PROXY_PORT, 5, InetAddress.getLoopbackAddress())
            onStatus("🔌 JWT proxy listening on :$PROXY_PORT")
            Log.d(TAG, "Proxy listening")
            while (running.get()) {
                val client = serverSocket!!.accept()
                Log.d(TAG, "Game connected to proxy")
                onStatus("🎮 Game connected — intercepting…")
                executor.execute { handleSession(client) }
            }
        } catch (e: IOException) {
            if (running.get()) {
                onStatus("Proxy error: ${e.message}")
                Log.e(TAG, "acceptLoop: ${e.message}")
            }
        }
    }

    private fun handleSession(clientSocket: Socket) {
        var serverSocket: Socket? = null
        try {
            // Connect to real PW server
            val realAddr = InetAddress.getByName(REAL_HOST)
            serverSocket = Socket(realAddr, REAL_PORT).apply { tcpNoDelay = true }
            clientSocket.tcpNoDelay = true

            val clientIn  = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()
            val serverIn  = serverSocket.getInputStream()
            val serverOut = serverSocket.getOutputStream()

            // S→C relay (server → game): transparent, no inspection needed
            val sc = executor.submit {
                relay(serverIn, clientOut, "S→C", intercept = false)
            }

            // C→S relay (game → server): inspect for RuXN JWT
            relay(clientIn, serverOut, "C→S", intercept = true)
            sc.cancel(true)

        } catch (e: Exception) {
            Log.e(TAG, "session: ${e.message}")
        } finally {
            runCatching { clientSocket.close() }
            runCatching { serverSocket?.close() }
        }
    }

    private fun relay(input: InputStream, output: OutputStream, dir: String, intercept: Boolean) {
        // Stream reassembly buffer
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)

        try {
            while (true) {
                val n = input.read(tmp)
                if (n < 0) break
                buf.write(tmp, 0, n)

                // Forward immediately
                output.write(tmp, 0, n)
                output.flush()

                // Inspect accumulated buffer for BSON frames
                if (intercept) inspectBuffer(buf.toByteArray())
            }
        } catch (e: IOException) { /* normal close */ }
    }

    private var lastInspectPos = 0

    private fun inspectBuffer(data: ByteArray) {
        var pos = lastInspectPos
        while (pos < data.size - 4) {
            val total = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (total < 5 || total > 2_000_000) { pos++; continue }
            if (pos + total > data.size) break

            try {
                val bsonData = data.copyOfRange(pos + 4, pos + total)
                val bb = ByteBuffer.wrap(bsonData)
                val env = BsonDocumentCodec().decode(BsonBinaryReader(bb), DecoderContext.builder().build())
                var i = 0
                while (env.containsKey("m$i")) {
                    val pkt = env.getDocument("m$i")
                    if (pkt.containsKey("ID") && pkt.getString("ID").value == "RuXN") {
                        val jwt = pkt.getString("PiYM", BsonString("")).value
                        if (jwt.startsWith("eyJ")) {
                            Log.d(TAG, "✅ JWT captured! len=${jwt.length}")
                            onStatus("✅ JWT captured automatically!")
                            prefs.edit().putString("jwt", jwt).apply()
                            onCaptured(jwt, "", "")
                        }
                    }
                    i++
                }
            } catch (e: Exception) { /* ignore parse errors */ }

            pos += total
        }
        lastInspectPos = pos
    }

    private fun BsonDocument.getString(k: String, default: BsonString) =
        if (containsKey(k)) getString(k) else default
}
