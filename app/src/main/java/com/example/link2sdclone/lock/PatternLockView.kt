package com.example.link2sdclone.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface PatternListener {
        fun onPatternComplete(pattern: List<Int>)
        fun onPatternStart()
    }

    var listener: PatternListener? = null

    private val dotCount = 3
    private val dotPositions = FloatArray(dotCount * dotCount * 2)
    private val selected = mutableListOf<Int>()

    private var currentX = 0f
    private var currentY = 0f
    private var isTracking = false

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cellW = w / dotCount.toFloat()
        val cellH = h / dotCount.toFloat()
        var index = 0
        for (row in 0 until dotCount) {
            for (col in 0 until dotCount) {
                dotPositions[index++] = cellW * col + cellW / 2f
                dotPositions[index++] = cellH * row + cellH / 2f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width / (dotCount * 6f)

        if (selected.size > 1) {
            for (i in 0 until selected.size - 1) {
                val (x1, y1) = pointFor(selected[i])
                val (x2, y2) = pointFor(selected[i + 1])
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }
        if (isTracking && selected.isNotEmpty()) {
            val (x1, y1) = pointFor(selected.last())
            canvas.drawLine(x1, y1, currentX, currentY, linePaint)
        }

        for (i in 0 until dotCount * dotCount) {
            val (x, y) = pointFor(i)
            canvas.drawCircle(x, y, radius, if (selected.contains(i)) selectedDotPaint else dotPaint)
        }
    }

    private fun pointFor(index: Int): Pair<Float, Float> =
        dotPositions[index * 2] to dotPositions[index * 2 + 1]

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selected.clear()
                isTracking = true
                listener?.onPatternStart()
                handleTouch(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                handleTouch(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isTracking = false
                if (selected.size >= 4) {
                    listener?.onPatternComplete(selected.toList())
                }
                postDelayed({ selected.clear(); invalidate() }, 300)
                invalidate()
            }
        }
        return true
    }

    private fun handleTouch(x: Float, y: Float) {
        val radius = width / (dotCount * 4f)
        for (i in 0 until dotCount * dotCount) {
            if (selected.contains(i)) continue
            val (dx, dy) = pointFor(i)
            if (hypot((x - dx).toDouble(), (y - dy).toDouble()) < radius) {
                selected.add(i)
            }
        }
        currentX = x
        currentY = y
    }

    fun reset() {
        selected.clear()
        invalidate()
    }
}
