package com.orion.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingBubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val orbShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#050811"))
            setStroke(6, Color.parseColor("#00E676"))
        }

        val bubbleView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.parseColor("#00E5FF"))
            background = orbShape
            setPadding(35, 35, 35, 35)
        }
        floatingBubble = bubbleView

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            170, 170, layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 350
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchStartTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingBubble, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchStartTime
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)

                        if (diffX < 20 && diffY < 20) {
                            if (duration > 1200) {
                                Toast.makeText(this@FloatingBubbleService, "ORION Orb Terminated", Toast.LENGTH_SHORT).show()
                                stopSelf()
                            } else {
                                Toast.makeText(this@FloatingBubbleService, "ORION Listening...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(floatingBubble, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingBubble != null) {
            windowManager?.removeView(floatingBubble)
            floatingBubble = null
        }
    }
}
