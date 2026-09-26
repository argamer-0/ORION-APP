package com.orion.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

        val borderView = OrionBorderSweepView(this)
        root.addView(borderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        mainContent = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val baseLinear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // Top App Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 45, 30, 20)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val btnMenu = TextView(this).apply {
            text = "☰"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 0, 30, 0)
            setOnClickListener { toggleDrawer() }
        }
        topBar.addView(btnMenu)

        val tvTitle = TextView(this).apply {
            text = "ORION SENTINEL // V1"
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
                cornerRadius = 14f
                setColor(Color.parseColor("#142236"))
                setStroke(2, Color.parseColor("#76FF03"))
            }
            setOnClickListener { showDualKeyDialog() }
        }
        topBar.addView(btnKeyConfig)
        baseLinear.addView(topBar)

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
            val size = (240 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 10, 0, 25)
            }
            isClickable = true
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (engine?.isContinuousMode == true) {
                        engine?.stopListening()
                        tvStatus.text = "STATUS: STANDBY"
                    } else {
                        engine?.isContinuousMode = true
                        engine?.startListening()
                    }
                } else {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                }
            }
        }
        homeLayout.addView(orb)

        val btnFloat = Button(this).apply {
            text = "🚀 LAUNCH FLOATING ORION ORB"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.parseColor("#00E5FF"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 10, 0, 10)
            layoutParams = lp
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                }
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

        // Bottom Navigation Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 18, 20, 18)
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
        drawerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0F1D"))
            setPadding(35, 55, 35, 35)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.90).toInt(), FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val drawerScroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvDrawerHead = TextView(this).apply {
            text = "[02] PERMISSION CONTROL PANEL"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 20)
        }
        drawerContent.addView(tvDrawerHead)

        // 1. Microphone
        val micGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Microphone (RECORD_AUDIO)", "Wake-word 'Orion' aur voice reply engine ke liye.", micGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }

        // 2. Camera
        val camGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Camera (CAMERA)", "Intruder photo capture aur security lock ke liye.", camGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 102)
        }

        // 3. Phone Call State
        val callGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Phone Call State (CALL & STATE)", "Scam call screening aur auto-dial ke liye.", callGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CALL_PHONE, android.Manifest.permission.READ_PHONE_STATE), 103)
        }

        // 4. SMS Access
        val smsGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "SMS Access (READ & RECEIVE)", "OTP read, fraud check aur voice reply ke liye.", smsGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS), 104)
        }

        // 5. Contacts
        val conGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Contacts (READ_CONTACTS)", "True caller name pehchanne aur reminders ke liye.", conGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS), 105)
        }

        // 6. Location
        val locGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Location (FINE & COARSE)", "Emergency location share aur navigation help ke liye.", locGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 106)
        }

        // 7. Notification Access
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        addPermissionCard(drawerContent, "Notification Access", "VIP/WhatsApp & App summaries detect karne ke liye.", notifGranted) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 107)
                }
            }
        }

        // 8. Floating Bubble
        val bubbleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        addPermissionCard(drawerContent, "Floating Bubble (Draw Over Apps)", "Screen ke upar floating glowing orb dikhane ke liye.", bubbleGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        // 9. Battery Optimization
        addPermissionCard(drawerContent, "Battery Optimization Exemption", "Background me service kill na ho isliye exemption.", true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }

        // 10. Calendar Access
        val calGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(drawerContent, "Calendar Access", "Daily events read aur schedule auto-booking ke liye.", calGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR), 108)
        }

        // Kill Switch Button
        val btnKill = Button(this).apply {
            text = "🔴 EMERGENCY KILL SWITCH (STOP ALL)"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#D50000"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 30, 0, 20)
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

        engine = OrionEngine(
            this,
            onStatus = { status ->
                runOnUiThread { tvStatus.text = status }
            },
            onMessage = { msg, isUser ->
                runOnUiThread { addChatMessage(msg, isUser) }
            }
        )

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun toggleDrawer() {
        isDrawerOpen = !isDrawerOpen
        drawerLayout.visibility = if (isDrawerOpen) View.VISIBLE else View.GONE
    }

    private fun addPermissionCard(parent: LinearLayout, title: String, desc: String, isGranted: Boolean, onGrant: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 22, 26, 22)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#0C1322"))
                setStroke(2, Color.parseColor("#1B2A4A"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp
        }

        val tvT = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tvD = TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#8892B0"))
            textSize = 12f
            setPadding(0, 6, 0, 14)
        }
        val btn = Button(this).apply {
            text = if (isGranted) "✔ GRANTED & ACTIVE" else "Enable Permission"
            setTextColor(if (isGranted) Color.parseColor("#76FF03") else Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (isGranted) Color.parseColor("#102B1E") else Color.parseColor("#14253B"))
                setStroke(2, if (isGranted) Color.parseColor("#76FF03") else Color.parseColor("#00E5FF"))
            }
            setOnClickListener { onGrant() }
        }

        card.addView(tvT)
        card.addView(tvD)
        card.addView(btn)
        parent.addView(card)
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
                val gK = etGroq.text.toString().trim()
                val gmK = etGemini.text.toString().trim()
                prefs.edit()
                    .putString("groq_key", gK)
                    .putString("gemini_key", gmK)
                    .apply()
                Toast.makeText(this, "API Keys Updated Successfully", Toast.LENGTH_SHORT).show()
                engine?.speak("Dono API keys update ho gayi hain boss.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.destroy()
    }
}
