package com.example.link2sdclone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/** دائرة نسب: الأزرق = ذاكرة الهاتف، البرتقالي = البطاقة، والنسبة المئوية للهاتف في الوسط. */
class PieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var internalPercent = 100
    private val internalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val sdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9800") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val oval = RectF()

    fun setInternalPercent(percent: Int) {
        internalPercent = percent.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        oval.set(left, top, left + size, top + size)
        if (internalPercent >= 100) {
            canvas.drawArc(oval, 0f, 360f, true, internalPaint)
        } else if (internalPercent <= 0) {
            canvas.drawArc(oval, 0f, 360f, true, sdPaint)
        } else {
            val sweep = internalPercent * 3.6f
            canvas.drawArc(oval, -90f, sweep, true, internalPaint)
            canvas.drawArc(oval, -90f + sweep, 360f - sweep, true, sdPaint)
        }
        val cy = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("$internalPercent%", width / 2f, cy, textPaint)
    }
}
