package com.instafact.app.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.databinding.ItemExploreHighlightBinding
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.verdictColorRes

class ExploreHighlightAdapter(
    private val onClick: (ExploreItemResponse) -> Unit,
) : ListAdapter<ExploreItemResponse, ExploreHighlightAdapter.HighlightViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HighlightViewHolder {
        val binding = ItemExploreHighlightBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return HighlightViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: HighlightViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HighlightViewHolder(
        private val binding: ItemExploreHighlightBinding,
        private val onClick: (ExploreItemResponse) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExploreItemResponse) {
            val context = binding.root.context
            binding.urlTextView.text = item.title?.takeIf { it.isNotBlank() } ?: item.videoUrl.toReadableHeadline()
            binding.thumbnailImageView.loadThumbnail(item.thumbnailUrl)
            binding.verdictTextView.text = context.getString(
                R.string.label_verdict_format,
                "${item.verdict.displayVerdict(context)} • ${item.confidence.displayConfidence(context)}",
            )
            binding.verdictTextView.setTextColor(ContextCompat.getColor(context, item.verdict.verdictColorRes()))
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ExploreItemResponse>() {
        override fun areItemsTheSame(oldItem: ExploreItemResponse, newItem: ExploreItemResponse): Boolean =
            oldItem.queryId == newItem.queryId

        override fun areContentsTheSame(oldItem: ExploreItemResponse, newItem: ExploreItemResponse): Boolean =
            oldItem == newItem
    }
}
