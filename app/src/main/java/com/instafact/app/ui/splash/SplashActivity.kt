package com.instafact.app.ui.splash

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivitySplashBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.ui.walkthrough.WalkthroughActivity
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_surface,
            lightStatusBar = true,
        )
        binding.root.applySystemBarInsets(applyTop = true, applyBottom = true)

        renderBrand()
        playIntroAnimation()

        binding.root.postDelayed(
            {
                val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
                // Signed in goes straight to Home; everyone else sees onboarding first.
                val destination = if (preferenceManager.isLoggedIn()) {
                    Intent(this, HomeActivity::class.java)
                } else {
                    Intent(this, WalkthroughActivity::class.java)
                }
                startActivity(destination)
                finish()
            },
            SPLASH_DELAY_MS,
        )
    }

    private fun renderBrand() {
        val brand = getString(R.string.app_name)
        val span = SpannableString(brand)
        val splitIndex = brand.indexOf("Fact")
        if (splitIndex in 1 until brand.length) {
            span.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_primary)),
                splitIndex,
                brand.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        binding.splashBrandTextView.text = span
    }

    private fun playIntroAnimation() {
        binding.splashLogoImageView.apply {
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            animate()
                .alpha(1f)
                .scaleXBy(0.28f)
                .scaleYBy(0.28f)
                .setDuration(560L)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }

        listOf(binding.splashBrandTextView, binding.splashTaglineTextView)
            .forEachIndexed { index, view ->
                view.alpha = 0f
                view.translationY = 18f
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(220L + index * 110L)
                    .setDuration(420L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

        binding.splashLoadingContainer.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setStartDelay(500L)
                .setDuration(360L)
                .start()
        }
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1600L
    }
}
