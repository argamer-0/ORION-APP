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
    private var edgeView: View? = null
    private var orbView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        edgeView = BackgroundBorder(this)
        val edgeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm?.addView(edgeView, edgeParams)

        val glassOrb = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#90060913"))
                setStroke(4, Color.parseColor("#00E5FF"))
            }
            setPadding(10, 10, 10, 10)
        }

        val tv = TextView(this).apply {
            text = "ORION"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        glassOrb.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        orbView = glassOrb
        val orbParams = WindowManager.LayoutParams(
            170, 170,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 90
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
                        wm?.updateViewLayout(orbView, orbParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        if (Math.abs(event.rawX - touchX) < 15 && Math.abs(event.rawY - touchY) < 15) {
                            if (duration > 1500) {
                                Toast.makeText(this@FloatingBubbleService, "ORION Background Stop", Toast.LENGTH_SHORT).show()
                                stopSelf()
                            } else {
                                Toast.makeText(this@FloatingBubbleService, "ORION Active Listening", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
        wm?.addView(orbView, orbParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (edgeView != null) { wm?.removeView(edgeView); edgeView = null }
        if (orbView != null) { wm?.removeView(orbView); orbView = null }
    }

    class BackgroundBorder(context: Context) : View(context) {
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
}
