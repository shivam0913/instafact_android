package com.instafact.app.utils

import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

/**
 * Paints one run of text with a horizontal gradient, used for the highlighted words in the
 * walkthrough headlines. The shader is sized to the run itself so short and long highlights
 * both get the full colour sweep.
 */
class GradientTextSpan(
    private val runText: String,
    private val startColor: Int,
    private val endColor: Int,
) : CharacterStyle(), UpdateAppearance {

    override fun updateDrawState(tp: TextPaint) {
        val width = tp.measureText(runText).coerceAtLeast(1f)
        tp.shader = LinearGradient(
            0f,
            0f,
            width,
            0f,
            startColor,
            endColor,
            Shader.TileMode.CLAMP,
        )
    }
}
