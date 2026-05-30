package com.autobreaker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

/**
 * AutoBreaker — Pixel Worlds otomatik blok kırıcı
 *
 * Nasıl çalışır:
 * 1. AccessibilityService ekrana GestureDispatch ile touch gönderir (ROOT gerekmez)
 * 2. Ekranı grid'e böler (tile boyutu ~54px varsayılan, ayarlanabilir)
 * 3. Akıllı BFS: soldan sağa, yukarıdan aşağıya spiral tarama
 * 4. Her tile'a PUNCH gesture gönderir (basılı tut ~250ms)
 * 5. Sonraki tile'a geçmeden önce karakter tile'a yürür (move tuşları)
 * 6. Harita büyük → sonsuz scroll: sağ kenara gelince sağ ok tuşuna basılı tutar
 * 7. Jitter: her dokunuş ±8px rastgele offset (anti-deteksiyon)
 */
class AutoBreakerService : AccessibilityService() {

    companion object {
        var instance: AutoBreakerService? = null
        var overlayActive = false
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // Overlay UI
    private var overlayView: View? = null
    private var tvStatus: TextView? = null
    private var tvStats: TextView? = null
    private var tvSpeed: TextView? = null

    // State
    private val running    = AtomicBoolean(false)
    private var blocksBroken = 0
    private var sessionStart = 0L
    private var workerThread: Thread? = null

    // Screen dimensions (set on first use)
    private var screenW = 0
    private var screenH = 0

    // Game UI layout (based on screenshots analysis)
    // Pixel Worlds landscape layout:
    // Left side: movement arrows (bottom-left)
    // Right side: jump + punch buttons (bottom-right)
    // Game world: center area
    private var gameLeft   = 0f   // game area left edge
    private var gameTop    = 0f   // game area top
    private var gameRight  = 0f
    private var gameBottom = 0f

    // Configurable via overlay
    var tileSize    = 54f   // pixels per tile (Pixel Worlds ≈54px at 1080p)
    var punchMs     = 200L  // punch hold duration
    var moveDelayMs = 80L   // delay between move steps
    var jitterPx    = 8     // random offset per touch

    // Button positions (from screenshots, normalized 0-1)
    // These are approximate — user can recalibrate
    private val BTN_LEFT_X  = 0.11f
    private val BTN_RIGHT_X = 0.21f
    private val BTN_JUMP_X  = 0.82f
    private val BTN_PUNCH_X = 0.93f
    private val BTN_ROW_Y   = 0.87f  // all buttons at this Y

    // Drag state for overlay
    private var oX = 0; private var oY = 0
    private var tX = 0f; private var tY = 0f
    private var dragging = false
    private lateinit var overlayParams: WindowManager.LayoutParams

    // ── LIFECYCLE ────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("autobreaker", Context.MODE_PRIVATE)
        tileSize    = prefs.getFloat("tileSize", 54f)
        punchMs     = prefs.getLong("punchMs", 200L)
        moveDelayMs = prefs.getLong("moveMs", 80L)
        buildOverlay()
        status("Ready — tap START")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBreaking()
        instance = null
        runCatching { overlayView?.let { wm.removeView(it) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopBreaking() }

    // ── OVERLAY UI ───────────────────────────────────────────────

    private fun buildOverlay() {
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("ox", 20); y = prefs.getInt("oy", 60)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0xEE0d1b2a.toInt(), 0xFF00aaff.toInt(), 2, dp(14).toFloat())
        }

        // Header row: ⛏ title + drag handle
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val fab = TextView(this).apply {
            text = "⛏"; textSize = 22f; gravity = Gravity.CENTER
            setPadding(0, 0, dp(8), 0)
        }
        val title = TextView(this).apply {
            text = "AutoBreaker"; setTextColor(0xFF00aaff.toInt())
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "×"; setTextColor(0xFF888888.toInt()); textSize = 18f
            setOnClickListener { stopBreaking(); runCatching { wm.removeView(overlayView) } }
        }
        header.addView(fab); header.addView(title); header.addView(closeBtn)
        root.addView(header)

        // Status
        tvStatus = TextView(this).apply {
            text = "Ready"; setTextColor(0xFF00ff88.toInt()); textSize = 10f
            setPadding(0, dp(2), 0, dp(2))
        }
        root.addView(tvStatus)

        // Stats
        tvStats = TextView(this).apply {
            text = "Broken: 0 | Time: 0:00"; setTextColor(0xFFaaaaaa.toInt()); textSize = 10f
        }
        root.addView(tvStats)

        root.addView(divider())

        // Speed slider row
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        speedRow.addView(TextView(this).apply {
            text = "Speed "; setTextColor(Color.WHITE); textSize = 10f
        })
        tvSpeed = TextView(this).apply {
            text = "Fast"; setTextColor(0xFF00aaff.toInt()); textSize = 10f
            setPadding(dp(4),0,dp(4),0)
            setOnClickListener { cycleSpeed() }
        }
        speedRow.addView(tvSpeed)
        root.addView(speedRow)

        root.addView(divider())

        // START / STOP button
        val startBtn = TextView(this).apply {
            text = "▶ START"; setTextColor(Color.WHITE); textSize = 13f
            gravity = Gravity.CENTER; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(0xFF007744.toInt(), 0xFF00ff88.toInt(), 2, dp(8).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (running.get()) {
                    stopBreaking()
                    text = "▶ START"
                    background = rounded(0xFF007744.toInt(), 0xFF00ff88.toInt(), 2, dp(8).toFloat())
                } else {
                    startBreaking()
                    text = "■ STOP"
                    background = rounded(0xFF770011.toInt(), 0xFFff4444.toInt(), 2, dp(8).toFloat())
                }
            }
        }
        root.addView(startBtn)

        // Drag setup
        fab.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { oX=overlayParams.x; oY=overlayParams.y; tX=e.rawX; tY=e.rawY; dragging=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=e.rawX-tX; val dy=e.rawY-tY
                    if (abs(dx)>6||abs(dy)>6) dragging=true
                    if (dragging) { overlayParams.x=oX+dx.toInt(); overlayParams.y=oY+dy.toInt(); wm.updateViewLayout(overlayView, overlayParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("ox",overlayParams.x).putInt("oy",overlayParams.y).apply(); true
                }
                else -> false
            }
        }

        overlayView = root
        wm.addView(root, overlayParams)
        overlayActive = true

        // Stats ticker
        handler.post(object : Runnable {
            override fun run() {
                if (overlayActive) {
                    if (sessionStart > 0) {
                        val ms = System.currentTimeMillis() - sessionStart
                        val s = ms/1000; val m = s/60
                        tvStats?.text = "Broken: $blocksBroken | Time: ${m}:${"%02d".format(s%60)}"
                    }
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    // ── BREAKING LOGIC ───────────────────────────────────────────

    private fun startBreaking() {
        if (running.get()) return
        running.set(true)
        blocksBroken = 0
        sessionStart = System.currentTimeMillis()
        status("Running…")

        workerThread = Thread({
            Thread.sleep(800)  // let user switch to game
            breakLoop()
        }, "AutoBreaker").also { it.start() }
    }

    private fun stopBreaking() {
        running.set(false)
        workerThread?.interrupt()
        status("Stopped. Broken: $blocksBroken")
    }

    private fun breakLoop() {
        getScreenSize()

        // Game area (approximate from screenshots — landscape)
        // Pixel Worlds: buttons on bottom ~15% of screen
        // Left UI strip: ~9% of width
        gameLeft   = screenW * 0.09f
        gameTop    = screenH * 0.02f
        gameRight  = screenW * 0.97f
        gameBottom = screenH * 0.75f  // above buttons

        val cols = ((gameRight - gameLeft) / tileSize).toInt().coerceAtLeast(1)
        val rows = ((gameBottom - gameTop) / tileSize).toInt().coerceAtLeast(1)

        status("Grid: ${cols}x${rows} tiles")

        var col = 0
        var row = 0
        var direction = 1  // 1=right, -1=left (snake pattern)

        while (running.get()) {
            // Calculate tile center
            val tx = gameLeft + col * tileSize + tileSize / 2
            val ty = gameTop  + row * tileSize + tileSize / 2

            // Skip if too close to UI buttons
            if (ty < gameBottom) {
                punchTile(tx, ty)
                blocksBroken++
            }

            // Next tile (snake: right→left→right)
            col += direction
            if (col >= cols || col < 0) {
                direction = -direction
                col += direction
                row++
                if (row >= rows) {
                    // Done with visible area — scroll right and restart
                    status("Scrolling right…")
                    scrollRight()
                    row = 0
                    col = 0
                    direction = 1
                    Thread.sleep(500)
                }
            }

            Thread.sleep(moveDelayMs)
        }
    }

    // ── GESTURE HELPERS ──────────────────────────────────────────

    private fun punchTile(x: Float, y: Float) {
        if (!running.get()) return
        val jx = x + Random.nextInt(-jitterPx, jitterPx + 1)
        val jy = y + Random.nextInt(-jitterPx, jitterPx + 1)

        // 1. Move character toward tile: tap left/right arrow
        moveToward(x)

        // 2. Punch: long press on tile
        val path = Path().also { it.moveTo(jx, jy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, punchMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)

        // Small delay between punches
        Thread.sleep(punchMs + Random.nextLong(20, 60))
    }

    private fun moveToward(targetX: Float) {
        val playerX = screenW * 0.5f  // assume player is roughly centered
        val diff = targetX - playerX
        if (abs(diff) < tileSize * 0.5f) return  // close enough

        val btnX = if (diff < 0) screenW * BTN_LEFT_X else screenW * BTN_RIGHT_X
        val btnY = screenH * BTN_ROW_Y

        val steps = (abs(diff) / tileSize).toInt().coerceIn(1, 5)
        val holdMs = (steps * moveDelayMs).coerceAtLeast(60L)

        val path = Path().also { it.moveTo(btnX, btnY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, holdMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        Thread.sleep(holdMs + 30)
    }

    private fun scrollRight() {
        // Hold right arrow for ~1.5 seconds to scroll the map
        val btnX = screenW * BTN_RIGHT_X
        val btnY = screenH * BTN_ROW_Y
        val path = Path().also { it.moveTo(btnX, btnY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1500)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        Thread.sleep(1800)
    }

    private fun jump() {
        val x = screenW * BTN_JUMP_X
        val y = screenH * BTN_ROW_Y
        val path = Path().also { it.moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        Thread.sleep(400)
    }

    private fun getScreenSize() {
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        if (screenW < screenH) { // portrait fallback
            val tmp = screenW; screenW = screenH; screenH = tmp
        }
    }

    // ── SPEED CYCLE ──────────────────────────────────────────────

    private fun cycleSpeed() {
        when (tvSpeed?.text) {
            "Slow"   -> { punchMs=200; moveDelayMs=100; tvSpeed?.text="Normal" }
            "Normal" -> { punchMs=150; moveDelayMs=70;  tvSpeed?.text="Fast"   }
            "Fast"   -> { punchMs=100; moveDelayMs=50;  tvSpeed?.text="Ultra"  }
            "Ultra"  -> { punchMs=300; moveDelayMs=150; tvSpeed?.text="Slow"   }
            else     -> { punchMs=150; moveDelayMs=70;  tvSpeed?.text="Fast"   }
        }
        prefs.edit().putLong("punchMs", punchMs).putLong("moveMs", moveDelayMs).apply()
        status("Speed: ${tvSpeed?.text}")
    }

    // ── UI HELPERS ───────────────────────────────────────────────

    private fun status(msg: String) = handler.post { tvStatus?.text = msg }

    private fun divider() = View(this).apply {
        setBackgroundColor(0x2200aaff.toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.setMargins(0,dp(5),0,dp(5)) }
    }

    private fun rounded(fill: Int, stroke: Int, sw: Int, r: Float) = GradientDrawable().apply {
        setColor(fill); setStroke(sw, stroke); cornerRadius = r
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
