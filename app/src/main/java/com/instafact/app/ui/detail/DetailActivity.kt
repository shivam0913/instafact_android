package com.instafact.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.databinding.ActivityDetailBinding
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.ui.webview.InAppBrowserActivity
import com.instafact.app.utils.HtmlRenderer
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.MarkdownRenderer
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.utils.displayVerdict
import com.instafact.app.utils.ellipsized
import com.instafact.app.utils.loadThumbnail
import com.instafact.app.utils.platformIconRes
import com.instafact.app.utils.sourceUrlLabel
import com.instafact.app.utils.toReadableHeadline
import com.instafact.app.utils.toRelativeTimeLabel
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictSectionTitle
import com.instafact.app.viewmodel.DetailViewModel

class DetailActivity : AppCompatActivity() {

    companion object {
        private const val MENU_SHARE = 1
        private const val MENU_OPEN = 2
        private const val MENU_COPY = 3
    }

    private enum class DetailTab {
        SUMMARY,
        DETAILS,
        REFERENCES,
    }

    private data class ReferenceItem(
        val title: String,
        val url: String,
    )

    private lateinit var binding: ActivityDetailBinding

    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var queryId: Int = -1
    private var currentVideoUrl: String = ""
    private var currentDetail: DetailResponse? = null
    private var currentReferences: List<ReferenceItem> = emptyList()
    private var isExplanationExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_background,
            navigationBarColorRes = R.color.brand_background,
            lightStatusBar = true,
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
        binding.bookmarkButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.detail_save_coming_soon), Toast.LENGTH_SHORT).show()
        }
        binding.moreButton.setOnClickListener { showMoreMenu(it) }
        binding.copyLinkButton.setOnClickListener { copyCurrentLink() }
        binding.shareReelButton.setOnClickListener { shareCurrentResult() }
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
        binding.readMoreTextView.setOnClickListener { toggleExplanationExpanded() }
        binding.tabSummaryContainer.setOnClickListener { selectTab(DetailTab.SUMMARY, true) }
        binding.tabDetailsContainer.setOnClickListener { selectTab(DetailTab.DETAILS, true) }
        binding.tabReferencesContainer.setOnClickListener { selectTab(DetailTab.REFERENCES, true) }
        binding.viewAllReferencesTextView.setOnClickListener { showAllReferencesDialog() }
        updateFeedbackButtons(null)
        selectTab(DetailTab.SUMMARY, false)
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
                UiState.Loading -> setFeedbackButtonsEnabled(false)
                is UiState.Success -> {
                    Toast.makeText(this, state.data, Toast.LENGTH_SHORT).show()
                    currentDetail = currentDetail?.let { detail ->
                        when (effectiveVoteType(detail)) {
                            "up", "down" -> detail
                            else -> {
                                val voteType = when {
                                    state.data.contains("down", ignoreCase = true) -> "down"
                                    else -> "up"
                                }
                                if (voteType == "up") {
                                    detail.copy(
                                        upvotes = detail.upvotes + 1,
                                        currentUserVote = "up",
                                    )
                                } else {
                                    detail.copy(
                                        downvotes = detail.downvotes + 1,
                                        currentUserVote = "down",
                                    )
                                }
                            }
                        }
                    }
                    currentDetail?.let { updateFeedbackButtons(it) }
                    setFeedbackButtonsEnabled(false)
                    viewModel.resetFeedbackState()
                }

                is UiState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    currentDetail?.let { updateFeedbackButtons(it) }
                    viewModel.resetFeedbackState()
                }
            }
        }
    }

    private fun showDetail(detail: DetailResponse) {
        binding.detailProgressBar.visibility = View.GONE
        binding.contentScrollView.visibility = View.VISIBLE
        binding.detailErrorTextView.visibility = View.GONE

        currentDetail = detail
        currentVideoUrl = detail.videoUrl
        currentReferences = extractReferences(detail)
        isExplanationExpanded = false

        val verdictText = detail.verdict.displayVerdict(this)
        val verdictColor = ContextCompat.getColor(this, detail.verdict.verdictColorRes())
        val verdictLabelColor = ContextCompat.getColor(this, detail.verdict.verdictColorRes())

        binding.statusTextView.text = detail.title?.takeIf { it.isNotBlank() } ?: detail.videoUrl.toReadableHeadline()
        binding.platformImageView.setImageResource(detail.videoUrl.platformIconRes())
        binding.videoUrlTextView.text = detail.videoUrl.sourceUrlLabel().ellipsized(28)
        binding.videoMetaTextView.isVisible = false
        binding.thumbnailImageView.loadThumbnail(detail.thumbnailUrl)

        binding.verdictTextView.text = verdictText.toVerdictDisplay()
        binding.verdictTextView.setTextColor(verdictLabelColor)
        binding.verdictIconImageView.setColorFilter(verdictColor)
        binding.verdictIconImageView.setImageResource(verdictIconRes(detail.verdict))

        val summaryText = extractSummary(detail).ifBlank { getString(R.string.detail_result_pending) }
        binding.resultSummaryTextView.text = buildVerdictBlurb(detail)
        binding.summaryTextView.text = summaryText

        binding.confidenceTextView.text = detail.confidence?.let { getString(R.string.confidence_format, it) }
            ?: getString(R.string.unknown)
        binding.confidenceTextView.setTextColor(verdictLabelColor)
        binding.confidenceGaugeView.setConfidence(detail.confidence, verdictColor)

        renderExplanation(detail)
        renderTags(detail.tags)
        renderReferences(currentReferences)
        binding.referencesTitleTextView.text = getString(
            R.string.detail_references_title_with_count,
            currentReferences.size,
        )
        binding.viewAllReferencesTextView.isVisible = false
        binding.askAiButton.visibility = if (detail.status.equals("completed", ignoreCase = true)) View.VISIBLE else View.GONE
        updateFeedbackButtons(detail)
        selectTab(DetailTab.SUMMARY, false)
    }

    private fun renderExplanation(detail: DetailResponse) {
        val detailsHtml = detail.detailsHtml?.trim().orEmpty()
        val explanation = legacyBodyMarkdown(detail.explanation)
        binding.explanationTitleTextView.text = detail.verdict.verdictSectionTitle(this)
        if (detailsHtml.isBlank() && explanation.isBlank()) {
            binding.explanationTextView.text = getString(R.string.detail_result_pending)
            binding.readMoreTextView.isVisible = false
            return
        }

        val plainExplanation = if (detailsHtml.isNotBlank()) {
            HtmlRenderer.toPlainText(detailsHtml)
        } else {
            cleanMarkdownToPlainText(explanation)
        }
        val shouldCollapse = plainExplanation.length > 280 || plainExplanation.count { it == '\n' } > 3
        if (detailsHtml.isNotBlank()) {
            val displayedHtml = if (shouldCollapse && !isExplanationExpanded) {
                trimHtmlForPreview(detailsHtml)
            } else {
                detailsHtml
            }
            HtmlRenderer.render(
                textView = binding.explanationTextView,
                html = displayedHtml,
                onLinkClicked = { openInAppBrowser(it, getString(R.string.chat_source_title)) },
            )
        } else {
            val displayedMarkdown = if (shouldCollapse && !isExplanationExpanded) {
                trimMarkdownForPreview(explanation)
            } else {
                explanation
            }
            MarkdownRenderer.render(
                textView = binding.explanationTextView,
                markdown = displayedMarkdown,
                onLinkClicked = { openInAppBrowser(it, getString(R.string.chat_source_title)) },
            )
        }
        binding.readMoreTextView.isVisible = shouldCollapse
        binding.readMoreTextView.text = if (isExplanationExpanded) {
            getString(R.string.detail_read_less)
        } else {
            getString(R.string.detail_read_more)
        }
    }

    private fun renderTags(tags: List<String>) {
        binding.tagsContainer.removeAllViews()
        binding.sourcesLabelTextView.isVisible = tags.isNotEmpty()
        binding.tagsScrollView.isVisible = tags.isNotEmpty()
        if (tags.isEmpty()) return

        tags.forEach { tag ->
            binding.tagsContainer.addView(createTagChip(tag))
        }
    }

    private fun renderReferences(references: List<ReferenceItem>) {
        binding.referencesContainer.removeAllViews()
        binding.emptyReferencesTextView.isVisible = references.isEmpty()
        if (references.isEmpty()) return

        references.forEachIndexed { index, reference ->
            val row = createReferenceRow(reference)
            binding.referencesContainer.addView(row)
            if (index < references.lastIndex) {
                binding.referencesContainer.addView(createVerticalSpacer())
            }
        }
    }

    private fun createTagChip(label: String, accent: Boolean = false): Chip {
        return Chip(this).apply {
            text = label
            isClickable = false
            isCheckable = false
            setEnsureMinTouchTargetSize(false)
            chipStrokeWidth = 0f
            textSize = 9.5f
            minHeight = dp(22)
            minimumHeight = dp(22)
            chipMinHeight = dp(22).toFloat()
            chipStartPadding = dp(4).toFloat()
            chipEndPadding = dp(4).toFloat()
            textStartPadding = dp(2).toFloat()
            textEndPadding = dp(2).toFloat()
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (accent) R.color.brand_primary else R.color.brand_primary,
                ),
            )
            chipBackgroundColor = ContextCompat.getColorStateList(
                context,
                if (accent) R.color.brand_primary_soft else R.color.brand_primary_soft,
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.marginEnd = dp(3)
            layoutParams = params
        }
    }

    private fun createReferenceRow(reference: ReferenceItem): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            foreground = ContextCompat.getDrawable(context, R.drawable.bg_reference_row_ripple)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { openInAppBrowser(reference.url, reference.title) }

            addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))
                    setImageResource(R.drawable.ic_link)
                    setColorFilter(ContextCompat.getColor(context, R.color.brand_primary))
                },
            )

            addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                        it.marginStart = dp(10)
                    }
                    text = reference.title
                    setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
                    textSize = 12f
                    maxLines = 3
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                },
            )
        }
    }

    private fun createVerticalSpacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12),
            )
        }
    }

    private fun shareCurrentResult() {
        val detail = currentDetail ?: return
        val summary = extractSummary(detail).ifBlank {
            getString(R.string.detail_result_pending)
        }
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
        val url = currentVideoUrl.ifBlank { currentDetail?.videoUrl.orEmpty() }
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openInAppBrowser(url: String, title: String) {
        startActivity(
            Intent(this, InAppBrowserActivity::class.java).apply {
                putExtra(IntentExtras.EXTRA_URL, url)
                putExtra(IntentExtras.EXTRA_TITLE, title)
            },
        )
    }

    private fun copyCurrentLink() {
        val detail = currentDetail ?: return
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.detail_link_label), detail.videoUrl),
        )
        Toast.makeText(this, getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_SHARE, 0, getString(R.string.share_result))
            menu.add(0, MENU_OPEN, 1, getString(R.string.detail_open_original))
            menu.add(0, MENU_COPY, 2, getString(R.string.detail_copy_link))
            setOnMenuItemClickListener(::onMoreMenuClicked)
        }.show()
    }

    private fun onMoreMenuClicked(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_SHARE -> {
                shareCurrentResult()
                true
            }

            MENU_OPEN -> {
                openVideoLink()
                true
            }

            MENU_COPY -> {
                copyCurrentLink()
                true
            }

            else -> false
        }
    }

    private fun updateFeedbackButtons(detail: DetailResponse?) {
        val userVote = effectiveVoteType(detail)
        val upSelected = userVote == "up"
        val downSelected = userVote == "down"

        applyFeedbackButtonState(
            button = binding.thumbsUpButton,
            count = detail?.upvotes ?: 0,
            selected = upSelected,
            filledIconRes = R.drawable.ic_helpful_filled,
            outlineIconRes = R.drawable.ic_helpful_outline,
        )
        applyFeedbackButtonState(
            button = binding.thumbsDownButton,
            count = detail?.downvotes ?: 0,
            selected = downSelected,
            filledIconRes = R.drawable.ic_not_helpful_filled,
            outlineIconRes = R.drawable.ic_not_helpful_outline,
        )
        setFeedbackButtonsEnabled(userVote == null)
    }

    private fun applyFeedbackButtonState(
        button: MaterialButton,
        count: Int,
        selected: Boolean,
        filledIconRes: Int,
        outlineIconRes: Int,
    ) {
        button.text = count.toString()
        button.setIconResource(if (selected) filledIconRes else outlineIconRes)
        val textColor = ContextCompat.getColor(
            this,
            if (selected) R.color.brand_primary else R.color.brand_text,
        )
        button.setTextColor(textColor)
        button.iconTint = ContextCompat.getColorStateList(
            this,
            if (selected) R.color.brand_primary else R.color.brand_text,
        )
        button.strokeColor = ContextCompat.getColorStateList(
            this,
            if (selected) R.color.brand_primary else R.color.brand_border,
        )
    }

    private fun setFeedbackButtonsEnabled(enabled: Boolean) {
        binding.thumbsUpButton.isEnabled = enabled
        binding.thumbsDownButton.isEnabled = enabled
    }

    private fun effectiveVoteType(detail: DetailResponse?): String? {
        return detail?.currentUserVote?.lowercase()
            ?: (application as InstafactApplication).appContainer.submissionRepository.getUserVoteType(queryId)?.lowercase()
    }

    private fun toggleExplanationExpanded() {
        isExplanationExpanded = !isExplanationExpanded
        currentDetail?.let(::renderExplanation)
    }

    private fun selectTab(tab: DetailTab, scroll: Boolean) {
        updateTabVisuals(
            selected = tab == DetailTab.SUMMARY,
            labelView = binding.tabSummaryTextView,
            indicatorView = binding.tabSummaryIndicator,
        )
        updateTabVisuals(
            selected = tab == DetailTab.DETAILS,
            labelView = binding.tabDetailsTextView,
            indicatorView = binding.tabDetailsIndicator,
        )
        updateTabVisuals(
            selected = tab == DetailTab.REFERENCES,
            labelView = binding.tabReferencesTextView,
            indicatorView = binding.tabReferencesIndicator,
        )

        if (!scroll) return
        val target = when (tab) {
            DetailTab.SUMMARY -> binding.summarySectionCard
            DetailTab.DETAILS -> binding.summarySectionCard
            DetailTab.REFERENCES -> binding.referencesSectionCard
        }
        binding.contentScrollView.post {
            binding.contentScrollView.smoothScrollTo(0, target.top - dp(16))
        }
    }

    private fun updateTabVisuals(
        selected: Boolean,
        labelView: TextView,
        indicatorView: View,
    ) {
        val textColor = ContextCompat.getColor(
            this,
            if (selected) R.color.brand_primary else R.color.brand_muted,
        )
        labelView.setTextColor(textColor)
        indicatorView.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.brand_primary else android.R.color.transparent,
            ),
        )
    }

    private fun showAllReferencesDialog() {
        if (currentReferences.isEmpty()) return
        val labels = currentReferences.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.detail_references_title_with_count, currentReferences.size))
            .setItems(labels) { _, which ->
                val reference = currentReferences.getOrNull(which) ?: return@setItems
                openInAppBrowser(reference.url, reference.title)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun extractSummary(detail: DetailResponse): String {
        val htmlSummary = HtmlRenderer.toPlainText(detail.summaryHtml)
        if (htmlSummary.isNotBlank()) return htmlSummary

        val cleanText = cleanMarkdownToPlainText(legacyBodyMarkdown(detail.explanation))
        if (cleanText.isBlank()) return ""
        val firstParagraph = cleanText.split(Regex("\\n\\s*\\n")).firstOrNull().orEmpty().trim()
        if (firstParagraph.isNotBlank()) return firstParagraph
        return cleanText.split(Regex("(?<=[.!?])\\s+"))
            .take(2)
            .joinToString(" ")
            .trim()
    }

    private fun buildVerdictBlurb(detail: DetailResponse): String {
        return when (detail.verdict?.lowercase()) {
            "true" -> getString(R.string.detail_verdict_blurb_true)
            "false" -> getString(R.string.detail_verdict_blurb_false)
            "misleading" -> getString(R.string.detail_verdict_blurb_misleading)
            else -> getString(R.string.detail_verdict_blurb_unverified)
        }
    }

    private fun legacyBodyMarkdown(markdown: String?): String {
        val content = markdown?.trim().orEmpty()
        if (content.isBlank()) return ""
        return content.split(Regex("\\n\\s*##\\s*Sources reviewed", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            .orEmpty()
            .ifBlank {
                content.substringBefore("Sources reviewed").trim()
            }
    }

    private fun cleanMarkdownToPlainText(markdown: String): String {
        return markdown
            .replace(Regex("\\[(.+?)]\\((https?://[^)]+)\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[*_`>#-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun trimMarkdownForPreview(markdown: String): String {
        val paragraphs = markdown.trim().split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return paragraphs.take(2).joinToString("\n\n")
    }

    private fun trimHtmlForPreview(html: String): String {
        val paragraphMatches = Regex("<p\\b[^>]*>.*?</p>|<li\\b[^>]*>.*?</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
        return if (paragraphMatches.isNotEmpty()) {
            paragraphMatches.take(2).joinToString("")
        } else {
            html
        }
    }

    private fun extractReferences(detail: DetailResponse): List<ReferenceItem> {
        val referenceHtml = detail.referencesHtml?.trim().orEmpty()
        if (referenceHtml.isNotBlank()) {
            val htmlLinks = Regex("<a\\b[^>]*href=[\"'](https?://[^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(referenceHtml)
                .map {
                    ReferenceItem(
                        title = HtmlRenderer.toPlainText(it.groupValues[2]).ifBlank { it.groupValues[1].toReferenceTitle() },
                        url = it.groupValues[1].trim(),
                    )
                }
                .toList()
                .distinctBy { it.url }
            if (htmlLinks.isNotEmpty()) return htmlLinks
        }

        val content = legacySourcesMarkdown(detail.explanation)
        if (content.isBlank()) return emptyList()

        val markdownLinks = Regex("\\[(.+?)]\\((https?://[^)]+)\\)")
            .findAll(content)
            .map {
                ReferenceItem(
                    title = it.groupValues[1].trim(),
                    url = it.groupValues[2].trim(),
                )
            }
            .toList()

        val plainLinks = Regex("https?://[^\\s)]+")
            .findAll(content)
            .map { match ->
                val url = match.value.trim().trimEnd('.', ',', ';')
                ReferenceItem(
                    title = url.toReferenceTitle(),
                    url = url,
                )
            }
            .toList()

        return (markdownLinks + plainLinks)
            .distinctBy { it.url }
    }

    private fun legacySourcesMarkdown(markdown: String?): String {
        val content = markdown?.trim().orEmpty()
        if (content.isBlank()) return ""
        val splitByHeader = content.split(Regex("\\n\\s*##\\s*Sources reviewed", RegexOption.IGNORE_CASE))
        if (splitByHeader.size > 1) {
            return splitByHeader.drop(1).joinToString("\n").trim()
        }
        return content.substringAfter("Sources reviewed", "").trim()
    }

    private fun String.toReferenceTitle(): String {
        return runCatching {
            val uri = Uri.parse(this)
            val host = uri.host?.removePrefix("www.").orEmpty()
            if (host.isNotBlank()) host else this
        }.getOrDefault(this)
    }

    private fun String.toVerdictDisplay(): String {
        return when (lowercase()) {
            "true" -> getString(R.string.verdict_mostly_true)
            else -> this
        }
    }

    private fun verdictIconRes(verdict: String?): Int {
        return when (verdict?.lowercase()) {
            "true" -> R.drawable.ic_verdict_true_outline
            "false" -> R.drawable.ic_verdict_false_outline
            "misleading" -> R.drawable.ic_verdict_misleading_outline
            else -> R.drawable.ic_help_outline
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
