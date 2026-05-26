package com.instafact.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityHomeBinding
import com.instafact.app.ui.explore.ExploreFragment
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.ui.profile.ProfileFragment
import com.instafact.app.ui.splash.SplashActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val viewModel: HomeViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var selectedTabId: Int = R.id.menu_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        val forwardedSharedUrl = getIncomingSharedUrl(intent)
        if (!preferenceManager.isLoggedIn()) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    forwardedSharedUrl?.let { putExtra(IntentExtras.EXTRA_SHARED_URL, it) }
                },
            )
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_background,
            lightStatusBar = true,
        )
        binding.contentRoot.applySystemBarInsets(applyTop = true)
        binding.bottomNavigationView.applySystemBarInsets(applyBottom = true)

        setupDrawer()
        setupBottomNavigation()
        observeViewModel()
        maybeRequestNotificationPermission()

        if (savedInstanceState == null) {
            switchTab(R.id.menu_home)
            handleIncomingSharedUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSharedUrl(intent)
        refreshCurrentTab()
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun navigateToProfileTab() {
        binding.bottomNavigationView.selectedItemId = R.id.menu_profile
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home, R.id.menu_explore, R.id.menu_profile -> {
                    binding.bottomNavigationView.selectedItemId = item.itemId
                }

                R.id.menu_connect_instagram -> openInstagram()
                R.id.menu_share_app -> shareApp()
                R.id.menu_logout -> logout()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }
    }

    private fun observeViewModel() {
        viewModel.submitState.observe(this) { state ->
            when (state) {
                UiState.Idle -> binding.shareStatusCard.visibility = View.GONE
                UiState.Loading -> {
                    binding.shareStatusCard.visibility = View.VISIBLE
                    binding.shareStatusTextView.text = getString(R.string.share_processing)
                }

                is UiState.Success -> {
                    binding.shareStatusCard.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.submission_success), Toast.LENGTH_SHORT).show()
                    binding.bottomNavigationView.selectedItemId = R.id.menu_home
                    viewModel.loadHistory()
                    viewModel.loadExplore()
                    viewModel.resetSubmitState()
                }

                is UiState.Error -> {
                    binding.shareStatusCard.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSubmitState()
                }
            }
        }
    }

    private fun handleIncomingSharedUrl(incomingIntent: Intent?) {
        val sharedUrl = getIncomingSharedUrl(incomingIntent) ?: return
        if (!UrlValidator.isSupportedVideoUrl(sharedUrl)) {
            Toast.makeText(this, getString(R.string.unsupported_url), Toast.LENGTH_LONG).show()
            return
        }
        viewModel.submitSharedUrl(sharedUrl)
    }

    private fun switchTab(itemId: Int) {
        selectedTabId = itemId
        binding.navigationView.setCheckedItem(itemId)

        val fragment = when (itemId) {
            R.id.menu_explore -> ExploreFragment()
            R.id.menu_profile -> ProfileFragment()
            else -> HomeFeedFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun refreshCurrentTab() {
        when (selectedTabId) {
            R.id.menu_explore -> viewModel.loadExplore()
            else -> viewModel.loadHistory()
        }
    }

    private fun openInstagram() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
    }

    private fun shareApp() {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(R.string.share_friends_message, getString(R.string.app_download_link)),
                    )
                },
                getString(R.string.share_with_friends),
            ),
        )
    }

    private fun logout() {
        (application as InstafactApplication).appContainer.preferenceManager.clearUserSession()
        Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    private fun getIncomingSharedUrl(incomingIntent: Intent?): String? {
        if (incomingIntent == null) return null

        return when {
            incomingIntent.action == Intent.ACTION_SEND && incomingIntent.type == "text/plain" ->
                incomingIntent.getStringExtra(Intent.EXTRA_TEXT)?.trim()

            incomingIntent.hasExtra(IntentExtras.EXTRA_SHARED_URL) ->
                incomingIntent.getStringExtra(IntentExtras.EXTRA_SHARED_URL)?.trim()

            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION,
        )
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1101
    }
}
