package com.instafact.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        binding.root.postDelayed(
            {
                val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
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

    companion object {
        private const val SPLASH_DELAY_MS = 1000L
    }
}
