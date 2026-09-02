package com.instafact.app.ui.coachmark

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Dims the screen and punches a rounded hole over the view being explained.
 *
 * The cutout is drawn with PorterDuff.CLEAR, which only erases pixels within an
 * offscreen buffer - hence the explicit software layer. Without it the "hole" would
 * either be black or do nothing at all depending on the device's hardware pipeline.
 *
 * The hole animates between targets rather than jumping, so the eye can follow it from
 * one step to the next.
 */
class SpotlightView(context: Context) : View(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCRIM_COLOR
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_PX
        color = Color.WHITE
        alpha = RING_ALPHA
    }

    private val current = RectF()
    private val from = RectF()
    private val to = RectF()
    private var animator: ValueAnimator? = null
    private var hasTarget = false

    init {
        // Required for PorterDuff.CLEAR to erase rather than paint black.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Moves the spotlight to [target], animating from wherever it currently is. */
    fun moveTo(target: RectF, animate: Boolean) {
        animator?.cancel()

        if (!hasTarget || !animate) {
            hasTarget = true
            current.set(target)
            invalidate()
            return
        }

        from.set(current)
        to.set(target)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MOVE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                current.set(
                    from.left + (to.left - from.left) * f,
                    from.top + (to.top - from.top) * f,
                    from.right + (to.right - from.right) * f,
                    from.bottom + (to.bottom - from.bottom) * f,
                )
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (!hasTarget) return
        canvas.drawRoundRect(current, CORNER_RADIUS_PX, CORNER_RADIUS_PX, holePaint)
        canvas.drawRoundRect(current, CORNER_RADIUS_PX, CORNER_RADIUS_PX, ringPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        // Dark enough that the highlighted view clearly wins, light enough that the
        // surrounding screen still reads as the app rather than a blank sheet.
        const val SCRIM_COLOR = 0xD9000000.toInt()
        const val CORNER_RADIUS_PX = 20f
        const val RING_WIDTH_PX = 2f
        const val RING_ALPHA = 140
        const val MOVE_DURATION_MS = 320L
    }
}
