package com.instafact.app.ui.support

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.databinding.ItemFaqBinding

data class FaqItem(
    val question: String,
    val answer: String,
)

class FaqAdapter(
    private val items: List<FaqItem>,
) : RecyclerView.Adapter<FaqAdapter.FaqViewHolder>() {

    private var expandedPosition: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        val binding = ItemFaqBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FaqViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        holder.bind(items[position], isExpanded = position == expandedPosition)
        holder.itemView.setOnClickListener {
            val oldPosition = expandedPosition
            expandedPosition = if (position == expandedPosition) RecyclerView.NO_POSITION else position
            if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition)
            if (expandedPosition != RecyclerView.NO_POSITION) notifyItemChanged(expandedPosition)
        }
    }

    class FaqViewHolder(
        private val binding: ItemFaqBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FaqItem, isExpanded: Boolean) {
            binding.questionTextView.text = item.question
            binding.answerTextView.text = item.answer
            binding.answerTextView.isVisible = isExpanded
            binding.expandIconImageView.rotation = if (isExpanded) 180f else 0f
        }
    }
}
