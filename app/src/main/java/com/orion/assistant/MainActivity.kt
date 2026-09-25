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
    private lateinit var homeLayout: LinearLayout
    private lateinit var chatLayout: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var drawerLayout: LinearLayout
    private lateinit var mainContent: FrameLayout
    private var isDrawerOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050811"))
        }

        // RGB Border Sweep View
        val borderView = OrionBorderSweepView(this)
        root.addView(borderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        mainContent = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val baseLinear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // --- TOP NAVIGATION BAR ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 40, 30, 20)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val btnMenu = TextView(this).apply {
            text = "☰"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 0, 30, 0)
            setOnClickListener { toggleDrawer() }
        }
        topBar.addView(btnMenu)

        val tvTitle = TextView(this).apply {
            text = "ORION SENTINEL // AI"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(tvTitle)

        val btnKeyConfig = TextView(this).apply {
            text = "🔑 KEYS"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setColor(Color.parseColor("#152238"))
                setStroke(2, Color.parseColor("#76FF03"))
            }
            setOnClickListener { showDualKeyDialog() }
        }
        topBar.addView(btnKeyConfig)
        baseLinear.addView(topBar)

        // --- CONTENT AREA (HOME & CHAT) ---
        val viewPagerArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 1. HOME TAB
        homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 20, 40, 20)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        tvStatus = TextView(this).apply {
            text = "STATUS: STANDBY (TAP ORB FOR CONTINUOUS AI)"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        homeLayout.addView(tvStatus)

        val orb = OrionArcOrbView(this).apply {
            val size = (220 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 20, 0, 30)
            }
            isClickable = true
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (engine?.isContinuousMode == true) {
                        engine?.stopListening()
                        tvStatus.text = "STATUS: STANDBY (TAP TO TALK)"
                        Toast.makeText(this@MainActivity, "Voice mode paused", Toast.LENGTH_SHORT).show()
                    } else {
                        engine?.isContinuousMode = true
                        engine?.startListening()
                        tvStatus.text = "STATUS: CONTINUOUS LIVE LISTENING..."
                        Toast.makeText(this@MainActivity, "Continuous listening ON", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                }
            }
        }
        homeLayout.addView(orb)

        val btnFloat = Button(this).apply {
            text = "🚀 LAUNCH FLOATING ORION"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#00E5FF"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 10, 0, 10)
            layoutParams = lp
            setOnClickListener {
                startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            }
        }
        homeLayout.addView(btnFloat)
        viewPagerArea.addView(homeLayout)

        // 2. CHAT TAB
        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(25, 20, 25, 20)
        }

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        chatScroll.addView(chatContainer)
        chatLayout.addView(chatScroll)
        viewPagerArea.addView(chatLayout)

        baseLinear.addView(viewPagerArea)

        // --- BOTTOM TAB BAR ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val btnTabHome = Button(this).apply {
            text = "🏠 HOME"
            setTextColor(Color.parseColor("#00E5FF"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                homeLayout.visibility = View.VISIBLE
                chatLayout.visibility = View.GONE
            }
        }

        val btnTabChat = Button(this).apply {
            text = "💬 HISTORY"
            setTextColor(Color.parseColor("#8892B0"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                homeLayout.visibility = View.GONE
                chatLayout.visibility = View.VISIBLE
            }
        }
        bottomBar.addView(btnTabHome)
        bottomBar.addView(btnTabChat)
        baseLinear.addView(bottomBar)

        mainContent.addView(baseLinear)
        root.addView(mainContent)

        // --- DRAWER SLIDE PANEL (Three-line Menu) ---
        drawerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0F1D"))
            setPadding(40, 60, 40, 40)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.85).toInt(), FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val drawerScroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvDrawerHead = TextView(this).apply {
            text = "ORION SYSTEM PERMISSIONS"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 30)
        }
        drawerContent.addView(tvDrawerHead)

        addPermissionItem(drawerContent, "Microphone (RECORD_AUDIO)", "Voice STT loop & continuous engine") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }
        addPermissionItem(drawerContent, "Camera (CAMERA)", "Intruder security vision capture") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 102)
        }
        addPermissionItem(drawerContent, "Phone / Calls (CALL_PHONE)", "Dialer control and call screening") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE), 103)
        }
        addPermissionItem(drawerContent, "SMS / Messages (READ_SMS)", "Scam alert & message detection") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS), 104)
        }
        addPermissionItem(drawerContent, "Location (GPS)", "Real-time navigation and alerts") {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 105)
        }

        val btnKill = Button(this).apply {
            text = "🔴 EMERGENCY KILL SWITCH"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#D50000"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 40, 0, 20)
            layoutParams = lp
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                engine?.destroy()
                finishAffinity()
            }
        }
        drawerContent.addView(btnKill)

        drawerScroll.addView(drawerContent)
        drawerLayout.addView(drawerScroll)
        root.addView(drawerLayout)

        setContentView(root)

        // Init Engine
        engine = OrionEngine(
            this,
            onStatus = { status ->
                runOnUiThread { tvStatus.text = status }
            },
            onMessage = { msg, isUser ->
                runOnUiThread { addChatMessage(msg, isUser) }
            }
        )
    }

    private fun toggleDrawer() {
        isDrawerOpen = !isDrawerOpen
        drawerLayout.visibility = if (isDrawerOpen) View.VISIBLE else View.GONE
    }

    private fun addPermissionItem(parent: LinearLayout, title: String, desc: String, onGrant: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(Color.parseColor("#111A2E"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 20)
            layoutParams = lp
        }

        val tvT = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tvD = TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#8892B0"))
            textSize = 11f
            setPadding(0, 4, 0, 10)
        }
        val btn = Button(this).apply {
            text = "VERIFY / GRANT"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(Color.parseColor("#182A45"))
            }
            setOnClickListener { onGrant() }
        }

        item.addView(tvT)
        item.addView(tvD)
        item.addView(btn)
        parent.addView(item)
    }

    private fun addChatMessage(text: String, isUser: Boolean) {
        val msgView = TextView(this).apply {
            this.text = (if (isUser) "YOU: " else "ORION: ") + text
            setTextColor(if (isUser) Color.parseColor("#00E5FF") else Color.parseColor("#76FF03"))
            textSize = 13f
            setPadding(25, 20, 25, 20)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(if (isUser) Color.parseColor("#0E1D33") else Color.parseColor("#16281E"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                setMargins(0, 10, 0, 10)
            }
            layoutParams = lp
        }
        chatContainer.addView(msgView)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showDualKeyDialog() {
        val prefs = getSharedPreferences("orion_config", Context.MODE_PRIVATE)
        val curGroq = prefs.getString("groq_key", "") ?: ""
        val curGemini = prefs.getString("gemini_key", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etGroq = EditText(this).apply {
            hint = "Groq Key (gsk_...)"
            setText(curGroq)
        }
        val etGemini = EditText(this).apply {
            hint = "Gemini Key (AIzaSy...)"
            setText(curGemini)
        }

        layout.addView(TextView(this).apply { text = "Primary: Groq Llama-3.3 Key"; textSize = 12f; setTextColor(Color.GRAY) })
        layout.addView(etGroq)
        layout.addView(TextView(this).apply { text = "\nFallback: Google Gemini Key"; textSize = 12f; setTextColor(Color.GRAY) })
        layout.addView(etGemini)

        AlertDialog.Builder(this)
            .setTitle("AI Engine Config")
            .setView(layout)
            .setPositiveButton("Save Keys") { _, _ ->
                prefs.edit()
                    .putString("groq_key", etGroq.text.toString().trim())
                    .putString("gemini_key", etGemini.text.toString().trim())
                    .apply()
                Toast.makeText(this, "API Keys Updated Successfully", Toast.LENGTH_SHORT).show()
                engine?.speak("Dono API keys save ho gayi hain boss.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.destroy()
    }
}
