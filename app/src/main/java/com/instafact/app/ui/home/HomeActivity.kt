package com.instafact.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityHomeBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var submissionAdapter: SubmissionAdapter

    private val viewModel: HomeViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

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

        setupList()
        setupActions()
        observeViewModel()
        maybeRequestNotificationPermission()

        if (savedInstanceState == null) {
            viewModel.loadHistory()
            handleIncomingSharedUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSharedUrl(intent)
        viewModel.loadHistory()
    }

    private fun setupList() {
        submissionAdapter = SubmissionAdapter { item ->
            startActivity(
                Intent(this, DetailActivity::class.java).apply {
                    putExtra(IntentExtras.EXTRA_QUERY_ID, item.queryId)
                },
            )
        }

        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = submissionAdapter
        }
    }

    private fun setupActions() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadHistory()
        }
        binding.retryButton.setOnClickListener {
            viewModel.loadHistory()
        }
    }

    private fun observeViewModel() {
        viewModel.historyState.observe(this) { state ->
            binding.swipeRefreshLayout.isRefreshing = false
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    if (submissionAdapter.itemCount == 0) {
                        binding.historyProgressBar.visibility = android.view.View.VISIBLE
                    }
                    binding.emptyStateContainer.visibility = android.view.View.GONE
                    binding.errorStateContainer.visibility = android.view.View.GONE
                }
                is UiState.Success -> {
                    binding.historyProgressBar.visibility = android.view.View.GONE
                    binding.errorStateContainer.visibility = android.view.View.GONE
                    val items = state.data
                    submissionAdapter.submitList(items)
                    binding.emptyStateContainer.visibility =
                        if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
                is UiState.Error -> {
                    binding.historyProgressBar.visibility = android.view.View.GONE
                    if (submissionAdapter.itemCount == 0) {
                        binding.errorStateContainer.visibility = android.view.View.VISIBLE
                        binding.emptyStateContainer.visibility = android.view.View.GONE
                        binding.errorTextView.text = state.message
                    } else {
                        Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.submitState.observe(this) { state ->
            when (state) {
                UiState.Idle -> {
                    binding.shareStatusCard.visibility = android.view.View.GONE
                }
                UiState.Loading -> {
                    binding.shareStatusCard.visibility = android.view.View.VISIBLE
                    binding.shareStatusTextView.text = getString(R.string.share_processing)
                }
                is UiState.Success -> {
                    binding.shareStatusCard.visibility = android.view.View.GONE
                    Toast.makeText(this, getString(R.string.submission_success), Toast.LENGTH_SHORT).show()
                    viewModel.loadHistory()
                    viewModel.resetSubmitState()
                }
                is UiState.Error -> {
                    binding.shareStatusCard.visibility = android.view.View.GONE
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
