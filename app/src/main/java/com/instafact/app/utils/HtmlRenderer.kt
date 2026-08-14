package com.instafact.app.utils

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat

object HtmlRenderer {

    fun render(
        textView: TextView,
        html: String?,
        onLinkClicked: ((String) -> Unit)? = null,
    ) {
        val content = html?.trim().orEmpty()
        if (content.isBlank()) {
            textView.text = ""
            return
        }

        val spanned = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val builder = SpannableStringBuilder(spanned)
        val spans = builder.getSpans(0, builder.length, URLSpan::class.java)
        spans.forEach { span ->
            val start = builder.getSpanStart(span)
            val end = builder.getSpanEnd(span)
            val flags = builder.getSpanFlags(span)
            builder.removeSpan(span)
            builder.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onLinkClicked?.invoke(span.url)
                    }
                },
                start,
                end,
                flags,
            )
        }

        textView.text = builder
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    fun toPlainText(html: String?): String {
        val content = html?.trim().orEmpty()
        if (content.isBlank()) return ""
        return HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
