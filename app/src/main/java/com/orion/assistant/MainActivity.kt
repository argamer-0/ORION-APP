package com.orion.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private var engine: OrionEngine? = null
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = OrionEngine(this) { msg ->
            runOnUiThread {
                tvStatus.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050811"))
        }

        // 1. RGB Sweep Border View
        val borderView = OrionBorderSweepView(this)
        root.addView(borderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Top Status Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0F1424"))
            }
        }
        val tvOwner = TextView(this).apply {
            text = "⚡ BAT: 100%   🔒 OWNER: ANKIT   🛡️ SENTINEL"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(tvOwner)
        content.addView(topBar)

        val tvTitle = TextView(this).apply {
            text = "\nPROJECT ORION // PHONE V1"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(tvTitle)

        tvStatus = TextView(this).apply {
            text = "AI SENTINEL // STATUS: READY (TAP ORB TO SPEAK)"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 13f
            setPadding(0, 10, 0, 20)
            gravity = Gravity.CENTER
        }
        content.addView(tvStatus)

        // 2. Central Animated Neon Orb
        val orb = OrionArcOrbView(this).apply {
            val size = (200 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 10, 0, 25)
            }
            isClickable = true
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    tvStatus.text = "LISTENING NOW..."
                    engine?.startListening()
                } else {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                }
            }
        }
        content.addView(orb)

        // 3. Floating Orb Launch Button
        val btnFloat = Button(this).apply {
            text = "🚀 LAUNCH FLOATING ORION ORB"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#00E5FF"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 10, 0, 15)
            layoutParams = lp
            setOnClickListener {
                startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            }
        }
        content.addView(btnFloat)

        // 4. BADA KEY BUTTON (Screen par key paste karne ke liye)
        val btnKey = Button(this).apply {
            text = "🔑 SET GROQ API KEY"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#76FF03"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 5, 0, 25)
            layoutParams = lp
            setOnClickListener { showKeyDialog() }
        }
        content.addView(btnKey)

        // 5. Permission Control Cards
        val tvPermHeader = TextView(this).apply {
            text = "[02] PERMISSION CONTROL PANEL"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 15, 0, 15)
        }
        content.addView(tvPermHeader)

        addPermissionCard(content, "Microphone (RECORD_AUDIO)", "Wake-word Orion aur voice reply engine ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }
        addPermissionCard(content, "Camera (CAMERA)", "Intruder capture aur vision tasks ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 102)
        }
        addPermissionCard(content, "Phone Call State (CALL_PHONE)", "Call screening aur dialing ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE), 103)
        }
        addPermissionCard(content, "SMS Access (READ_SMS)", "OTP read aur scam detection ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS), 104)
        }
        addPermissionCard(content, "Location (ACCESS_FINE_LOCATION)", "Navigation aur emergency help ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 105)
        }

        // Kill Switch Button
        val btnKill = Button(this).apply {
            text = "🔴 EMERGENCY KILL SWITCH (STOP ALL)"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#D50000"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 25, 0, 30)
            layoutParams = lp
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                engine?.destroy()
                finishAffinity()
            }
        }
        content.addView(btnKill)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addPermissionCard(parent: LinearLayout, title: String, desc: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 25, 30, 25)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.parseColor("#0C1322"))
                setStroke(2, Color.parseColor("#1B2A4A"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 10, 0, 10)
            layoutParams = lp
        }

        val tvT = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tvD = TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#8892B0"))
            textSize = 12f
            setPadding(0, 6, 0, 15)
        }
        val btnAction = Button(this).apply {
            text = "✔ GRANT / CHECK PERMISSION"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(Color.parseColor("#12253B"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            setOnClickListener { action() }
        }

        card.addView(tvT)
        card.addView(tvD)
        card.addView(btnAction)
        parent.addView(card)
    }

    private fun showKeyDialog() {
        val prefs = getSharedPreferences("orion_config", Context.MODE_PRIVATE)
        val currentKey = prefs.getString("groq_key", "") ?: ""
        val input = EditText(this).apply {
            hint = "gsk_..."
            setTextColor(Color.BLACK)
            setText(currentKey)
        }

        AlertDialog.Builder(this)
            .setTitle("🔑 Groq Llama-3.3 Key")
            .setMessage("Apni Groq API Key yahan paste karein:")
            .setView(input)
            .setPositiveButton("Save Key") { _, _ ->
                val k = input.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("groq_key", k).apply()
                    Toast.makeText(this, "API Key Saved Successfully!", Toast.LENGTH_SHORT).show()
                    engine?.speak("Orion system online. API Key save ho gayi hai boss.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.destroy()
    }
}
