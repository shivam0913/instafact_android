package com.instafact.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.databinding.ActivityDetailBinding
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayStatus
import com.instafact.app.utils.displayVerdict
import com.instafact.app.viewmodel.DetailViewModel

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var queryId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!(application as InstafactApplication).appContainer.preferenceManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        queryId = intent.getIntExtra(IntentExtras.EXTRA_QUERY_ID, -1)
        if (queryId <= 0) {
            Toast.makeText(this, getString(R.string.missing_query_id), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUi()
        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadDetail(queryId)
        }
    }

    private fun setupUi() {
        binding.backButton.setOnClickListener { finish() }
        binding.thumbsUpButton.setOnClickListener {
            viewModel.submitFeedback(queryId, FeedbackType.UP)
        }
        binding.thumbsDownButton.setOnClickListener {
            viewModel.submitFeedback(queryId, FeedbackType.DOWN)
        }
        updateFeedbackButtons(viewModel.hasUserVoted(queryId))
    }

    private fun observeViewModel() {
        viewModel.detailState.observe(this) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    binding.detailProgressBar.visibility = android.view.View.VISIBLE
                    binding.contentScrollView.visibility = android.view.View.GONE
                    binding.detailErrorTextView.visibility = android.view.View.GONE
                }
                is UiState.Success -> showDetail(state.data)
                is UiState.Error -> {
                    binding.detailProgressBar.visibility = android.view.View.GONE
                    binding.contentScrollView.visibility = android.view.View.GONE
                    binding.detailErrorTextView.visibility = android.view.View.VISIBLE
                    binding.detailErrorTextView.text = state.message
                }
            }
        }

        viewModel.feedbackState.observe(this) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> updateFeedbackButtons(false, enabled = false)
                is UiState.Success -> {
                    Toast.makeText(this, state.data, Toast.LENGTH_SHORT).show()
                    updateFeedbackButtons(true)
                    viewModel.resetFeedbackState()
                }
                is UiState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    updateFeedbackButtons(viewModel.hasUserVoted(queryId))
                    viewModel.resetFeedbackState()
                }
            }
        }
    }

    private fun showDetail(detail: DetailResponse) {
        binding.detailProgressBar.visibility = android.view.View.GONE
        binding.contentScrollView.visibility = android.view.View.VISIBLE
        binding.detailErrorTextView.visibility = android.view.View.GONE
        binding.videoUrlTextView.text = detail.videoUrl
        binding.statusTextView.text = detail.status.displayStatus(this)
        binding.verdictTextView.text = detail.verdict.displayVerdict(this)
        binding.confidenceTextView.text = detail.confidence.displayConfidence(this)
        binding.explanationTextView.text = detail.explanation ?: getString(R.string.detail_result_pending)
        updateFeedbackButtons(viewModel.hasUserVoted(queryId))
    }

    private fun updateFeedbackButtons(voted: Boolean, enabled: Boolean = !voted) {
        binding.thumbsUpButton.isEnabled = enabled
        binding.thumbsDownButton.isEnabled = enabled
    }
}
