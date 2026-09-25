package com.orion.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import android.app.AlertDialog
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private var engine: OrionEngine? = null
    private var engine: OrionEngine? = null

    private lateinit var tvBattery: TextView
    private lateinit var tvSecurity: TextView
    private lateinit var tvMode: TextView
    private lateinit var orbView: ArcOrbView
    private lateinit var btnKillSwitch: Button
    private lateinit var btnLaunchOrb: Button

    // Permission Buttons List
    private val permissionMap = mutableMapOf<String, Button>()

    
    private fun showGroqKeyDialog() {
        val input = EditText(this).apply {
            hint = "gsk_..."
            setTextColor(Color.WHITE)
            setText(getSharedPreferences("orion_config", Context.MODE_PRIVATE).getString("groq_key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Groq API Key")
            .setMessage("Save your Groq Llama-3.3 API Key:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val k = input.text.toString().trim()
                getSharedPreferences("orion_config", Context.MODE_PRIVATE).edit().putString("groq_key", k).apply()
                Toast.makeText(this, "API Key Saved Successfully!", Toast.LENGTH_SHORT).show()
                engine?.speak("API Key save ho gayi hai boss.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root Container with Ambient Edge Border Glow (Four Corner Light)
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050811"))
        }

        val ambientBorder = View(this).apply {
            val stroke = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(4, Color.parseColor("#00E676"))
            }
            background = stroke
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(ambientBorder)

        // Ambient Border Pulse Animation
        val borderAnim = ObjectAnimator.ofFloat(ambientBorder, "alpha", 0.2f, 0.85f).apply {
            duration = 2200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val scrollContainer = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 70, 50, 90)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. TOP STATUS BAR (Battery | Security | Mode)
        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0D131F"))
                setStroke(2, Color.parseColor("#1B2A3D"))
            }
        }

        tvBattery = createStatusItem("⚡ BAT: --%", 1f)
        tvSecurity = createStatusItem("🔒 OWNER: ANKIT", 1f)
        tvMode = createStatusItem("🛡 SILENT GUARDIAN", 1f)

        statusBar.addView(tvBattery)
        statusBar.addView(tvSecurity)
        statusBar.addView(tvMode)
        contentLayout.addView(statusBar)

        // Title Section
        val titleText = TextView(this).apply {
            text = "PROJECT ORION // PHONE V1"
            textSize = 20f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 10)
        }
        val subText = TextView(this).apply {
            text = "AI SENTINEL // CLASSIFICATION: OWNER-ONLY"
            textSize = 12f
            setTextColor(Color.parseColor("#78909C"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        contentLayout.addView(titleText)
        contentLayout.addView(subText)

        // 2. CENTER BREATHING ARC-ORB
        orbView = ArcOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(360, 360).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 10, 0, 50)
            }
        }
        contentLayout.addView(orbView)

        // Quick Launch Floating Orb Button
        btnLaunchOrb = Button(this).apply {
            text = "🚀 LAUNCH FLOATING ORION ORB"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#00E5FF"))
            }
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 40)
            }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Floating permission enable kijiye pehle!", Toast.LENGTH_SHORT).show()
                } else {
                    startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                    Toast.makeText(this@MainActivity, "ORION Orb Activated! Tap to speak, Hold to dismiss.", Toast.LENGTH_LONG).show()
                }
            }
        }
        contentLayout.addView(btnLaunchOrb)

        // 3. CONTROL PANEL (PERMISSIONS LIST ACCORDING TO BLUEPRINT V1)
        val panelHeader = TextView(this).apply {
            text = "[02] PERMISSION CONTROL PANEL"
            textSize = 14f
            setTextColor(Color.parseColor("#00E676"))
            typeface = Typeface.MONOSPACE
            setPadding(10, 20, 0, 20)
        }
        contentLayout.addView(panelHeader)

        // Setup individual cards matching Blueprint
        addPermissionCard(contentLayout, "AUDIO", "Microphone (RECORD_AUDIO)", "Wake-word 'Orion' aur voice reply engine ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
        addPermissionCard(contentLayout, "CAMERA", "Camera (CAMERA)", "Intruder photo capture aur security lock ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 102)
        }
        addPermissionCard(contentLayout, "PHONE", "Phone Call State (CALL & STATE)", "Scam call screening aur auto-dial ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE), 103)
        }
        addPermissionCard(contentLayout, "SMS", "SMS Access (READ & RECEIVE)", "OTP read, fraud check aur voice reply ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), 104)
        }
        addPermissionCard(contentLayout, "CONTACTS", "Contacts (READ_CONTACTS)", "True caller name pehchanne aur reminders ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 105)
        }
        addPermissionCard(contentLayout, "LOCATION", "Location (FINE & COARSE)", "Emergency location share aur navigation help ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 106)
        }
        addPermissionCard(contentLayout, "NOTIFICATIONS", "Notification Access", "VIP/GF WhatsApp & App summaries detect karne ke liye.") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        addPermissionCard(contentLayout, "OVERLAY", "Floating Bubble (Draw Over Apps)", "Screen ke upar floating glowing orb dikhane ke liye.") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        addPermissionCard(contentLayout, "BATTERY", "Battery Optimization Exemption", "Background me service kill na ho isliye exemption.") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
        addPermissionCard(contentLayout, "CALENDAR", "Calendar Access", "Daily events read aur schedule auto-booking ke liye.") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 107)
        }

        // 4. EMERGENCY KILL SWITCH (RED BUTTON)
        btnKillSwitch = Button(this).apply {
            text = "🛑 EMERGENCY KILL SWITCH (STOP ALL)"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#D50000"))
            }
            setPadding(30, 35, 30, 35)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 50, 0, 40)
            }
            layoutParams = params
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                Toast.makeText(this@MainActivity, "ORION Emergency Protocol: Saari Background Services Band!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        contentLayout.addView(btnKillSwitch)

        scrollContainer.addView(contentLayout)
        rootLayout.addView(scrollContainer)
        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        updateStatusHeader()
        refreshPermissionStates()
    }

    private fun updateStatusHeader() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        tvBattery.text = "⚡ BAT: $batLevel%"
    }

    private fun addPermissionCard(parent: LinearLayout, key: String, title: String, desc: String, onGrant: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 30)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0D131F"))
                setStroke(2, Color.parseColor("#1B2A3D"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            layoutParams = params
        }

        val cardTitle = TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val cardDesc = TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#78909C"))
            setPadding(0, 8, 0, 20)
        }

        val btn = Button(this).apply {
            text = "Enable Permission"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#151D2A"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            setPadding(25, 20, 25, 20)
            setOnClickListener { onGrant() }
        }

        permissionMap[key] = btn

        card.addView(cardTitle)
        card.addView(cardDesc)
        card.addView(btn)
        parent.addView(card)
    }

    private fun refreshPermissionStates() {
        checkAndApply("AUDIO", ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("CAMERA", ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("PHONE", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("SMS", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("CONTACTS", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("LOCATION", ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        checkAndApply("CALENDAR", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)

        // Notification listener check
        val notifGranted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        checkAndApply("NOTIFICATIONS", notifGranted)

        // Overlay check
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        checkAndApply("OVERLAY", overlayGranted)

        // Battery optimization
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(packageName) else true
        checkAndApply("BATTERY", batteryIgnored)
    }

    private fun checkAndApply(key: String, isGranted: Boolean) {
        val btn = permissionMap[key] ?: return
        if (isGranted) {
            btn.text = "✔ GRANTED & ACTIVE"
            btn.setTextColor(Color.parseColor("#00E676"))
            btn.background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#1B5E20"))
                setStroke(2, Color.parseColor("#00E676"))
            }
            btn.isEnabled = false
        }
    }

    private fun createStatusItem(text: String, weight: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    // Custom Arc-Reactor Breathing Orb Component
    class ArcOrbView(context: Context) : View(context) {
        private var breathProgress = 0f
        private val paintCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.parseColor("#00E676")
        }
        private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#00E5FF")
        }
        private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1100E676")
        }

        init {
            ValueAnimator.ofFloat(0.85f, 1.15f).apply {
                duration = 1800
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    breathProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val baseRadius = (width.coerceAtMost(height) / 2.6f) * breathProgress

            canvas.drawCircle(cx, cy, baseRadius * 0.9f, paintGlow)
            canvas.drawCircle(cx, cy, baseRadius, paintCore)
            canvas.drawCircle(cx, cy, baseRadius * 1.25f, paintRing)
        }
    }
}
