package com.orion.assistant

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var edgeOverlay: View? = null
    private var orbOverlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Full Screen 4-Corner Moving RGB Light when Minimized
        edgeOverlay = BackgroundEdgeView(this)
        val edgeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(edgeOverlay, edgeParams)

        // Glassmorphic / Invisible Semi-Transparent Center Floating Orb
        val glassOrb = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#80060913")) // Invisible Semi-Transparent Glass
                setStroke(4, Color.parseColor("#00E5FF"))
            }
            setPadding(15, 15, 15, 15)
        }

        val tvOrb = TextView(this).apply {
            text = "O.R.I.O.N"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        glassOrb.addView(tvOrb, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        orbOverlay = glassOrb

        val orbParams = WindowManager.LayoutParams(
            170, 170,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 350
        }

        glassOrb.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var startTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = orbParams.x
                        initY = orbParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        startTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        orbParams.x = initX + (event.rawX - touchX).toInt()
                        orbParams.y = initY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(orbOverlay, orbParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        val dX = Math.abs(event.rawX - touchX)
                        val dY = Math.abs(event.rawY - touchY)

                        if (dX < 15 && dY < 15) {
                            if (duration > 1500) {
                                Toast.makeText(this@FloatingBubbleService, "ORION Background Service Band", Toast.LENGTH_SHORT).show()
                                stopSelf()
                            } else {
                                Toast.makeText(this@FloatingBubbleService, "ORION Standby Listening...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(orbOverlay, orbParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (edgeOverlay != null) {
            windowManager?.removeView(edgeOverlay)
            edgeOverlay = null
        }
        if (orbOverlay != null) {
            windowManager?.removeView(orbOverlay)
            orbOverlay = null
        }
    }

    class BackgroundEdgeView(context: Context) : View(context) {
        private var rot = 0f
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }

        init {
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 4500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rot = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val shader = SweepGradient(
                width / 2f, height / 2f,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#E91E63"),
                    Color.parseColor("#FF1744"),
                    Color.parseColor("#00E676"),
                    Color.parseColor("#00E5FF")
                ),
                null
            )
            val m = Matrix()
            m.setRotate(rot, width / 2f, height / 2f)
            shader.setLocalMatrix(m)
            edgePaint.shader = shader

            val rect = RectF(6f, 6f, width - 6f, height - 6f)
            canvas.drawRoundRect(rect, 40f, 40f, edgePaint)
        }
    }
}
