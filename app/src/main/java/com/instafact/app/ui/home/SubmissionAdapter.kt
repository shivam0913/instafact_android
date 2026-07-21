package com.instafact.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.ItemSubmissionBinding
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayStatus
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.platformIconRes
import com.instafact.app.utils.platformSourceLabel
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictSoftColorRes

class SubmissionAdapter(
    private val onItemClicked: (HistoryItemResponse) -> Unit,
) : ListAdapter<HistoryItemResponse, SubmissionAdapter.SubmissionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionViewHolder {
        val binding = ItemSubmissionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return SubmissionViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SubmissionViewHolder(
        private val binding: ItemSubmissionBinding,
        private val onItemClicked: (HistoryItemResponse) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemResponse) {
            val context = binding.root.context
            binding.urlTextView.text = item.title?.takeIf { it.isNotBlank() } ?: item.videoUrl.toReadableHeadline()
            binding.sourceTextView.text =
                item.channelName?.takeIf { it.isNotBlank() } ?: item.videoUrl.platformSourceLabel(context)
            binding.platformImageView.setImageResource(item.videoUrl.platformIconRes())
            binding.sourceIconImageView.setImageResource(item.videoUrl.platformIconRes())
            binding.thumbnailImageView.loadThumbnail(item.thumbnailUrl)
            binding.verdictTextView.text = item.verdict.displayVerdict(context)
            binding.statusTextView.text = if (item.status.equals("completed", true)) {
                item.confidence.displayConfidence(context)
            } else {
                item.status.displayStatus(context)
            }
            binding.countTextView.text = if (item.factCheckCount > 0) {
                context.resources.getQuantityString(
                    R.plurals.fact_check_count,
                    item.factCheckCount,
                    item.factCheckCount,
                )
            } else {
                context.getString(R.string.recently)
            }

            binding.verdictTextView.setTextColor(ContextCompat.getColor(context, item.verdict.verdictColorRes()))
            binding.verdictTextView.backgroundTintList =
                ContextCompat.getColorStateList(context, item.verdict.verdictSoftColorRes())
            binding.statusTextView.setTextColor(ContextCompat.getColor(context, item.verdict.verdictColorRes()))
            binding.root.setOnClickListener { onItemClicked(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HistoryItemResponse>() {
        override fun areItemsTheSame(
            oldItem: HistoryItemResponse,
            newItem: HistoryItemResponse,
        ): Boolean = oldItem.queryId == newItem.queryId

        override fun areContentsTheSame(
            oldItem: HistoryItemResponse,
            newItem: HistoryItemResponse,
        ): Boolean = oldItem == newItem
    }
}
