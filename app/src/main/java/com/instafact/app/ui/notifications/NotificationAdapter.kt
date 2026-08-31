package com.instafact.app.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.databinding.ItemNotificationBinding
import com.instafact.app.utils.NotificationRecord
import com.instafact.app.utils.toShortRelativeTime

class NotificationAdapter(
    private val onItemClicked: (NotificationRecord) -> Unit,
) : ListAdapter<NotificationRecord, NotificationAdapter.NotificationViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: NotificationRecord) {
            val context = binding.root.context
            binding.notificationTitleTextView.text = record.title
            binding.notificationBodyTextView.text = record.body
            binding.notificationTimeTextView.text = record.receivedAt.toShortRelativeTime()
            binding.notificationUnreadDot.isVisible = !record.isRead
            binding.notificationActionTextView.isVisible = record.queryId != null

            // Read items step back visually, matching the mockup's "Earlier" treatment.
            binding.notificationTitleTextView.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (record.isRead) R.color.brand_muted else R.color.brand_text,
                ),
            )
            binding.notificationCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (record.isRead) R.color.brand_background else R.color.brand_surface,
                ),
            )

            binding.root.setOnClickListener { onItemClicked(record) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NotificationRecord>() {
            override fun areItemsTheSame(
                oldItem: NotificationRecord,
                newItem: NotificationRecord,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: NotificationRecord,
                newItem: NotificationRecord,
            ): Boolean = oldItem == newItem
        }
    }
}
