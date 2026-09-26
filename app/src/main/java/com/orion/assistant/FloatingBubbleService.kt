package com.orion.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    class GlassOrbView(context: Context) : View(context) {
        private var angle = 0f
        private val glassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#440B132B")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8800E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        private val rect = RectF()

        init {
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    angle = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (Math.min(width, height) / 2f) - 10f
            if (radius <= 0) return

            canvas.drawCircle(cx, cy, radius, glassBg)
            canvas.drawCircle(cx, cy, radius, borderPaint)

            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, angle, 90f, false, arcPaint)

            canvas.drawText("O.R.I.O.N", cx, cy + 10f, textPaint)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (140 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val glassOrb = GlassOrbView(this)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isClick = false

        glassOrb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(glassOrb, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
        bubbleView = glassOrb
        windowManager?.addView(bubbleView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager?.removeView(it) }
    }
}
