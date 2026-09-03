package com.instafact.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.app.NotificationManagerCompat
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityHomeBinding
import com.instafact.app.ui.explore.ExploreFragment
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.ui.profile.ProfileFragment
import com.instafact.app.ui.splash.SplashActivity
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.analyticsPlatform
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.utils.UnsupportedPlatformDialog
import com.instafact.app.viewmodel.HomeViewModel
import com.instafact.app.ui.coachmark.CoachMarkSequence
import com.instafact.app.ui.coachmark.CoachStep

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val viewModel: HomeViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var selectedTabId: Int = R.id.menu_home
    private var lastBackPressedAt: Long = 0L

    /** Long enough for the Home fragment to inflate and lay out its paste card. */
    private val tourStartDelayMs = 700L

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
        binding.customBottomNavCard.applySystemBarInsets(applyBottom = true)

        setupDrawer()
        setupBottomNavigation()
        setupBackPressHandling()
        observeViewModel()
        maybeRequestNotificationPermission()

        if (savedInstanceState == null) {
            switchTab(resolveInitialTab(intent))
            handleIncomingSharedUrl(intent)
            maybeShowFirstRunTour()
        }
    }

    /**
     * Walks a first-time user through the three things they need to know.
     *
     * Delayed because the Home feed is a fragment: its paste card does not exist yet
     * when onCreate runs, and the tour needs the real on-screen bounds of the views it
     * points at. Steps whose target is missing are skipped by the sequence itself.
     */
    private fun maybeShowFirstRunTour() {
        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        if (preferenceManager.hasSeenHomeTour()) return

        binding.root.postDelayed(
            {
                if (isFinishing || isDestroyed) return@postDelayed
                // Marked as soon as it starts: someone who backs out mid-tour has
                // already seen it, and re-showing it on the next launch would nag.
                preferenceManager.setHomeTourSeen()
                Analytics.logTourStarted()
                CoachMarkSequence.show(
                    activity = this,
                    steps = listOf(
                        CoachStep(
                            targetProvider = { findViewById(R.id.shareCtaCard) },
                            titleRes = R.string.coach_paste_title,
                            bodyRes = R.string.coach_paste_body,
                        ),
                        CoachStep(
                            targetProvider = { binding.navExploreItem },
                            titleRes = R.string.coach_explore_title,
                            bodyRes = R.string.coach_explore_body,
                            paddingPx = 6,
                        ),
                        CoachStep(
                            targetProvider = { binding.navProfileItem },
                            titleRes = R.string.coach_profile_title,
                            bodyRes = R.string.coach_profile_body,
                            paddingPx = 6,
                        ),
                    ),
                    onFinished = { completed -> Analytics.logTourFinished(completed) },
                )
            },
            tourStartDelayMs,
        )
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
        switchTab(R.id.menu_profile)
    }

    @JvmOverloads
    fun submitVideoUrl(videoUrl: String, source: String = Analytics.SOURCE_IN_APP) {
        if (areNotificationsDisabled()) {
            showNotificationPrompt {
                viewModel.submitSharedUrl(videoUrl, source)
            }
        } else {
            viewModel.submitSharedUrl(videoUrl, source)
        }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home, R.id.menu_explore, R.id.menu_profile -> {
                    switchTab(item.itemId)
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
        binding.navHomeItem.setOnClickListener { switchTab(R.id.menu_home) }
        binding.navExploreItem.setOnClickListener { switchTab(R.id.menu_explore) }
        binding.navProfileItem.setOnClickListener { switchTab(R.id.menu_profile) }
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                        }

                        selectedTabId != R.id.menu_home -> {
                            switchTab(R.id.menu_home)
                        }

                        shouldExitApp() -> {
                            finish()
                        }

                        else -> {
                            lastBackPressedAt = SystemClock.elapsedRealtime()
                            Toast.makeText(
                                this@HomeActivity,
                                getString(R.string.press_back_again_to_exit),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
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
                    switchTab(R.id.menu_home)
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
            // Worth counting: a spike here tells us which platform to build next.
            Analytics.logUnsupportedPlatform(sharedUrl.analyticsPlatform())
            UnsupportedPlatformDialog.show(this)
            return
        }
        submitVideoUrl(sharedUrl, Analytics.SOURCE_SHARE_SHEET)
    }

    private fun switchTab(itemId: Int) {
        selectedTabId = itemId
        binding.navigationView.setCheckedItem(itemId)
        updateBottomNavSelection(itemId)

        val fragment = when (itemId) {
            R.id.menu_explore -> ExploreFragment()
            R.id.menu_profile -> ProfileFragment()
            else -> HomeFeedFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun updateBottomNavSelection(itemId: Int) {
        updateNavItem(
            isSelected = itemId == R.id.menu_home,
            icon = binding.navHomeIcon,
            iconBackground = binding.navHomeIconBg,
            label = binding.navHomeLabel,
            selectedIconRes = R.drawable.ic_home_outline,
            unselectedIconRes = R.drawable.ic_home_outline,
        )
        updateNavItem(
            isSelected = itemId == R.id.menu_explore,
            icon = binding.navExploreIcon,
            iconBackground = binding.navExploreIconBg,
            label = binding.navExploreLabel,
            selectedIconRes = R.drawable.ic_explore_tab,
            unselectedIconRes = R.drawable.ic_explore_tab,
        )
        updateNavItem(
            isSelected = itemId == R.id.menu_profile,
            icon = binding.navProfileIcon,
            iconBackground = binding.navProfileIconBg,
            label = binding.navProfileLabel,
            selectedIconRes = R.drawable.ic_profile_outline,
            unselectedIconRes = R.drawable.ic_profile_outline,
        )
    }

    private fun updateNavItem(
        isSelected: Boolean,
        icon: ImageView,
        iconBackground: View,
        label: TextView,
        selectedIconRes: Int,
        unselectedIconRes: Int,
    ) {
        icon.setImageResource(if (isSelected) selectedIconRes else unselectedIconRes)
        icon.setColorFilter(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.brand_primary else R.color.brand_muted,
            ),
        )
        iconBackground.setBackgroundResource(android.R.color.transparent)
        label.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.brand_primary else R.color.brand_muted,
            ),
        )
        label.typeface = ResourcesCompat.getFont(this, R.font.inter)
    }

    private fun refreshCurrentTab() {
        when (selectedTabId) {
            R.id.menu_explore -> viewModel.loadExplore()
            else -> viewModel.loadHistory()
        }
    }

    private fun openInstagram() {
        // A device with no browser and no Instagram app has nothing to handle this.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
        }.onFailure {
            Toast.makeText(this, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
        }
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
        Analytics.logLogout()
        // Detaches this device's future events from the account that just signed out.
        Analytics.setUserId(null)
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

    private fun resolveInitialTab(intent: Intent): Int {
        return when (intent.getStringExtra(IntentExtras.EXTRA_DEFAULT_TAB)?.lowercase()) {
            IntentExtras.TAB_EXPLORE -> R.id.menu_explore
            IntentExtras.TAB_PROFILE -> R.id.menu_profile
            IntentExtras.TAB_HOME -> R.id.menu_home
            else -> R.id.menu_home
        }
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

    private fun areNotificationsDisabled(): Boolean {
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        return permissionMissing || !NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun showNotificationPrompt(onContinue: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notifications_off_title)
            .setMessage(R.string.notifications_off_message)
            .setNegativeButton(R.string.notifications_continue_without) { dialog, _ ->
                dialog.dismiss()
                onContinue()
            }
            .setPositiveButton(R.string.notifications_turn_on) { dialog, _ ->
                dialog.dismiss()
                onContinue()
                openNotificationSettings()
            }
            .show()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    private fun shouldExitApp(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return now - lastBackPressedAt <= BACK_PRESS_EXIT_WINDOW_MS
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1101
        private const val BACK_PRESS_EXIT_WINDOW_MS = 2000L
    }
}
