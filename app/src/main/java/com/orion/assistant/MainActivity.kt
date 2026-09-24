package com.orion.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
        }

        val title = TextView(this).apply {
            text = "ORION AI Assistant"
            textSize = 24f
            setPadding(0, 0, 0, 40)
        }

        val btnNotification = Button(this).apply {
            text = "1. Enable Notification Access (VIP/GF Alert)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val btnOverlay = Button(this).apply {
            text = "2. Enable Floating Bubble Permission"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Overlay Active Hai!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnStart = Button(this).apply {
            text = "3. Launch ORION Bubble"
            setOnClickListener {
                startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                Toast.makeText(this@MainActivity, "ORION Active Ho Gaya Ankit Bhai!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        layout.addView(title)
        layout.addView(btnNotification)
        layout.addView(btnOverlay)
        layout.addView(btnStart)

        setContentView(layout)
    }
}
