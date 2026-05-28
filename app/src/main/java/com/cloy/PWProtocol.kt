package com.cloy

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
 * Pixel Worlds TCP/BSON Protocol — reverse engineered from PCAP (May 2025)
 *
 * Frame format:  [4-byte LE total_length][BSON envelope]
 * Envelope:      { m0:{ID:..., ...}, m1:{...}, ... , sGot: N }
 * NOTE: NO "mc" field — messages are m0, m1, m2... until key missing
 *
 * Real login flow (PCAP confirmed):
 * C→S VChk {OS:"Android", OSt:2}
 * S→C VChk {VN:206}
 * C→S RuXN {PiYM:<JWT>, vKOJ:883, oeSI:1, sHbj:"!9ps*tT18JuheqAfT%c)vExv_j66MEvNx"}
 * S→C RuXN {NfIM:<coID>, Tokd:<nick>, ...}
 * C→S empB+Fwpn+UtgH("#menu")+geIt+fgkp+Islh (batch)
 * C→S zXpF {Bmie:"PIXELMINES"}
 * C→S ppIX {UUEW:"PIXELMINES", BiPp:0, WyWs:0}
 * S→C Snlb {fkSm:"ec2-xx.amazonaws.com", Bmie:"PIXELMINES"} ← redirect!
 * [reconnect to new host, repeat VChk+RuXN]
 * C→S ppIX {UUEW:"PIXELMINES", BiPp:0, WyWs:0}
 * S→C ppIX {Bmie:"PIXELMINES", qYqx:0, BiPp:0}
 * C→S rICq+DZJs(2,6,14,23)+uygc (batch) ← request world data
 * S→C aHNG {UUEW:<zstd-compressed world>} ← world data
 * C→S UtgH("PIXELMINES")+xxMa+Rlsm+yOLm+sDPK+uqjs+vHPe+Islh
 *
 * Mining/Nether packets (PCAP confirmed):
 * C→S yGLu {Nxir:<bin: x(LE i32) y(LE i32)>}  ← move (8 bytes = 2x int32 LE)
 * C→S TiLT {x:int, y:int}                       ← attack/hit block at tile
 * C→S xBiS {x:int, y:int}                       ← select/target tile
 * C→S GnkD {t?:long}                            ← physics tick (every ~100ms)
 * C→S mSjb {CollectableID:int}                  ← collect item
 * C→S fPAb {JfYG:int, fNPS:int, T:long}         ← position sync
 * C→S CHJT {x:int, y:int, fNPS:int}             ← checkpoint/landing
 * C→S udbU {hBlock:int}                         ← use item (pickaxe = 4087)
 * C→S ppIX {BaaD:true, UUEW:"MINEWORLD", BiPp:0, WyWs:N} ← enter mine (WyWs=level)
 * C→S ldEM {T:long}                             ← heartbeat every 2s
 * S→C TiLT {x,y,NGVj,qCrH,NfIM}               ← block hit confirm
 * S→C nHCR {x,y,qCrH,NfIM,jrFN}               ← block broken
 * S→C udbU {hBlock,NfIM,qCrH}                  ← item use result
 */
class PWProtocol(private val listener: Listener) {

    companion object {
        const val TAG = "PWProto"
        const val DEFAULT_HOST = "game-lava.pixelworlds.pw"
        const val PORT = 10001
        const val SHBJ = "!9ps*tT18JuheqAfT%c)vExv_j66MEvNx"
        const val VKOJ = 883
    }

    interface Listener {
        fun onConnected(host: String)
        fun onDisconnected(reason: String)
        fun onLoggedIn(coID: String, nick: String)
        fun onWorldJoined(world: String)
        fun onWorldData(tiles: IntArray, width: Int, height: Int)
        fun onPacket(id: String, pkt: BsonDocument)
        fun onStatus(msg: String)
    }

    var jwt = ""
    var coID = ""
    var nick = ""

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private val running = AtomicBoolean(false)
    private val loggedIn = AtomicBoolean(false)
    private val sendQueue = ConcurrentLinkedQueue<BsonDocument>()
    private val executor = Executors.newCachedThreadPool()

    // BFS world graph
    var worldWidth = 100
    var worldHeight = 60
    var worldFg = IntArray(0)
    var playerX = 0f
    var playerY = 0f

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    fun connect(host: String = DEFAULT_HOST) {
        if (running.get()) return
        running.set(true)
        executor.execute { doConnect(host) }
    }

