package com.instafact.app.ui.walkthrough

import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.instafact.app.R
import com.instafact.app.databinding.ItemWalkthroughPageBinding
import com.instafact.app.databinding.ItemWalkthroughWelcomeBinding
import com.instafact.app.utils.GradientTextSpan

sealed interface WalkthroughPage {
    val backgroundRes: Int

    /** Opening slide: centred brand lockup over a photo. */
    data class Welcome(
        val brand: String,
        override val backgroundRes: Int,
    ) : WalkthroughPage

    /** Standard slide: headline, body copy and an illustration. */
    data class Story(
        val title: String,
        /** Portion of [title] painted with the brand gradient, as in the mockup. */
        val highlight: String,
        val description: String,
        val illustrationLayoutRes: Int,
        override val backgroundRes: Int,
    ) : WalkthroughPage
}

class WalkthroughPagerAdapter(
    private val pages: List<WalkthroughPage>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_WELCOME = 0
        private const val TYPE_STORY = 1
    }

    override fun getItemViewType(position: Int): Int = when (pages[position]) {
        is WalkthroughPage.Welcome -> TYPE_WELCOME
        is WalkthroughPage.Story -> TYPE_STORY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_WELCOME) {
            WelcomeViewHolder(ItemWalkthroughWelcomeBinding.inflate(inflater, parent, false))
        } else {
            StoryViewHolder(ItemWalkthroughPageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = pages[position]) {
            is WalkthroughPage.Welcome -> (holder as WelcomeViewHolder).bind(page)
            is WalkthroughPage.Story -> (holder as StoryViewHolder).bind(page, position)
        }
    }

    override fun getItemCount(): Int = pages.size

    class WelcomeViewHolder(
        private val binding: ItemWalkthroughWelcomeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WalkthroughPage.Welcome) {
            val context = binding.root.context
            binding.welcomeBrandTextView.text = SpannableString(page.brand).apply {
                setSpan(
                    GradientTextSpan(
                        runText = page.brand,
                        startColor = ContextCompat.getColor(context, R.color.brand_welcome_gradient_start),
                        endColor = ContextCompat.getColor(context, R.color.brand_welcome_gradient_end),
                    ),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            WalkthroughAnimator.prepareWelcome(binding.root)
        }
    }

    class StoryViewHolder(
        private val binding: ItemWalkthroughPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WalkthroughPage.Story, position: Int) {
            binding.titleTextView.text = buildTitle(page)
            binding.descriptionTextView.text = page.description

            val container = binding.illustrationContainer
            container.removeAllViews()
            val illustration = LayoutInflater.from(container.context)
                .inflate(page.illustrationLayoutRes, container, false)
            container.addView(illustration)

            // Start hidden; the activity plays the entrance once the page is settled.
            WalkthroughAnimator.prepare(illustration, position)
        }

        private fun buildTitle(page: WalkthroughPage.Story): CharSequence {
            val span = SpannableString(page.title)
            val start = page.title.indexOf(page.highlight)
            if (start >= 0) {
                val context = binding.root.context
                span.setSpan(
                    GradientTextSpan(
                        runText = page.highlight,
                        startColor = ContextCompat.getColor(context, R.color.brand_gradient_start),
                        endColor = ContextCompat.getColor(context, R.color.brand_gradient_end),
                    ),
                    start,
                    start + page.highlight.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return span
        }
    }
}
