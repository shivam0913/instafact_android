package com.instafact.app.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.instafact.app.R
import kotlin.math.min

class ConfidenceGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val strokeWidth = context.resources.displayMetrics.density * 10f
    private val arcInset = strokeWidth / 2f + context.resources.displayMetrics.density * 4f
    private val arcBounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@ConfidenceGaugeView.strokeWidth
        color = ContextCompat.getColor(context, R.color.brand_divider)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@ConfidenceGaugeView.strokeWidth
        color = ContextCompat.getColor(context, R.color.brand_status_true)
    }

    private var confidence: Int = 0

    fun setConfidence(value: Int?, @ColorInt color: Int) {
        confidence = (value ?: 0).coerceIn(0, 100)
        progressPaint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = ((width.coerceAtLeast(suggestedMinimumWidth) * 0.62f).toInt())
            .coerceAtLeast((120 * resources.displayMetrics.density).toInt())
        val resolvedWidth = resolveSize(width, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val diameter = min(w.toFloat(), h.toFloat() * 2f) - arcInset * 2f
        val left = (w - diameter) / 2f
        val top = arcInset
        arcBounds.set(left, top, left + diameter, top + diameter)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint)
        canvas.drawArc(arcBounds, 180f, 180f * (confidence / 100f), false, progressPaint)
    }
}
