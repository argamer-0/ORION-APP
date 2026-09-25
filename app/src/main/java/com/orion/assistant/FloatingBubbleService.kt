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

    private var wm: WindowManager? = null
    private var edgeOverlay: View? = null
    private var orbOverlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Full Screen 4-Corner Multi-Color RGB Moving Edge Light
        edgeOverlay = BackgroundEdgeView(this)
        val edgeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm?.addView(edgeOverlay, edgeParams)

        // Glassmorphic Maya-Style Invisible Orb
        orbOverlay = MayaFloatingOrb(this)
        val orbParams = WindowManager.LayoutParams(
            220, 220,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 350
        }

        orbOverlay?.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var touchTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = orbParams.x
                        initY = orbParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        touchTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        orbParams.x = initX + (event.rawX - touchX).toInt()
                        orbParams.y = initY + (event.rawY - touchY).toInt()
                        wm?.updateViewLayout(orbOverlay, orbParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchTime
                        if (Math.abs(event.rawX - touchX) < 15 && Math.abs(event.rawY - touchY) < 15) {
                            if (duration > 1500) {
                                Toast.makeText(this@FloatingBubbleService, "ORION Background Stop", Toast.LENGTH_SHORT).show()
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

        wm?.addView(orbOverlay, orbParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (edgeOverlay != null) { wm?.removeView(edgeOverlay); edgeOverlay = null }
        if (orbOverlay != null) { wm?.removeView(orbOverlay); orbOverlay = null }
    }

    class BackgroundEdgeView(context: Context) : View(context) {
        private var rot = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#E91E63"), Color.parseColor("#FF1744"), Color.parseColor("#00E676"), Color.parseColor("#00E5FF")),
                null
            )
            val m = Matrix()
            m.setRotate(rot, width / 2f, height / 2f)
            shader.setLocalMatrix(m)
            p.shader = shader
            canvas.drawRoundRect(RectF(6f, 6f, width - 6f, height - 6f), 40f, 40f, p)
        }
    }

    class MayaFloatingOrb(context: Context) : View(context) {
        private var sweep = 0f
        private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#3000E5FF")
        }
        private val pArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
        }
        private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#80060913")
        }

        init {
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    sweep = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val rad = width.coerceAtMost(height) / 2.4f

            canvas.drawCircle(cx, cy, rad, pBg)
            canvas.drawCircle(cx, cy, rad, pRing)
            canvas.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), sweep, 90f, false, pArc)
            canvas.drawText("O.R.I.O.N", cx, cy + 8f, pText)
        }
    }
}
