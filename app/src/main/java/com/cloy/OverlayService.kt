package com.cloy

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.*
import android.widget.*
import android.graphics.PixelFormat
import android.widget.Toast

/**
 * Cloy — floating overlay panel.
 * FAB button (draggable) → expands to full control panel.
 *
 * Panels:
 * 1. CREDENTIALS  — email/pass + JWT field
 * 2. FEATURES     — Auto Nether toggle, Auto Mine toggle, Anti-AFK, Auto Collect
 * 3. STATS        — Gems, Crystals, Keys, Runs, Time
 * 4. LOG          — Live status scrolling log (last 8 lines)
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: RelativeLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var fab: TextView? = null
    private var panel: ScrollView? = null
    private var panelOpen = false

    // UI refs
    private var tvStatus: TextView? = null
    private var tvGems: TextView? = null
    private var tvCrystals: TextView? = null
    private var tvKeys: TextView? = null
    private var tvRuns: TextView? = null
    private var tvTime: TextView? = null
    private var tvCredStatus: TextView? = null
    private var etEmail: EditText? = null
    private var etPass: EditText? = null
    private var etJwt: EditText? = null
    private var swNether: LinearLayout? = null
    private var swMine: LinearLayout? = null
    private var swAfk: LinearLayout? = null
    private var swCollect: LinearLayout? = null

    private val logLines = ArrayDeque<String>(8)
    private var initX = 0; private var initY = 0
    private var initTX = 0f; private var initTY = 0f
    private var dragging = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("cloy_prefs", MODE_PRIVATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        handler.post(tickRunnable)
        connectBotCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        runCatching { wm.removeView(root) }
        BotService.instance?.stopBot()
    }

    // ── BUILD OVERLAY ─────────────────────────────────────────────────────────

    private fun buildOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("ox", 24); y = prefs.getInt("oy", 220)
        }

        root = RelativeLayout(this)

        // FAB
        fab = TextView(this).apply {
            text = "⚔"; textSize = 28f; gravity = Gravity.CENTER
            background = oval(0xDD1a1a2e.toInt(), 0xFFe94560.toInt(), 3)
        }
        root.addView(fab, RelativeLayout.LayoutParams(dp(56), dp(56)))

        // Panel
        panel = buildPanel()
        val pp = RelativeLayout.LayoutParams(dp(310), RelativeLayout.LayoutParams.WRAP_CONTENT)
        pp.topMargin = dp(64)
        panel!!.visibility = View.GONE
        root.addView(panel, pp)

        setupDrag()
        wm.addView(root, params)
    }

    private fun buildPanel(): ScrollView {
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xF01a1a2e.toInt(), 0xFFe94560.toInt(), 2, dp(16).toFloat())
        }

        // Header
        ll.addView(row(
            text("⚔ Cloy — AutoNether", 0xFFe94560.toInt(), 15f, bold = true, weight = 1f),
            text("✕", 0xFFaaaaaa.toInt(), 18f).also { it.setOnClickListener { togglePanel() } }
        ))
        ll.addView(divider())

        // ── CREDENTIALS ──
        ll.addView(section("🔑 Giriş Bilgileri"))
        etEmail = input("Email", false); etEmail!!.setText(prefs.getString("email", "")); ll.addView(etEmail)
        etPass  = input("Şifre", true);  etPass!!.setText(prefs.getString("password", "")); ll.addView(etPass)
        tvCredStatus = text("", 0xFF00ff88.toInt(), 11f); ll.addView(tvCredStatus)

        val savedEmail = prefs.getString("email", "") ?: ""
        val savedJwt   = prefs.getString("jwt", "") ?: ""
        tvCredStatus!!.text = when {
            savedEmail.isNotEmpty() -> "✅ $savedEmail"
            savedJwt.isNotEmpty()   -> "✅ JWT saved"
            else                    -> "⚠ No credentials"
        }
        tvCredStatus!!.setTextColor(if (savedEmail.isNotEmpty() || savedJwt.isNotEmpty()) 0xFF00ff88.toInt() else 0xFFffcc00.toInt())

        ll.addView(btn("💾 Save & Connect") { saveCreds() })

        ll.addView(section("🔐 Manual JWT"))
        etJwt = input("eyJhbGci...", false)
        etJwt!!.setText(savedJwt.take(60))
        ll.addView(etJwt)
        ll.addView(btn("💾 Save JWT") {
            val j = etJwt!!.text.toString().trim()
            if (j.isNotEmpty()) { prefs.edit().putString("jwt", j).apply(); toast("JWT saved!") }
        })
        ll.addView(divider())

        // ── FEATURES ──
        ll.addView(section("⚙ Features"))
        swNether  = switch("🔥 Auto Nether",  BotService.instance?.autoNether  == true) { BotService.instance?.autoNether  = it; if (it) startBotIfNeeded() }
        swMine    = switch("⛏ Auto Mine",     BotService.instance?.autoMine    == true) { BotService.instance?.autoMine    = it; if (it) startBotIfNeeded() }
        swAfk     = switch("🤖 Anti-AFK",     BotService.instance?.antiAfk     == true) { BotService.instance?.antiAfk     = it }
        swCollect = switch("💎 Auto Collect",  BotService.instance?.autoCollect == true) { BotService.instance?.autoCollect = it }
        ll.addView(swNether); ll.addView(swMine); ll.addView(swAfk); ll.addView(swCollect)
        ll.addView(divider())

        // ── QUICK ACTIONS ──
        ll.addView(section("🚀 Quick Actions"))
        ll.addView(btn("🔥 Enter Nether Now") { BotService.instance?.joinNether() })
        ll.addView(btn("⛏ Enter Mine Now")    { BotService.instance?.joinMine()   })
        ll.addView(btn("🎮 Open Pixel Worlds") { openGame() })
        ll.addView(divider())

        // ── STATS ──
        ll.addView(section("📊 Stats"))
        tvGems     = stat("Gems: 0");          ll.addView(tvGems)
        tvCrystals = stat("Crystals: 0");      ll.addView(tvCrystals)
        tvKeys     = stat("Keys: 0/4");        ll.addView(tvKeys)
        tvRuns     = stat("Runs: 0");          ll.addView(tvRuns)
        tvTime     = stat("Time: 0:00");       ll.addView(tvTime)
        ll.addView(divider())

        // ── LOG ──
        ll.addView(section("📋 Log"))
        tvStatus = TextView(this).apply {
            setTextColor(0xFF00ff88.toInt()); textSize = 10f
            setPadding(0, dp(2), 0, dp(2))
            text = "Ready"
        }
        ll.addView(tvStatus)
        ll.addView(divider())

        // ── STOP ──
        ll.addView(btn("⏹ Stop & Close", 0x22FF4444.toInt(), 0xFFff4444.toInt()) {
            BotService.instance?.stopBot()
            stopSelf()
        })

        sv.addView(ll)
        return sv
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────

    private fun saveCreds() {
        val email = etEmail!!.text.toString().trim()
        val pass  = etPass!!.text.toString().trim()
        if (email.isEmpty() || pass.isEmpty()) { toast("Fill both fields"); return }

        // Enable input for focus
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        wm.updateViewLayout(root, params)

        prefs.edit().putString("email", email).putString("password", pass).apply()
        BotService.instance?.auth?.saveCredentials(email, pass)
        tvCredStatus?.text = "✅ $email"; tvCredStatus?.setTextColor(0xFF00ff88.toInt())

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(root, params)
        toast("Saved! Starting bot…")
        startBotIfNeeded()
    }

    private fun startBotIfNeeded() {
        val svc = BotService.instance ?: return
        if (!svc.running) svc.startBot()
    }

    private fun openGame() {
        val pkgs = listOf("com.kukouri.wizworld", "com.kukouri.pixelworlds", "com.kukouri.PixelWorlds")
        for (pkg in pkgs) {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return }
        }
        toast("Pixel Worlds not found")
    }

    private fun togglePanel() {
        panelOpen = !panelOpen
        panel!!.visibility = if (panelOpen) View.VISIBLE else View.GONE
        fab!!.background = oval(
            if (panelOpen) 0xDDe94560.toInt() else 0xDD1a1a2e.toInt(),
            0xFFe94560.toInt(), 3
        )
    }

    // ── STATS REFRESH ─────────────────────────────────────────────────────────

    private fun refreshStats() {
        val bot = BotService.instance ?: return
        tvGems?.text     = "Gems: ${bot.totalGems}"
        tvCrystals?.text = "Crystals: ${bot.totalCrystals}"
        tvKeys?.text     = "Keys: ${bot.totalKeys}/4"
        tvRuns?.text     = "Runs: ${bot.totalRuns}"
        tvTime?.text     = "Time: ${formatDuration(bot.getSessionDuration())}"
    }

    private fun connectBotCallbacks() {
        BotService.statusListener = { msg ->
            handler.post {
                if (logLines.size >= 8) logLines.removeFirst()
                logLines.addLast(msg)
                tvStatus?.text = logLines.joinToString("\n")
            }
        }
        BotService.statsListener = { stats ->
            handler.post {
                tvGems?.text     = "Gems: ${stats.gemsCollected}"
                tvCrystals?.text = "Crystals: ${stats.crystalsCollected}"
                tvKeys?.text     = "Keys: ${stats.keysCollected}/4"
                tvRuns?.text     = "Runs: ${stats.runsCompleted}"
            }
        }
        BotService.connectedListener = { connected ->
            handler.post {
                fab?.background = oval(
                    if (connected) 0xDD0d4f2c.toInt() else 0xDD1a1a2e.toInt(),
                    0xFFe94560.toInt(), 3
                )
            }
        }
    }

    // ── DRAG ──────────────────────────────────────────────────────────────────

    private fun setupDrag() {
        fab!!.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTX = e.rawX; initTY = e.rawY; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - initTX; val dy = e.rawY - initTY
                    if (dx.abs() > 8 || dy.abs() > 8) dragging = true
                    if (dragging) {
                        params.x = initX + dx.toInt(); params.y = initY + dy.toInt()
                        wm.updateViewLayout(root, params)
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("ox", params.x).putInt("oy", params.y).apply()
                    if (!dragging) v.performClick()
                    true
                }
                else -> false
            }
        }
        fab!!.setOnClickListener { togglePanel() }
    }

    // ── UI HELPERS ────────────────────────────────────────────────────────────

    private fun section(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFFe94560.toInt()); textSize = 12f
        typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, dp(2))
    }

    private fun stat(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFFcccccc.toInt()); textSize = 11f
        setPadding(0, dp(1), 0, dp(1))
    }

    private fun input(hint: String, password: Boolean) = EditText(this).apply {
        this.hint = hint; setHintTextColor(0xFF444466.toInt())
        setTextColor(Color.WHITE); textSize = 12f; setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(0xFF0f0f1a.toInt(), 0xFF333355.toInt(), 1, dp(8).toFloat())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, dp(3), 0, dp(5)) }
    }

    private fun btn(
        t: String, bg: Int = 0x22e94560.toInt(), border: Int = 0xFFe94560.toInt(),
        action: () -> Unit
    ) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
        setPadding(0, dp(9), 0, dp(9))
        background = rounded(bg, border, 1, dp(8).toFloat())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(4)) }
        setOnClickListener { action() }
    }

    private fun switch(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val tv = text(label, Color.WHITE, 13f, weight = 1f)
        val sw = Switch(this).apply { isChecked = initial; setOnCheckedChangeListener { _, b -> onChange(b) } }
        row.addView(tv); row.addView(sw)
        return row
    }

    private fun text(t: String, color: Int, size: Float, bold: Boolean = false, weight: Float = 0f) =
        TextView(this).apply {
            text = t; setTextColor(color); textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            layoutParams = if (weight > 0) LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

    private fun row(vararg views: View): LinearLayout {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        views.forEach { ll.addView(it) }; return ll
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0x33e94560.toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .also { it.setMargins(0, dp(6), 0, dp(6)) }
    }

    private fun oval(fill: Int, stroke: Int, sw: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(fill); setStroke(sw, stroke)
    }

    private fun rounded(fill: Int, stroke: Int, sw: Int, r: Float) = GradientDrawable().apply {
        setColor(fill); setStroke(sw, stroke); cornerRadius = r
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun Float.abs() = kotlin.math.abs(this)
    private fun toast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    private fun formatDuration(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val h = m / 60
        return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60) else "%d:%02d".format(m, s % 60)
    }
}
