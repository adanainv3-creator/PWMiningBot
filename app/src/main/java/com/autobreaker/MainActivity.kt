package com.autobreaker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.content.ComponentName

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(buildUI())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildUI(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0d1b2a")) }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Icon
        ll.addView(TextView(this).apply {
            text = "⛏"; textSize = 72f; gravity = Gravity.CENTER
            setPadding(0,0,0,dp(8))
        })

        // Title
        ll.addView(TextView(this).apply {
            text = "AutoBreaker"
            setTextColor(Color.parseColor("#00aaff")); textSize = 30f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        ll.addView(TextView(this).apply {
            text = "Pixel Worlds Otomatik Blok Kırıcı"
            setTextColor(Color.parseColor("#666666")); textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(20))
        })

        // Status card
        tvStatus = TextView(this).apply {
            text = "Checking…"; textSize = 13f; gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.parseColor("#111827"), Color.parseColor("#00aaff"), 1, dp(10).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        }
        ll.addView(tvStatus)

        // Enable Accessibility button
        btnToggle = Button(this).apply {
            textSize = 15f; setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#005522"), Color.parseColor("#00ff88"), 2, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { onToggleClick() }
        }
        ll.addView(btnToggle)

        // Open Pixel Worlds button
        ll.addView(Button(this).apply {
            text = "🎮 Pixel Worlds'i Aç"
            textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#001833"), Color.parseColor("#0066cc"), 1, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(24) }
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { openGame() }
        })

        // How it works
        ll.addView(card(buildHowTo()))

        scroll.addView(ll); return scroll
    }

    private fun buildHowTo(): LinearLayout {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(TextView(this).apply {
            text = "⚙ Nasıl Çalışır"
            setTextColor(Color.parseColor("#00aaff")); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8))
        })
        listOf(
            "1. Erişilebilirlik iznini etkinleştir",
            "2. Bu uygulamayı kapat, Pixel Worlds'ü aç",
            "3. Overlay ⛏ paneli görünür — START'a bas",
            "4. Bot blokları otomatik kırar (sol→sağ, yukarı→aşağı)",
            "5. Harita bitince sağa scroll yapar, devam eder",
            "6. Speed: Slow/Normal/Fast/Ultra seçilebilir",
            "7. STOP'a basınca durur"
        ).forEach {
            TextView(this).apply {
                text = it; setTextColor(Color.parseColor("#888888")); textSize = 11f
                setPadding(0, dp(2), 0, dp(2))
            }.also { v -> ll.addView(v) }
        }
        return ll
    }

    private fun onToggleClick() {
        if (isAccessibilityEnabled()) {
            // Already enabled — open Pixel Worlds
            openGame()
        } else {
            // Open accessibility settings
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            tvStatus.text = "AutoBreaker'ı listede bulup AÇ"
        }
    }

    private fun updateStatus() {
        if (isAccessibilityEnabled()) {
            tvStatus.text = "✅ Hazır — Pixel Worlds'ü açıp oynayın"
            tvStatus.setTextColor(Color.parseColor("#00ff88"))
            btnToggle.text = "🎮 Pixel Worlds'i Aç"
        } else {
            tvStatus.text = "⚠ Erişilebilirlik izni gerekli"
            tvStatus.setTextColor(Color.parseColor("#ffcc00"))
            btnToggle.text = "⚙ Erişilebilirlik İznini Etkinleştir"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${AutoBreakerService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.split(":").any { it.equals(service, ignoreCase = true) }
        } catch (e: Exception) { false }
    }

    private fun openGame() {
        listOf("com.kukouri.wizworld", "com.kukouri.pixelworlds").forEach { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it); return }
        }
        Toast.makeText(this, "Pixel Worlds bulunamadı", Toast.LENGTH_SHORT).show()
    }

    private fun card(content: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Color.parseColor("#111827"), Color.parseColor("#1a3a5c"), 1, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(content)
    }

    private fun rounded(fill: Int, stroke: Int, sw: Int, r: Float) = GradientDrawable().apply {
        setColor(fill); setStroke(sw, stroke); cornerRadius = r
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
