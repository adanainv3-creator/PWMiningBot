package com.cloy

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import org.bson.BsonDocument

/**
 * BotService: background service that runs the bot.
 * Connects Protocol + NetherAI + stat tracking.
 * Communicates with OverlayService via shared preferences + callbacks.
 */
class BotService : Service(), PWProtocol.Listener {

    companion object {
        const val TAG = "BotService"
        var instance: BotService? = null

        // External listener set by OverlayService
        var statusListener: ((String) -> Unit)? = null
        var statsListener: ((NetherAI.NetherStats) -> Unit)? = null
        var connectedListener: ((Boolean) -> Unit)? = null
    }

    lateinit var protocol: PWProtocol
    internal lateinit var auth: AuthManager
    var interceptor: JwtInterceptor? = null
    private lateinit var prefs: SharedPreferences
    private var netherAI: NetherAI? = null
    var running = false

    // Toggles
    var autoNether   = false
    var autoMine     = false
    var autoCollect  = true
    var antiAfk      = true

    // Stats
    var totalGems       = 0
    var totalCrystals   = 0
    var totalRuns       = 0
    var totalKeys       = 0
    var sessionStart    = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs    = getSharedPreferences("cloy_prefs", MODE_PRIVATE)
        protocol = PWProtocol(this)
        auth     = AuthManager(prefs)
        log("BotService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startBot()
            "STOP"  -> stopBot()
            "JOIN_NETHER" -> joinNether()
            "JOIN_MINE"   -> joinMine(intent.getIntExtra("level", 1))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopBot()
        stopInterceptor()
        instance = null
    }

    // ── BOT CONTROL ──────────────────────────────────────────────────────────

    fun startInterceptor() {
        if (interceptor != null) return
        interceptor = JwtInterceptor(prefs,
            onCaptured = { jwt, _, _ ->
                protocol.jwt = jwt
                log("JWT auto-captured, starting bot...")
                if (!running) startBot()
                else protocol.jwt = jwt
            },
            onStatus = { msg -> log(msg) }
        )
        interceptor!!.start()
        log("JWT interceptor started — open Pixel Worlds to capture token")
    }

    fun stopInterceptor() {
        interceptor?.stop()
        interceptor = null
    }

        fun startBot() {
        if (running) return
        running = true
        sessionStart = System.currentTimeMillis()
        log("Starting bot…")
        connectedListener?.invoke(false)

        // Auth first
        auth.login(object : AuthManager.AuthListener {
            override fun onSuccess(coID: String, jwt: String, nick: String) {
                protocol.jwt  = jwt
                protocol.coID = coID
                log("Auth OK: $nick")
                protocol.connect()
            }
            override fun onFailed(error: String) {
                log("Auth failed: $error")
                // If manual JWT saved, try anyway
                val savedJwt = prefs.getString("jwt", "") ?: ""
                if (savedJwt.isNotEmpty()) {
                    protocol.jwt = savedJwt
                    protocol.coID = prefs.getString("coID", "") ?: ""
                    protocol.connect()
                }
            }
            override fun onRefreshed(jwt: String) {
                protocol.jwt = jwt
                log("Token refreshed automatically")
            }
        })
    }

    fun stopBot() {
        running = false
        netherAI?.stop()
        protocol.disconnect()
        connectedListener?.invoke(false)
        log("Bot stopped")
    }

    fun joinNether(level: Int = 1) {
        if (!protocol.isLoggedIn()) { log("Not logged in!"); return }
        // Enter nether: join PIXELSTATION then enter nether portal
        protocol.joinWorld("PIXELSTATION")
        log("Joining Nether (level $level)…")
    }

    fun joinMine(level: Int = 1) {
        if (!protocol.isLoggedIn()) { log("Not logged in!"); return }
        protocol.joinWorld("PIXELMINES")
        log("Joining Mine (level $level)…")
    }

    // ── PROTOCOL CALLBACKS ────────────────────────────────────────────────────

    override fun onConnected(host: String) {
        log("✅ Connected: $host")
        connectedListener?.invoke(true)
    }

    override fun onDisconnected(reason: String) {
        log("❌ Disconnected: $reason")
        connectedListener?.invoke(false)
        netherAI?.stop()
    }

    override fun onLoggedIn(coID: String, nick: String) {
        log("✅ Logged in: $nick ($coID)")
        // After login, join target world based on active mode
        when {
            autoNether -> {
                Thread.sleep(1200)
                protocol.joinWorld("PIXELSTATION")
            }
            autoMine -> {
                Thread.sleep(1200)
                protocol.joinWorld("PIXELMINES")
            }
        }
    }

    override fun onWorldJoined(world: String) {
        log("🌍 World: $world")
        when {
            world.contains("NETHER", ignoreCase = true) -> startNetherAI()
            world.contains("MINE", ignoreCase = true)   -> { /* mining handled separately */ }
        }
    }

    override fun onWorldData(tiles: IntArray, width: Int, height: Int) {
        netherAI?.onWorldData(tiles, width, height)
    }

    override fun onPacket(id: String, pkt: BsonDocument) {
        netherAI?.onPacket(id, pkt)

        // Global collectable handling
        if (id == "?" && autoCollect) {
            val cid = if (pkt.containsKey("CollectableID")) pkt.getInt32("CollectableID").value else return
            protocol.collect(cid)
        }

        // Gems collected
        if (id == "cJeE" && pkt.containsKey("zNds")) {
            // Extract gem count from zNds field
        }
    }

    override fun onStatus(msg: String) {
        log(msg)
        statusListener?.invoke(msg)
    }

    // ── NETHER AI INIT ────────────────────────────────────────────────────────

    private fun startNetherAI() {
        if (!autoNether) return
        netherAI?.stop()
        netherAI = NetherAI(
            protocol = protocol,
            onStatus = { msg ->
                log(msg)
                statusListener?.invoke(msg)
            },
            onStats = { stats ->
                totalGems      = stats.gemsCollected
                totalCrystals  = stats.crystalsCollected
                totalRuns      = stats.runsCompleted
                totalKeys      = stats.keysCollected
                statsListener?.invoke(stats)
            }
        )
        netherAI!!.start()
        log("⚔️ NetherAI started")
    }

    fun getSessionDuration(): Long =
        if (sessionStart == 0L) 0L else System.currentTimeMillis() - sessionStart

    private fun log(msg: String) {
        Log.d(TAG, msg)
        statusListener?.invoke(msg)
    }

}
