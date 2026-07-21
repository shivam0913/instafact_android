package com.instafact.app.utils

import android.graphics.Color
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.core.text.util.LinkifyCompat

fun TextView.setInAppLinkText(
    value: String,
    onUrlClicked: (String) -> Unit,
) {
    val spannable = SpannableString(value)
    LinkifyCompat.addLinks(spannable, android.util.Patterns.WEB_URL, null)

    val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
    urlSpans.forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onUrlClicked(span.url)
                }
            },
            start,
            end,
            flags,
        )
    }

    text = spannable
    movementMethod = LinkMovementMethod.getInstance()
    highlightColor = Color.TRANSPARENT
}