    fun disconnect() {
        running.set(false)
        loggedIn.set(false)
        runCatching { socket?.close() }
        listener.onDisconnected("manual")
    }

    fun joinWorld(world: String, baaD: Boolean = false, wyWs: Int = 0) {
        val pkt = bson("ppIX")
        if (baaD) pkt["BaaD"] = BsonBoolean.TRUE
        pkt["UUEW"] = BsonString(world)
        pkt["BiPp"] = BsonInt32(0)
        pkt["WyWs"] = BsonInt32(wyWs)
        queue(pkt)
        listener.onStatus("Joining $world…")
    }

    fun move(x: Int, y: Int) {
        playerX = x.toFloat(); playerY = y.toFloat()
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(x); buf.putInt(y)
        val pkt = bson("yGLu")
        pkt["Nxir"] = BsonBinary(buf.array())
        queue(pkt)
    }

    fun attack(x: Int, y: Int) {
        val pkt = bson("TiLT"); pkt["x"] = BsonInt32(x); pkt["y"] = BsonInt32(y)
        queue(pkt)
    }

    fun selectTile(x: Int, y: Int) {
        val pkt = bson("xBiS"); pkt["x"] = BsonInt32(x); pkt["y"] = BsonInt32(y)
        queue(pkt)
    }

    fun collect(collectableId: Int) {
        val pkt = bson("mSjb"); pkt["CollectableID"] = BsonInt32(collectableId)
        queue(pkt)
    }

    fun useItem(blockId: Int = 4087) { // 4087 = pickaxe
        val pkt = bson("udbU"); pkt["hBlock"] = BsonInt32(blockId)
        queue(pkt)
    }

    fun tick(timestamp: Long = ts()) {
        val pkt = bson("GnkD")
        if (timestamp > 0) pkt["t"] = BsonInt64(timestamp)
        queue(pkt)
    }

    fun posSync(jfYG: Int, fNPS: Int) {
        val pkt = bson("fPAb")
        pkt["JfYG"] = BsonInt32(jfYG)
        pkt["fNPS"] = BsonInt32(fNPS)
        pkt["T"]    = BsonInt64(ts())
        queue(pkt)
    }

    fun flush() = flushQueue()

    fun isConnected() = running.get() && socket?.isConnected == true
    fun isLoggedIn()  = loggedIn.get()

    // ── INTERNALS ─────────────────────────────────────────────────────────────

    private fun doConnect(host: String) {
        try {
            listener.onStatus("Connecting $host:$PORT…")
            val addr = InetAddress.getByName(host)
            socket = Socket(addr, PORT).apply {
                tcpNoDelay = true; soTimeout = 35_000
            }
            input  = socket!!.getInputStream()
            output = socket!!.getOutputStream()
            listener.onConnected(host)
            listener.onStatus("Connected! Sending VChk…")
            startHeartbeat()
            startReceive()
            sendDirect(buildVChk())
        } catch (e: Exception) {
            Log.e(TAG, "connect: ${e.message}")
            listener.onStatus("Bağlantı hatası: ${e.message}")
            if (running.get()) scheduleReconnect(host)
        }
    }

    private fun startReceive() = executor.execute {
        val lenBuf = ByteArray(4)
        while (running.get()) {
            try {
                if (readFully(input!!, lenBuf, 4) < 4) { drop("EOF"); break }
                val total = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                val bsonLen = total - 4
                if (bsonLen < 5 || bsonLen > 5_000_000) { drop("bad len $bsonLen"); break }
                val bsonData = ByteArray(bsonLen)
                if (readFully(input!!, bsonData, bsonLen) < bsonLen) { drop("short read"); break }
                processEnvelope(bsonData)
            } catch (e: IOException) {
                if (running.get()) drop(e.message ?: "io error")
                break
            }
        }
    }

    private fun startHeartbeat() = executor.execute {
        while (running.get()) {
            Thread.sleep(2000)
            if (loggedIn.get()) { queue(bson("ldEM").also { it["T"] = BsonInt64(ts()) }); flushQueue() }
        }
    }

