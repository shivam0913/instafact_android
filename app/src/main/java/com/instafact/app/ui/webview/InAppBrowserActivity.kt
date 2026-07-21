package com.instafact.app.ui.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.instafact.app.R
import com.instafact.app.databinding.ActivityInAppBrowserBinding
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class InAppBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInAppBrowserBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInAppBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_surface,
            lightStatusBar = true,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

        val url = intent.getStringExtra(IntentExtras.EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(IntentExtras.EXTRA_TITLE).orEmpty()

        binding.titleTextView.text = title.ifBlank { getString(R.string.in_app_browser_title) }
        binding.backButton.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }
        binding.closeButton.setOnClickListener { finish() }

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.settings.loadsImagesAutomatically = true
        binding.webView.settings.mediaPlaybackRequiresUserGesture = false
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                binding.progressBar.progress = newProgress
            }
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString().orEmpty()
                if (nextUrl.isBlank()) return false
                view?.loadUrl(nextUrl)
                return true
            }
        }

        if (url.isBlank()) {
            finish()
        } else {
            binding.webView.loadUrl(url)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::binding.isInitialized && binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
