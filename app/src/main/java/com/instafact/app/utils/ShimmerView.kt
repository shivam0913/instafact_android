package com.instafact.app.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.instafact.app.R

/**
 * A rounded placeholder bar with a sweeping highlight.
 *
 * Hand-rolled rather than pulling in Facebook's shimmer library for three bars: the
 * whole effect is one translated gradient, and the dependency would outweigh it.
 *
 * The animation is tied to window attachment, so it stops when the view leaves the
 * screen instead of spinning forever behind another activity.
 */
class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_border)
    }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var animator: ValueAnimator? = null
    private var progress = 0f

    private val highlight = ContextCompat.getColor(context, R.color.brand_surface)
    private val transparent = ContextCompat.getColor(context, R.color.brand_border) and 0x00FFFFFF

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val radius = height / 2f
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, radius, radius, basePaint)

        // Sweep one highlight band from just off the left edge to just off the right.
        val bandWidth = width * BAND_FRACTION
        val start = -bandWidth + progress * (width + 2 * bandWidth)
        shimmerPaint.shader = LinearGradient(
            start, 0f, start + bandWidth, 0f,
            intArrayOf(transparent, highlight, transparent),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, shimmerPaint)
    }

    private companion object {
        const val SWEEP_DURATION_MS = 1200L
        const val BAND_FRACTION = 0.45f
    }
}