    private fun processEnvelope(data: ByteArray) {
        try {
            val bb = ByteBuffer.wrap(data)
            val env = BsonDocumentCodec().decode(BsonBinaryReader(bb), DecoderContext.builder().build())
            var i = 0
            while (env.containsKey("m$i")) {
                val pkt = env.getDocument("m$i")
                if (pkt.containsKey("ID")) handlePacket(pkt)
                i++
            }
        } catch (e: Exception) { Log.v(TAG, "parse: ${e.message}") }
        flushQueue()
    }

    private fun handlePacket(p: BsonDocument) {
        val id = p.getString("ID").value
        Log.d(TAG, "← $id")

        when (id) {
            "VChk" -> {
                val vn = if (p.containsKey("VN")) p.getInt32("VN").value else 0
                listener.onStatus("VChk OK (v$vn), logging in…")
                sendDirect(buildRuXN())
            }
            "RuXN" -> {
                coID = p.getString("NfIM", BsonString("")).value
                nick = p.getString("Tokd", BsonString("")).value
                loggedIn.set(true)
                listener.onLoggedIn(coID, nick)
                listener.onStatus("✅ Logged in: $nick")
                sendPostLogin()
            }
            "Snlb" -> {
                val host = p.getString("fkSm", BsonString("")).value
                val world = p.getString("Bmie", BsonString("")).value
                if (host.isNotEmpty()) {
                    listener.onStatus("Redirect → $host")
                    redirectTo(host, world)
                }
            }
            "ppIX" -> {
                val world = p.getString("Bmie", BsonString("")).value
                if (world.isNotEmpty()) {
                    listener.onWorldJoined(world)
                    listener.onStatus("🌍 $world")
                    requestWorldData(world)
                }
            }
            "aHNG" -> {
                listener.onStatus("World data received, parsing…")
                parseWorldData(p)
                sendPostWorld()
            }
            "ldEM" -> {
                val t = if (p.containsKey("STime")) p.getInt64("STime").value else ts()
                queue(bson("ldEM").also { it["T"] = BsonInt64(t) })
            }
            else -> listener.onPacket(id, p)
        }
    }

    // ── PACKET SEQUENCES ─────────────────────────────────────────────────────

    private fun sendPostLogin() {
        queue(bson("empB").also { it["JTsl"] = BsonBoolean.TRUE })
        queue(bson("Fwpn"))
        queue(bson("UtgH").also { it["sBRu"] = BsonString("#menu") })
        queue(bson("geIt"))
        queue(bson("fgkp"))
        queue(bson("Islh"))
        queue(bson("ldEM").also { it["T"] = BsonInt64(ts()) })
    }

    private fun requestWorldData(world: String) {
        queue(bson("rICq").also {
            it["wnBO"] = BsonString(""); it["UUEW"] = BsonString(world); it["BiPp"] = BsonInt32(0)
        })
        listOf(2, 6, 14, 23).forEach { v ->
            queue(bson("DZJs").also { it["jBRj"] = BsonInt32(v) })
        }
        queue(bson("uygc"))
    }

    private fun sendPostWorld() {
        // From PCAP: UtgH+xxMa+Rlsm+yOLm+sDPK+uqjs+vHPe+Islh
        listOf("xxMa", "Rlsm", "yOLm", "sDPK", "uqjs", "vHPe", "Islh").forEach {
            queue(bson(it).also { d -> if (it == "xxMa") d["eNyl"] = BsonInt32(2) })
        }
        queue(bson("PZlO").also { it["kqda"] = BsonInt32(1) })
        queue(bson("sGha").also { it["zNds"] = BsonString("{'0':0}") })
    }

    // ── WORLD DATA ────────────────────────────────────────────────────────────

