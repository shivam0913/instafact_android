package com.instafact.app.ui.detail

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.ChatMessageItem
import com.instafact.app.data.model.LocalChatIds
import com.instafact.app.databinding.ItemChatMessageBinding
import com.instafact.app.databinding.ItemChatTypingBinding
import com.instafact.app.utils.MarkdownRenderer

class ChatAdapter : ListAdapter<ChatMessageItem, RecyclerView.ViewHolder>(DiffCallback) {

    private var onLinkClicked: ((String) -> Unit)? = null

    fun setOnLinkClicked(listener: (String) -> Unit) {
        onLinkClicked = listener
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).id == LocalChatIds.PENDING_REPLY) TYPE_TYPING else TYPE_MESSAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TYPING) {
            TypingViewHolder(ItemChatTypingBinding.inflate(inflater, parent, false))
        } else {
            ChatViewHolder(ItemChatMessageBinding.inflate(inflater, parent, false), onLinkClicked)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatViewHolder -> holder.bind(getItem(position))
            is TypingViewHolder -> holder.start()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // The dots animate forever; without this every recycled bubble leaks an animator.
        if (holder is TypingViewHolder) holder.stop()
        super.onViewRecycled(holder)
    }

    /** Three dots rising in sequence, so the wait reads as work rather than a freeze. */
    class TypingViewHolder(
        private val binding: ItemChatTypingBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val animators = mutableListOf<ObjectAnimator>()

        fun start() {
            stop()
            listOf(binding.typingDotOne, binding.typingDotTwo, binding.typingDotThree)
                .forEachIndexed { index, dot ->
                    animators += ObjectAnimator.ofFloat(dot, View.ALPHA, 0.3f, 1f, 0.3f).apply {
                        duration = DOT_CYCLE_MS
                        startDelay = index * DOT_STAGGER_MS
                        repeatCount = ObjectAnimator.INFINITE
                        start()
                    }
                }
        }

        fun stop() {
            animators.forEach { it.cancel() }
            animators.clear()
        }
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding,
        private val onLinkClicked: ((String) -> Unit)?,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessageItem) {
            val isUser = item.role.equals("user", ignoreCase = true)
            val context = binding.root.context
            val listener = onLinkClicked
            MarkdownRenderer.render(binding.messageTextView, item.content, listener)
            binding.roleTextView.text = if (isUser) "You" else "Instafact AI"
            val params = binding.messageCardView.layoutParams as ViewGroup.MarginLayoutParams
            if (isUser) {
                params.marginStart = 56
                params.marginEnd = 0
                binding.messageCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.brand_primary_soft))
            } else {
                params.marginStart = 0
                params.marginEnd = 56
                binding.messageCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.brand_surface))
            }
            binding.messageCardView.layoutParams = params
        }
    }

    private companion object {
        const val TYPE_MESSAGE = 0
        const val TYPE_TYPING = 1
        const val DOT_CYCLE_MS = 900L
        const val DOT_STAGGER_MS = 150L
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatMessageItem>() {
        override fun areItemsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean = oldItem == newItem
    }
}
