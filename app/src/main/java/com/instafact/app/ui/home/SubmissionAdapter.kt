package com.instafact.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.ItemSubmissionBinding
import com.instafact.app.utils.displayStatus
import com.instafact.app.utils.displayVerdict

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
            binding.urlTextView.text = item.videoUrl
            binding.statusTextView.text = item.status.displayStatus(binding.root.context)
            binding.verdictTextView.text = binding.root.context.getString(
                R.string.label_verdict_format,
                item.verdict.displayVerdict(binding.root.context),
            )
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