    private fun parseWorldData(p: BsonDocument) {
        try {
            val compressed = p.getBinary("UUEW").data
            val raw = com.github.luben.zstd.Zstd.decompress(
                compressed, com.github.luben.zstd.Zstd.decompressedSize(compressed).toInt().coerceAtLeast(500_000)
            )
            val bb = ByteBuffer.wrap(raw)
            val wd = BsonDocumentCodec().decode(BsonBinaryReader(bb), DecoderContext.builder().build())

            worldWidth  = wd.getInt32("w",  BsonInt32(100)).value
            worldHeight = wd.getInt32("h",  BsonInt32(60)).value

            val fgData  = wd.getBinary("fg", BsonBinary(ByteArray(0))).data
            worldFg = IntArray(worldWidth * worldHeight)
            if (fgData.isNotEmpty()) {
                val fgBuf = ByteBuffer.wrap(fgData).order(ByteOrder.LITTLE_ENDIAN)
                for (i in worldFg.indices) {
                    if (fgBuf.remaining() >= 2) worldFg[i] = fgBuf.short.toInt() and 0xFFFF
                }
            }
            listener.onWorldData(worldFg, worldWidth, worldHeight)
            listener.onStatus("✅ World ${worldWidth}x${worldHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "parseWorld: ${e.message}")
            listener.onStatus("World parse error: ${e.message}")
        }
    }

    // ── REDIRECT ──────────────────────────────────────────────────────────────

    private fun redirectTo(host: String, world: String) {
        loggedIn.set(false)
        runCatching { socket?.close() }
        executor.execute {
            Thread.sleep(400)
            try {
                socket = Socket(InetAddress.getByName(host), PORT).apply {
                    tcpNoDelay = true; soTimeout = 35_000
                }
                input  = socket!!.getInputStream()
                output = socket!!.getOutputStream()
                listener.onConnected(host)
                startReceive()
                sendDirect(buildVChk())
            } catch (e: Exception) {
                listener.onStatus("Redirect error: ${e.message}")
            }
        }
    }

    private fun scheduleReconnect(host: String) = executor.execute {
        Thread.sleep(4000)
        if (running.get()) doConnect(host)
    }

    private fun drop(reason: String) {
        if (!running.get()) return
        loggedIn.set(false)
        listener.onDisconnected(reason)
        scheduleReconnect(DEFAULT_HOST)
    }

    // ── SEND ──────────────────────────────────────────────────────────────────

    fun queue(pkt: BsonDocument) = sendQueue.offer(pkt)

    private fun flushQueue() {
        if (sendQueue.isEmpty() || output == null) return
        val batch = ArrayList<BsonDocument>()
        var p: BsonDocument?; while (sendQueue.poll().also { p = it } != null) batch.add(p!!)
        if (batch.isEmpty()) return
        val env = BsonDocument()
        batch.forEachIndexed { i, doc ->
            Log.d(TAG, "→ ${doc.getString("ID").value}")
            env["m$i"] = doc
        }
        sendBson(env)
    }

    private fun sendDirect(pkt: BsonDocument) {
        val env = BsonDocument()
        Log.d(TAG, "→ ${pkt.getString("ID").value}")
        env["m0"] = pkt
        sendBson(env)
    }

    private fun sendBson(env: BsonDocument) {
        try {
            val buf = BasicOutputBuffer()
            BsonBinaryWriter(buf).use { BsonDocumentCodec().encode(it, env, EncoderContext.builder().build()) }
            val bsonBytes = buf.toByteArray()
            val total = 4 + bsonBytes.size
            val lb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); lb.putInt(total)
            output?.write(lb.array())
            output?.write(bsonBytes)
            output?.flush()
        } catch (e: IOException) { drop("send error") }
    }

    // ── BUILDERS ──────────────────────────────────────────────────────────────

    private fun buildVChk() = bson("VChk").also {
        it["OS"]  = BsonString("Android"); it["OSt"] = BsonInt32(2)
    }

    private fun buildRuXN() = bson("RuXN").also {
        it["PiYM"] = BsonString(jwt)
        it["vKOJ"] = BsonInt32(VKOJ)
        it["oeSI"] = BsonInt32(1)
        it["sHbj"] = BsonString(SHBJ)
    }

    // ── UTILS ─────────────────────────────────────────────────────────────────

    private fun bson(id: String) = BsonDocument().also { it["ID"] = BsonString(id) }

    private fun ts() = System.currentTimeMillis() * 10_000L + 621_355_968_000_000_000L

    private fun readFully(s: InputStream, buf: ByteArray, len: Int): Int {
        var r = 0; while (r < len) { val n = s.read(buf, r, len - r); if (n < 0) return r; r += n }; return r
    }

    private operator fun BsonDocument.set(key: String, v: BsonValue) { put(key, v) }
    private fun BsonDocument.getInt32(k: String, default: BsonInt32) = if (containsKey(k)) getInt32(k) else default
    private fun BsonDocument.getString(k: String, default: BsonString) = if (containsKey(k)) getString(k) else default
    private fun BsonDocument.getBinary(k: String, default: BsonBinary) = if (containsKey(k)) getBinary(k) else default
}
