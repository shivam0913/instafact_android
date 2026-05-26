package com.instafact.app.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.databinding.ItemExploreBinding
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.platformSourceLabel
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictSoftColorRes

class ExploreAdapter(
    private val onClick: (ExploreItemResponse) -> Unit,
) : ListAdapter<ExploreItemResponse, ExploreAdapter.ExploreViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExploreViewHolder {
        val binding = ItemExploreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExploreViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ExploreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExploreViewHolder(
        private val binding: ItemExploreBinding,
        private val onClick: (ExploreItemResponse) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExploreItemResponse) {
            val context = binding.root.context
            binding.platformTextView.text =
                item.channelName?.takeIf { it.isNotBlank() } ?: item.videoUrl.platformSourceLabel(context)
            binding.urlTextView.text = item.title?.takeIf { it.isNotBlank() } ?: item.videoUrl.toReadableHeadline()
            binding.thumbnailImageView.loadThumbnail(item.thumbnailUrl)
            binding.verdictTextView.text = item.verdict.displayVerdict(context)
            binding.confidenceTextView.text = item.confidence.displayConfidence(context)
            binding.countTextView.text = context.resources.getQuantityString(
                com.instafact.app.R.plurals.fact_check_count,
                item.factCheckCount,
                item.factCheckCount,
            )
            binding.verdictTextView.setTextColor(ContextCompat.getColor(context, item.verdict.verdictColorRes()))
            binding.verdictTextView.backgroundTintList =
                ContextCompat.getColorStateList(context, item.verdict.verdictSoftColorRes())
            binding.confidenceTextView.setTextColor(ContextCompat.getColor(context, item.verdict.verdictColorRes()))
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ExploreItemResponse>() {
        override fun areItemsTheSame(oldItem: ExploreItemResponse, newItem: ExploreItemResponse): Boolean {
            return oldItem.queryId == newItem.queryId
        }

        override fun areContentsTheSame(oldItem: ExploreItemResponse, newItem: ExploreItemResponse): Boolean {
            return oldItem == newItem
        }
    }
}
