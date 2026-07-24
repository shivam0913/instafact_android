package com.instafact.app.utils

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.linkify.LinkifyPlugin

object MarkdownRenderer {

    fun render(
        textView: TextView,
        markdown: String?,
        onLinkClicked: ((String) -> Unit)? = null,
    ) {
        val content = markdown?.trim().orEmpty()
        if (content.isBlank()) {
            textView.text = ""
            return
        }

        create(textView.context, onLinkClicked).setMarkdown(textView, content)
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    private fun create(
        context: Context,
        onLinkClicked: ((String) -> Unit)?,
    ): Markwon {
        return Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver { _, link ->
                            if (onLinkClicked != null) {
                                onLinkClicked(link)
                            }
                        }
                    }
                },
            )
            .build()
    }
}
