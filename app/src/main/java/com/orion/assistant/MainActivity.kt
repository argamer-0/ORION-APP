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

        val root = RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050811"))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val tvTitle = TextView(this).apply {
            text = "PROJECT ORION // PHONE V1"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }
        content.addView(tvTitle)

        tvStatus = TextView(this).apply {
            text = "STATUS: READY (TAP ORB TO SPEAK)"
            setTextColor(Color.parseColor("#76FF03"))
            textSize = 14f
            setPadding(0, 20, 0, 30)
            gravity = android.view.Gravity.CENTER
        }
        content.addView(tvStatus)

        val btnKey = Button(this).apply {
            text = "🔑 ENTER GROQ API KEY"
            setTextColor(Color.BLACK)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#00E5FF"))
            }
            setPadding(30, 20, 30, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 10, 0, 30)
            layoutParams = lp
            setOnClickListener { showKeyDialog() }
        }
        content.addView(btnKey)

        val orb = OrionArcOrbView(this).apply {
            val size = (220 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 20, 0, 40)
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

        val btnFloat = Button(this).apply {
            text = "🚀 LAUNCH FLOATING ORION ORB"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#00E5FF"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 20, 0, 20)
            layoutParams = lp
            setOnClickListener {
                startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            }
        }
        content.addView(btnFloat)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
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
            .setTitle("Groq API Key")
            .setMessage("Apni Groq API Key paste karein:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val k = input.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("groq_key", k).apply()
                    Toast.makeText(this, "API Key Saved Successfully!", Toast.LENGTH_SHORT).show()
                    engine?.speak("API Key save ho gayi hai boss.")
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
