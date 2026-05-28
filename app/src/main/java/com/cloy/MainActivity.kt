package com.cloy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.content.SharedPreferences

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var etJwt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        prefs = getSharedPreferences("cloy_prefs", MODE_PRIVATE)
        setContentView(buildUI())

        // Request overlay permission immediately on launch
        if (!Settings.canDrawOverlays(this)) requestOverlay()
        else tvStatus.apply { text = "✅ Overlay permission granted"; setTextColor(Color.parseColor("#00ff88")) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001) {
            if (Settings.canDrawOverlays(this)) tvStatus.apply { text = "✅ Overlay granted"; setTextColor(Color.parseColor("#00ff88")) }
            else tvStatus.apply { text = "❌ Overlay denied — bot won't work"; setTextColor(Color.parseColor("#ff4444")) }
        }
    }

    private fun requestOverlay() {
        tvStatus.text = "Overlay permission required…"
        startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 1001)
    }

    private fun buildUI(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0f0f1a")) }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Logo
        ll.addView(TextView(this).apply { text = "⚔"; textSize = 64f; gravity = Gravity.CENTER })
        ll.addView(TextView(this).apply {
            text = "Cloy"; setTextColor(Color.parseColor("#e94560")); textSize = 32f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        ll.addView(TextView(this).apply {
            text = "Pixel Worlds AutoNether Client"; setTextColor(Color.parseColor("#666666"))
            textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24))
        })

        tvStatus = TextView(this).apply {
            text = "Checking permissions…"; setTextColor(Color.parseColor("#ffcc00"))
            textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
        }
        ll.addView(tvStatus)

        // Credentials card
        ll.addView(card(buildCredsSection()))
        ll.addView(spacer(dp(12)))

        // Launch button
        ll.addView(styledBtn("🚀 Launch Cloy Overlay") { launchOverlay() })
        ll.addView(spacer(dp(8)))
        ll.addView(styledBtn("🎮 Open Pixel Worlds", "#16213e") { openGame() })
        ll.addView(spacer(dp(20)))
        ll.addView(buildHowItWorks())

        scroll.addView(ll); return scroll
    }

    private fun buildCredsSection(): LinearLayout {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        layout.addView(sectionTitle("🔑 Credentials"))
        layout.addView(desc("Email/password → auto JWT every 25 min. Or paste manual JWT."))

        etEmail = inputField("Email", prefs.getString("email", "") ?: "")
        etPass  = inputField("Password", prefs.getString("password", "") ?: "", password = true)
        etJwt   = inputField("Manual JWT (optional)", "")

        layout.addView(etEmail); layout.addView(etPass); layout.addView(etJwt)
        layout.addView(styledBtn("💾 Save", "#1a1a2e") { saveCreds() })

        val saved = prefs.getString("email", "") ?: ""
        if (saved.isNotEmpty()) layout.addView(TextView(this).apply {
            text = "✅ $saved"; setTextColor(Color.parseColor("#00ff88")); textSize = 12f
            setPadding(0, dp(6), 0, 0)
        })
        return layout
    }

    private fun buildHowItWorks(): LinearLayout {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(sectionTitle("ℹ How it works").apply { setTextColor(Color.parseColor("#444444")) })
        listOf(
            "1. Grant overlay permission",
            "2. Enter email/password (auto JWT) or paste JWT",
            "3. Launch Cloy overlay → ⚔ button appears",
            "4. Open Pixel Worlds",
            "5. Toggle Auto Nether → bot joins nether",
            "6. BFS finds keys → kills enemies → exits",
            "7. Priority: Survive → Keys → Crystals → Exit"
        ).forEach { ll.addView(TextView(this).apply {
            text = it; setTextColor(Color.parseColor("#444444")); textSize = 12f
            setPadding(0, dp(2), 0, dp(2))
        }) }
        return ll
    }

    private fun saveCreds() {
        val email = etEmail.text.toString().trim()
        val pass  = etPass.text.toString().trim()
        val jwt   = etJwt.text.toString().trim()

        if (email.isNotEmpty() && pass.isNotEmpty()) {
            prefs.edit().putString("email", email).putString("password", pass).apply()
            tvStatus.text = "✅ Credentials saved!"; tvStatus.setTextColor(Color.parseColor("#00ff88"))
        }
        if (jwt.isNotEmpty()) {
            prefs.edit().putString("jwt", jwt).apply()
            tvStatus.text = "✅ JWT saved!"; tvStatus.setTextColor(Color.parseColor("#00ff88"))
        }
        if (email.isEmpty() && jwt.isEmpty()) {
            tvStatus.text = "⚠ Enter email+pass or JWT"; tvStatus.setTextColor(Color.parseColor("#ffcc00"))
        }
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) { requestOverlay(); return }
        val email = prefs.getString("email", "") ?: ""
        val jwt   = prefs.getString("jwt", "") ?: ""
        if (email.isEmpty() && jwt.isEmpty()) {
            tvStatus.text = "⚠ Save credentials first!"; tvStatus.setTextColor(Color.parseColor("#ffcc00")); return
        }
        // Start services
        startService(Intent(this, BotService::class.java))
        startService(Intent(this, OverlayService::class.java))
        tvStatus.text = "✅ Cloy launched! Open Pixel Worlds."; tvStatus.setTextColor(Color.parseColor("#00ff88"))
        Toast.makeText(this, "Cloy overlay active!", Toast.LENGTH_LONG).show()
    }

    private fun openGame() {
        listOf("com.kukouri.wizworld", "com.kukouri.pixelworlds").forEach { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                startActivity(it); return
            }
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kukouri.wizworld")))
    }

    private fun card(content: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#CC16213e")); cornerRadius = dp(12).toFloat()
            setStroke(1, Color.parseColor("#33e94560"))
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(content)
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#e94560")); textSize = 14f
        typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8))
    }

    private fun desc(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#888888")); textSize = 12f; setPadding(0, 0, 0, dp(8))
    }

    private fun inputField(hint: String, value: String, password: Boolean = false) = EditText(this).apply {
        this.hint = hint; setHintTextColor(Color.parseColor("#444466"))
        setTextColor(Color.WHITE); textSize = 12f; setText(value); setSingleLine(true)
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = GradientDrawable().apply { setColor(Color.parseColor("#0f0f1a")); cornerRadius = dp(8).toFloat(); setStroke(1, Color.parseColor("#333333")) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(8)) }
    }

    private fun styledBtn(text: String, color: String = "#e94560", action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 14f
        background = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(10).toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(4)) }
        setPadding(0, dp(12), 0, dp(12)); setOnClickListener { action() }
    }

    private fun spacer(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
