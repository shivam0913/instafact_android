package com.instafact.app.ui.walkthrough

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityWalkthroughBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class WalkthroughActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkthroughBinding

    private val pages by lazy {
        listOf(
            WalkthroughPage(
                badge = getString(R.string.walkthrough_badge_1),
                title = getString(R.string.walkthrough_title_1),
                description = getString(R.string.walkthrough_desc_1),
                primaryIconRes = R.drawable.ic_check_badge,
                secondaryIconRes = R.drawable.ic_instagram,
                tertiaryIconRes = R.drawable.ic_youtube,
            ),
            WalkthroughPage(
                badge = getString(R.string.walkthrough_badge_2),
                title = getString(R.string.walkthrough_title_2),
                description = getString(R.string.walkthrough_desc_2),
                primaryIconRes = R.drawable.ic_share,
                secondaryIconRes = R.drawable.ic_sparkle,
                tertiaryIconRes = R.drawable.ic_instagram,
            ),
            WalkthroughPage(
                badge = getString(R.string.walkthrough_badge_3),
                title = getString(R.string.walkthrough_title_3),
                description = getString(R.string.walkthrough_desc_3),
                primaryIconRes = R.drawable.ic_check_badge,
                secondaryIconRes = R.drawable.ic_share,
                tertiaryIconRes = R.drawable.ic_play_filled,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        if (preferenceManager.isLoggedIn()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        binding = ActivityWalkthroughBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_dark_surface,
            navigationBarColorRes = R.color.brand_dark_surface,
            lightStatusBar = false,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

        binding.walkthroughViewPager.adapter = WalkthroughPagerAdapter(pages)
        binding.walkthroughViewPager.registerOnPageChangeCallback(pageCallback)

        binding.nextButton.setOnClickListener {
            val nextIndex = binding.walkthroughViewPager.currentItem + 1
            if (nextIndex >= pages.size) {
                navigateToLogin()
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
        binding.skipButton.setOnClickListener { navigateToLogin() }

        renderPage(0)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.walkthroughViewPager.unregisterOnPageChangeCallback(pageCallback)
        }
        super.onDestroy()
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            renderPage(position)
        }
    }

    private fun renderPage(position: Int) {
        binding.previousButton.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.nextButton.text = getString(
            if (position == pages.lastIndex) R.string.get_started else R.string.next,
        )
        val selected = intArrayOf(
            R.drawable.bg_dot_unselected,
            R.drawable.bg_dot_unselected,
            R.drawable.bg_dot_unselected,
        )
        selected[position] = R.drawable.bg_dot_selected
        binding.dotOne.setBackgroundResource(selected[0])
        binding.dotTwo.setBackgroundResource(selected[1])
        binding.dotThree.setBackgroundResource(selected[2])
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
