package com.instafact.app.ui.walkthrough

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityWalkthroughBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class WalkthroughActivity : AppCompatActivity() {

    companion object {
        /**
         * TEMPORARY (testing only): show the walkthrough on every launch instead of only when
         * signed out. Flip to false to restore the normal first-open behaviour - that single
         * change reverts both this screen and the splash routing.
         */
        const val ALWAYS_SHOW = true

        /** How long each slide holds before advancing, story-style. */
        private const val SLIDE_DURATION_MS = 6000L

        /** One expand-and-fade cycle of the Get Started pulse. */
        private const val PULSE_DURATION_MS = 1500L
    }

    private lateinit var binding: ActivityWalkthroughBinding

    private val segmentFills = mutableListOf<View>()
    private var progressAnimator: ValueAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null

    private val pages by lazy {
        listOf(
            WalkthroughPage.Welcome(
                brand = getString(R.string.walk_welcome_brand),
                backgroundRes = R.drawable.bg_walk_page_0,
            ),
            WalkthroughPage.Story(
                title = getString(R.string.walk_title_1),
                highlight = getString(R.string.walk_highlight_1),
                description = getString(R.string.walk_body_1),
                illustrationLayoutRes = R.layout.view_walk_illustration_1,
                backgroundRes = R.drawable.bg_walk_page_1,
            ),
            WalkthroughPage.Story(
                title = getString(R.string.walk_title_2),
                highlight = getString(R.string.walk_highlight_2),
                description = getString(R.string.walk_body_2),
                illustrationLayoutRes = R.layout.view_walk_illustration_2,
                backgroundRes = R.drawable.bg_walk_page_2,
            ),
            WalkthroughPage.Story(
                title = getString(R.string.walk_title_3),
                highlight = getString(R.string.walk_highlight_3),
                description = getString(R.string.walk_body_3),
                illustrationLayoutRes = R.layout.view_walk_illustration_3,
                backgroundRes = R.drawable.bg_walk_page_3,
            ),
            WalkthroughPage.Story(
                title = getString(R.string.walk_title_4),
                highlight = getString(R.string.walk_highlight_4),
                description = getString(R.string.walk_body_4),
                illustrationLayoutRes = R.layout.view_walk_illustration_4,
                backgroundRes = R.drawable.bg_walk_page_4,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        if (!ALWAYS_SHOW && preferenceManager.isLoggedIn()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        binding = ActivityWalkthroughBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_surface,
            lightStatusBar = true,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

        binding.walkthroughViewPager.adapter = WalkthroughPagerAdapter(pages)
        binding.walkthroughViewPager.offscreenPageLimit = 1
        binding.walkthroughViewPager.registerOnPageChangeCallback(pageCallback)

        buildProgressSegments(pages.size)

        binding.nextButton.setOnClickListener {
            val nextIndex = binding.walkthroughViewPager.currentItem + 1
            if (nextIndex >= pages.size) {
                finishWalkthrough()
            } else {
                binding.walkthroughViewPager.currentItem = nextIndex
            }
        }
        binding.previousButton.setOnClickListener {
            val previousIndex = binding.walkthroughViewPager.currentItem - 1
            if (previousIndex >= 0) {
                binding.walkthroughViewPager.currentItem = previousIndex
            }
        }
        binding.skipButton.setOnClickListener { finishWalkthrough() }

        binding.nextButton.doOnLayout { button ->
            binding.nextPulseView.updateLayoutParams {
                width = button.width
                height = button.height
            }
        }

        renderChrome(0)
    }

    override fun onResume() {
        super.onResume()
        binding.walkthroughViewPager.post {
            val position = binding.walkthroughViewPager.currentItem
            playPageAnimation(position)
            startAutoAdvance(position)
            if (position == pages.lastIndex) startNextPulse()
        }
    }

    override fun onPause() {
        stopAutoAdvance()
        stopNextPulse()
        super.onPause()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.walkthroughViewPager.unregisterOnPageChangeCallback(pageCallback)
        }
        stopAutoAdvance()
        stopNextPulse()
        super.onDestroy()
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            renderChrome(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            when (state) {
                // Hold the timer while the user is dragging, like a paused story.
                ViewPager2.SCROLL_STATE_DRAGGING -> stopAutoAdvance()
                ViewPager2.SCROLL_STATE_IDLE -> {
                    val position = binding.walkthroughViewPager.currentItem
                    playPageAnimation(position)
                    startAutoAdvance(position)
                }
            }
        }
    }

    /** One track per page, each holding a fill that scales up as the slide plays. */
    private fun buildProgressSegments(count: Int) {
        binding.progressContainer.removeAllViews()
        segmentFills.clear()
        val height = resources.getDimensionPixelSize(R.dimen.walk_progress_height)
        val gap = resources.getDimensionPixelSize(R.dimen.walk_progress_gap)

        repeat(count) { index ->
            val track = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply {
                    if (index > 0) marginStart = gap
                }
                setBackgroundResource(R.drawable.bg_progress_segment_inactive)
            }
            val fill = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundResource(R.drawable.bg_progress_segment_active)
                pivotX = 0f
                scaleX = 0f
            }
            track.addView(fill)
            binding.progressContainer.addView(track)
            segmentFills += fill
        }
    }

    private fun startAutoAdvance(position: Int) {
        stopAutoAdvance()

        // Past slides read as complete, upcoming ones as empty.
        segmentFills.forEachIndexed { index, fill ->
            fill.scaleX = if (index < position) 1f else 0f
        }

        val fill = segmentFills.getOrNull(position) ?: return
        progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SLIDE_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { fill.scaleX = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    val next = binding.walkthroughViewPager.currentItem + 1
                    // The final slide holds, so the CTA does not vanish on its own.
                    if (next < pages.size) {
                        binding.walkthroughViewPager.currentItem = next
                    }
                }
            })
            start()
        }
    }

    private fun stopAutoAdvance() {
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private fun renderChrome(position: Int) {
        val isLast = position == pages.lastIndex
        binding.rootLayout.setBackgroundResource(pages[position].backgroundRes)
        binding.previousButton.isInvisible = position == 0
        binding.nextLabelTextView.text = getString(
            if (isLast) R.string.walk_get_started else R.string.walk_next,
        )
        // Nothing advances on its own from the last slide, so draw the eye to the CTA.
        if (isLast) startNextPulse() else stopNextPulse()
    }

    private fun startNextPulse() {
        if (pulseAnimator?.isRunning == true) return
        val pulse = binding.nextPulseView
        if (pulse.width == 0) {
            pulse.doOnLayout { startNextPulse() }
            return
        }
        pulse.pivotX = pulse.width / 2f
        pulse.pivotY = pulse.height / 2f
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            pulse,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.32f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.75f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f),
        ).apply {
            duration = PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun stopNextPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.nextPulseView.alpha = 0f
        binding.nextPulseView.scaleX = 1f
        binding.nextPulseView.scaleY = 1f
    }

    private fun playPageAnimation(position: Int) {
        val recycler = binding.walkthroughViewPager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position) ?: return
        if (pages[position] is WalkthroughPage.Welcome) {
            WalkthroughAnimator.playWelcome(holder.itemView)
            return
        }
        val illustration = holder.itemView
            .findViewById<ViewGroup>(R.id.illustrationContainer)
            ?.getChildAt(0)
            ?: return
        WalkthroughAnimator.play(illustration, position)
    }

    /** Signed-in users continue to Home; everyone else goes on to sign in. */
    private fun finishWalkthrough() {
        val loggedIn = (application as InstafactApplication)
            .appContainer.preferenceManager.isLoggedIn()
        val destination = if (loggedIn) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(destination)
        finish()
    }
}
