package com.orion.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

class OrionBorderSweepView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    private var offset = 0f
    private val colors = intArrayOf(
        Color.parseColor("#00E5FF"),
        Color.parseColor("#76FF03"),
        Color.parseColor("#FF0055"),
        Color.parseColor("#D500F9"),
        Color.parseColor("#00E5FF")
    )

    init {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                offset = it.animatedValue as Float
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

        val shader = LinearGradient(
            0f, offset * h,
            w, (1f - offset) * h,
            colors, null, Shader.TileMode.MIRROR
        )
        paint.shader = shader
        canvas.drawRect(7f, 7f, w - 7f, h - 7f, paint)
    }
}
