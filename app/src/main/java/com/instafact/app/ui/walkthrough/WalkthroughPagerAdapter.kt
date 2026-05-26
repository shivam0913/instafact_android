package com.instafact.app.ui.walkthrough

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.databinding.ItemWalkthroughPageBinding

data class WalkthroughPage(
    val badge: String,
    val title: String,
    val description: String,
    val primaryIconRes: Int,
    val secondaryIconRes: Int,
    val tertiaryIconRes: Int,
)

class WalkthroughPagerAdapter(
    private val pages: List<WalkthroughPage>,
) : RecyclerView.Adapter<WalkthroughPagerAdapter.WalkthroughViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalkthroughViewHolder {
        val binding = ItemWalkthroughPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return WalkthroughViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WalkthroughViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class WalkthroughViewHolder(
        private val binding: ItemWalkthroughPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WalkthroughPage) {
            binding.badgeTextView.text = page.badge
            binding.titleTextView.text = page.title
            binding.descriptionTextView.text = page.description
            binding.centerIconImageView.setImageResource(page.primaryIconRes)
            binding.primaryFloatingIconImageView.setImageResource(page.primaryIconRes)
            binding.secondaryFloatingIconImageView.setImageResource(page.secondaryIconRes)
            binding.tertiaryFloatingIconImageView.setImageResource(page.tertiaryIconRes)
        }
    }
}
