package com.instafact.app.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
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
        // Below API 30 these default to true. With JavaScript on, a file:// or content://
        // URL would let a page read app-private storage, and the URLs reaching this screen
        // come from fact-check references - i.e. from model web-search output, not from us.
        binding.webView.settings.allowFileAccess = false
        binding.webView.settings.allowContentAccess = false
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                binding.progressBar.progress = newProgress
            }
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUri = request?.url ?: return false
                // Anything that is not plain web content - a tel:, mailto:, intent: or
                // javascript: link - is handed to the system rather than loaded here.
                if (!nextUri.isWebUrl()) {
                    openExternally(nextUri)
                    return true
                }
                view?.loadUrl(nextUri.toString())
                return true
            }

            // Fires whenever the back/forward list changes, which is the only signal
            // WebView gives that canGoBack() has flipped.
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                syncBackCallback()
            }
        }
        onBackPressedDispatcher.addCallback(this, webHistoryBackCallback)

        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (url.isBlank() || uri == null || !uri.isWebUrl()) {
            // Nothing sensible to show, and loading it anyway is how a javascript: or
            // file:// URL would end up executing inside our own WebView.
            Toast.makeText(this, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            binding.webView.loadUrl(url)
        }
    }

    /** Only http(s) is loaded in-app; every other scheme is somebody else's job. */
    private fun Uri.isWebUrl(): Boolean =
        scheme?.lowercase() == "http" || scheme?.lowercase() == "https"

    private fun openExternally(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure {
                Toast.makeText(this, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        // A WebView left attached keeps the Activity alive; detach it before destroying.
        if (::binding.isInitialized) binding.webView.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
        super.onDestroy()
    }

    /**
     * Back walks the page history before it leaves the browser.
     *
     * Registered on the dispatcher rather than overriding onBackPressed: from targetSdk 36
     * predictive back is on by default, and the framework then routes back through
     * OnBackInvokedDispatcher and never calls the legacy method. Overriding it would have
     * left every back gesture closing the browser outright.
     *
     * Enabled only while there is history to walk. When there is none the system handles
     * back itself, which is what lets the predictive close animation play.
     */
    private val webHistoryBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.webView.canGoBack()) binding.webView.goBack() else isEnabled = false
        }
    }

    private fun syncBackCallback() {
        webHistoryBackCallback.isEnabled = binding.webView.canGoBack()
    }

}
