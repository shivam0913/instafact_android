package com.instafact.app.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.databinding.ActivityDetailBinding
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.utils.displayConfidence
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.ellipsized
import com.instafact.app.utils.explanationAsBullets
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.platformIconRes
import com.instafact.app.utils.platformSourceLabel
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.toRelativeTimeLabel
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictSectionTitle
import com.instafact.app.viewmodel.DetailViewModel

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var queryId: Int = -1
    private var currentVideoUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_dark_surface,
            navigationBarColorRes = R.color.brand_background,
            lightStatusBar = false,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

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
        binding.shareButton.setOnClickListener { shareCurrentResult() }
        binding.linkShareButton.setOnClickListener { shareCurrentResult() }
        binding.videoUrlTextView.setOnClickListener { openVideoLink() }
        binding.videoPreviewContainer.setOnClickListener { openVideoLink() }
        binding.thumbnailImageView.setOnClickListener { openVideoLink() }
        binding.askAiButton.setOnClickListener {
            startActivity(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra(IntentExtras.EXTRA_QUERY_ID, queryId)
                },
            )
        }
        binding.thumbsUpButton.setOnClickListener { viewModel.submitFeedback(queryId, FeedbackType.UP) }
        binding.thumbsDownButton.setOnClickListener { viewModel.submitFeedback(queryId, FeedbackType.DOWN) }
        updateFeedbackButtons(viewModel.hasUserVoted(queryId))
    }

    private fun observeViewModel() {
        viewModel.detailState.observe(this) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    binding.detailProgressBar.visibility = View.VISIBLE
                    binding.contentScrollView.visibility = View.GONE
                    binding.detailErrorTextView.visibility = View.GONE
                }

                is UiState.Success -> showDetail(state.data)
                is UiState.Error -> {
                    binding.detailProgressBar.visibility = View.GONE
                    binding.contentScrollView.visibility = View.GONE
                    binding.detailErrorTextView.visibility = View.VISIBLE
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
        binding.detailProgressBar.visibility = View.GONE
        binding.contentScrollView.visibility = View.VISIBLE
        binding.detailErrorTextView.visibility = View.GONE

        val verdictText = detail.verdict.displayVerdict(this)
        val verdictColor = ContextCompat.getColor(this, detail.verdict.verdictColorRes())
        currentVideoUrl = detail.videoUrl

        binding.statusTextView.text = detail.title?.takeIf { it.isNotBlank() } ?: detail.videoUrl.toReadableHeadline()
        binding.platformImageView.setImageResource(detail.videoUrl.platformIconRes())
        binding.platformNameTextView.text =
            detail.channelName?.takeIf { it.isNotBlank() } ?: detail.videoUrl.platformSourceLabel(this)

        val relativeTime = detail.createdAt.toRelativeTimeLabel(this)
        binding.videoMetaTextView.visibility = if (relativeTime == null) View.GONE else View.VISIBLE
        binding.videoMetaTextView.text = relativeTime?.let { getString(R.string.detail_shared_meta, it) }

        binding.videoUrlTextView.text = detail.videoUrl.ellipsized(46)
        binding.thumbnailImageView.loadThumbnail(detail.thumbnailUrl)
        binding.verdictTextView.text = verdictText
        binding.confidenceTextView.text = detail.confidence.displayConfidence(this)
        binding.verdictBannerCard.setCardBackgroundColor(verdictColor)
        binding.verifiedChipTextView.text = getString(R.string.ai_verified)
        binding.checkedSourcesChipTextView.text = getString(R.string.checked_sources_count, detail.tags.size)
        binding.explanationTitleTextView.text = detail.verdict.verdictSectionTitle(this)
        binding.explanationTextView.text =
            (detail.explanation ?: getString(R.string.detail_result_pending)).explanationAsBullets()
        renderTags(detail.tags)
        binding.askAiButton.visibility = if (detail.status.equals("completed", ignoreCase = true)) View.VISIBLE else View.GONE
        updateFeedbackButtons(viewModel.hasUserVoted(queryId))
    }

    private fun renderTags(tags: List<String>) {
        binding.tagsChipGroup.removeAllViews()
        val hasTags = tags.isNotEmpty()
        binding.sourcesLabelTextView.visibility = if (hasTags) View.VISIBLE else View.GONE
        binding.tagsChipGroup.visibility = if (hasTags) View.VISIBLE else View.GONE

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isClickable = false
                isCheckable = false
                setEnsureMinTouchTargetSize(false)
                setTextColor(ContextCompat.getColor(context, R.color.brand_text))
                chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.brand_primary_soft)
            }
            binding.tagsChipGroup.addView(chip)
        }
    }

    private fun shareCurrentResult() {
        val detailState = viewModel.detailState.value as? UiState.Success ?: return
        val detail = detailState.data
        val summary = detail.explanation ?: getString(R.string.detail_result_pending)
        val shareText = getString(
            R.string.detail_share_template,
            detail.videoUrl,
            detail.verdict.displayVerdict(this),
            summary,
            getString(R.string.app_download_link),
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                getString(R.string.share_result),
            ),
        )
    }

    private fun openVideoLink() {
        val url = currentVideoUrl.ifBlank { binding.videoUrlTextView.text?.toString().orEmpty() }
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun updateFeedbackButtons(voted: Boolean, enabled: Boolean = !voted) {
        binding.thumbsUpButton.isEnabled = enabled
        binding.thumbsDownButton.isEnabled = enabled
    }
}
