package com.instafact.app.ui.home

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.ItemSubmissionBinding
import com.instafact.app.utils.ellipsized
import com.instafact.app.utils.formatFactCheckCount
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.platformIconRes
import com.instafact.app.utils.sourceUrlLabel
import com.instafact.app.utils.toCompactRelativeTimeLabel
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictSoftColorRes

class SubmissionAdapter(
    private val userVoteLookup: (Int) -> String?,
    private val onFeedbackClicked: (HistoryItemResponse, FeedbackType) -> Unit,
    private val onMoreClicked: (View, HistoryItemResponse) -> Unit,
    private val onItemClicked: (HistoryItemResponse) -> Unit,
) : ListAdapter<HistoryItemResponse, SubmissionAdapter.SubmissionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionViewHolder {
        val binding = ItemSubmissionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return SubmissionViewHolder(binding, userVoteLookup, onFeedbackClicked, onMoreClicked, onItemClicked)
    }

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SubmissionViewHolder(
        private val binding: ItemSubmissionBinding,
        private val userVoteLookup: (Int) -> String?,
        private val onFeedbackClicked: (HistoryItemResponse, FeedbackType) -> Unit,
        private val onMoreClicked: (View, HistoryItemResponse) -> Unit,
        private val onItemClicked: (HistoryItemResponse) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemResponse) {
            val context = binding.root.context
            binding.urlTextView.text = item.title?.takeIf { it.isNotBlank() } ?: item.videoUrl.toReadableHeadline()
            val sourceLabel = item.channelName?.takeIf { it.isNotBlank() }
                ?: item.videoUrl.sourceUrlLabel()
            binding.sourceTextView.text = sourceLabel.ellipsized(30)
            binding.sourceIconImageView.setImageResource(item.videoUrl.platformIconRes())
            binding.thumbnailImageView.loadThumbnail(item.thumbnailUrl)
            binding.verdictTextView.text = item.verdict.displayVerdict(context)
            binding.countTextView.text = item.factCheckCount.formatFactCheckCount(context)
            binding.statusTextView.text = item.createdAt.toCompactRelativeTimeLabel(context)
                ?: item.confidence.displayConfidence(context)
            binding.helpfulCountTextView.text = item.upvotes.toString()
            binding.notHelpfulCountTextView.text = item.downvotes.toString()

            val userVote = item.currentUserVote?.lowercase() ?: userVoteLookup(item.queryId)?.lowercase()
            binding.helpfulIconImageView.setImageResource(
                if (userVote == "up") R.drawable.ic_helpful_filled else R.drawable.ic_helpful_outline,
            )
            binding.notHelpfulIconImageView.setImageResource(
                if (userVote == "down") R.drawable.ic_not_helpful_filled else R.drawable.ic_not_helpful_outline,
            )
            binding.helpfulIconImageView.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (userVote == "up") R.color.brand_text else R.color.brand_muted,
                ),
            )
            binding.notHelpfulIconImageView.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (userVote == "down") R.color.brand_text else R.color.brand_muted,
                ),
            )

            val verdictColor = ContextCompat.getColor(context, item.verdict.verdictColorRes())
            binding.verdictTextView.setTextColor(verdictColor)
            binding.verdictChipContainer.backgroundTintList =
                ContextCompat.getColorStateList(context, item.verdict.verdictSoftColorRes())
            binding.verdictIconImageView.setColorFilter(verdictColor)
            binding.verdictIconImageView.setImageResource(
                when (item.verdict?.lowercase()) {
                    "true" -> R.drawable.ic_verdict_true_outline
                    "false" -> R.drawable.ic_verdict_false_outline
                    "misleading" -> R.drawable.ic_verdict_misleading_outline
                    else -> R.drawable.ic_verdict_misleading_outline
                },
            )
            binding.helpfulActionLayout.setOnClickListener { onFeedbackClicked(item, FeedbackType.UP) }
            binding.notHelpfulActionLayout.setOnClickListener { onFeedbackClicked(item, FeedbackType.DOWN) }
            binding.moreButton.setOnClickListener { onMoreClicked(it, item) }
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
