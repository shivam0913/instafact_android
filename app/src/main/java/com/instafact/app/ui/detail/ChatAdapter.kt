package com.instafact.app.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.ChatMessageItem
import com.instafact.app.databinding.ItemChatMessageBinding

class ChatAdapter : ListAdapter<ChatMessageItem, ChatAdapter.ChatViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessageItem) {
            val isUser = item.role.equals("user", ignoreCase = true)
            val context = binding.root.context
            binding.messageTextView.text = item.content
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

    private object DiffCallback : DiffUtil.ItemCallback<ChatMessageItem>() {
        override fun areItemsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean = oldItem == newItem
    }
}
