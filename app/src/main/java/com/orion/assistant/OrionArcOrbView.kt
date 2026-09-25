package com.orion.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

class OrionArcOrbView(context: Context) : View(context) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#00E5FF")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.parseColor("#76FF03")
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A192F")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var rotationAngle = 0f
    private val rect = RectF()

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 20f
        if (radius <= 0) return

        canvas.drawCircle(cx, cy, radius - 15f, corePaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, rotationAngle, 90f, false, arcPaint)
        canvas.drawArc(rect, rotationAngle + 180f, 90f, false, arcPaint)

        val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText("O.R.I.O.N", cx, textY, textPaint)
    }
}
