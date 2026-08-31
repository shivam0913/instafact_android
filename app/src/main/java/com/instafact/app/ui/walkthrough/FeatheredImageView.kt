package com.instafact.app.ui.walkthrough

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.instafact.app.R

/**
 * Image whose edges dissolve into the page instead of ending on a hard rounded rectangle,
 * matching the way the photography is blended in the walkthrough mockup.
 *
 * The bitmap is drawn into an offscreen layer, then alpha gradients are composited with
 * DST_IN so the outer band of each enabled edge fades to transparent. Edges that sit flush
 * against the screen should be left off, otherwise the fade reads as a white band.
 */
class FeatheredImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private var feather = resources.displayMetrics.density * 46f
    private var featherLeft = true
    private var featherTop = true
    private var featherRight = true
    private var featherBottom = true

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.FeatheredImageView)
            feather = a.getDimension(R.styleable.FeatheredImageView_featherSize, feather)
            featherLeft = a.getBoolean(R.styleable.FeatheredImageView_featherLeft, true)
            featherTop = a.getBoolean(R.styleable.FeatheredImageView_featherTop, true)
            featherRight = a.getBoolean(R.styleable.FeatheredImageView_featherRight, true)
            featherBottom = a.getBoolean(R.styleable.FeatheredImageView_featherBottom, true)
            a.recycle()
        }
    }

    private val opaque = 0xFF000000.toInt()
    private val clear = 0x00000000

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) {
            super.onDraw(canvas)
            return
        }

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val edge = feather.coerceAtMost(minOf(w, h) / 2f)

        if (featherLeft) {
            maskPaint.shader = LinearGradient(0f, 0f, edge, 0f, clear, opaque, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, edge, h, maskPaint)
        }
        if (featherRight) {
            maskPaint.shader = LinearGradient(w - edge, 0f, w, 0f, opaque, clear, Shader.TileMode.CLAMP)
            canvas.drawRect(w - edge, 0f, w, h, maskPaint)
        }
        if (featherTop) {
            maskPaint.shader = LinearGradient(0f, 0f, 0f, edge, clear, opaque, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, edge, maskPaint)
        }
        if (featherBottom) {
            maskPaint.shader = LinearGradient(0f, h - edge, 0f, h, opaque, clear, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h - edge, w, h, maskPaint)
        }

        maskPaint.shader = null
        canvas.restoreToCount(layer)
    }
}
