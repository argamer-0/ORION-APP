package com.orion.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator

class OrionBorderSweepView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private var angle = 0f
    private val colors = intArrayOf(
        Color.parseColor("#00E5FF"),
        Color.parseColor("#76FF03"),
        Color.parseColor("#D500F9"),
        Color.parseColor("#00E5FF")
    )

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
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
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.save()
        canvas.rotate(angle, w / 2f, h / 2f)
        paint.shader = SweepGradient(w / 2f, h / 2f, colors, null)
        canvas.drawRect(5f, 5f, w - 5f, h - 5f, paint)
        canvas.restore()
    }
}
