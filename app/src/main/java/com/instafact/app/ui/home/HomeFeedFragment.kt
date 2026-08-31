package com.instafact.app.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.FragmentHomeFeedBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.ui.notifications.NotificationsActivity
import com.instafact.app.utils.NotificationStore
import com.instafact.app.utils.ResultState
import com.instafact.app.utils.resultStateOf
import com.instafact.app.utils.verdictColorRes
import com.instafact.app.utils.verdictIconRes
import com.instafact.app.utils.verdictShortLabel
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.UnsupportedPlatformDialog
import com.instafact.app.utils.analyticsPlatform
import com.instafact.app.viewmodel.HomeViewModel
import androidx.fragment.app.activityViewModels

class HomeFeedFragment : Fragment() {

    private var _binding: FragmentHomeFeedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        ViewModelFactory((requireActivity().application as InstafactApplication).appContainer)
    }

    private lateinit var submissionAdapter: SubmissionAdapter
    private var allItems: List<HistoryItemResponse> = emptyList()
    private var selectedFilter: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Analytics.logScreenView("home_feed", "HomeFeedFragment")
        submissionAdapter = SubmissionAdapter(
            userVoteLookup = { queryId -> viewModel.getUserVoteType(queryId) },
            onFeedbackClicked = { item, feedbackType -> submitItemFeedback(item, feedbackType) },
            onMoreClicked = { anchor, item -> showItemMenu(anchor, item) },
        ) { item ->
            Analytics.logHistoryItemOpened(item.queryId)
            startActivity(
                Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra(IntentExtras.EXTRA_QUERY_ID, item.queryId)
                },
            )
        }

        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = submissionAdapter
            isNestedScrollingEnabled = false
        }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadHistory() }
        binding.retryButton.setOnClickListener { viewModel.loadHistory() }
        binding.submitButton.setOnClickListener { submitPastedUrl() }
        binding.notificationButton.setOnClickListener { openNotifications() }
        binding.pasteUrlEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitPastedUrl()
                true
            } else {
                false
            }
        }
        renderBrand()

        observeState()

        if (viewModel.historyState.value == UiState.Idle) {
            viewModel.loadHistory()
        }
    }

    private fun observeState() {
        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            binding.swipeRefreshLayout.isRefreshing = false
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    if (submissionAdapter.itemCount == 0) {
                        binding.historyProgressBar.visibility = View.VISIBLE
                    }
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.errorStateContainer.visibility = View.GONE
                }

                is UiState.Success -> {
                    binding.historyProgressBar.visibility = View.GONE
                    binding.errorStateContainer.visibility = View.GONE
                    allItems = state.data
                    refreshFilters()
                }

                is UiState.Error -> {
                    binding.historyProgressBar.visibility = View.GONE
                    if (submissionAdapter.itemCount == 0) {
                        binding.errorStateContainer.visibility = View.VISIBLE
                        binding.emptyStateContainer.visibility = View.GONE
                        binding.errorTextView.text = state.message
                    } else {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.submitState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Idle -> binding.submitButton.isEnabled = true
                UiState.Loading -> {
                    binding.submitButton.isEnabled = false
                    binding.submitButton.text = getString(R.string.share_processing)
                }

                is UiState.Success -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = getString(R.string.submit)
                    binding.pasteUrlEditText.text?.clear()
                }

                is UiState.Error -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = getString(R.string.submit)
                }
            }
        }

        viewModel.feedbackState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> Unit
                is UiState.Success -> {
                    Toast.makeText(requireContext(), state.data, Toast.LENGTH_SHORT).show()
                    viewModel.resetFeedbackState()
                }

                is UiState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetFeedbackState()
                }
            }
        }

        viewModel.deleteState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> Unit
                is UiState.Success -> {
                    Toast.makeText(requireContext(), state.data, Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
                }

                is UiState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
                }
            }
        }
    }

    /** Verdict chips are ordered like this whenever they are present in the feed. */
    private val verdictChipOrder = listOf(
        "true",
        "misleading",
        "false",
        "exaggerated",
        "hidden_information",
        "unverified",
        "unverifiable",
    )

    /** Rebuild the chip row for the current feed, then apply the active filter. */
    private fun refreshFilters() {
        val available = availableVerdicts()
        // The selected verdict may have vanished from the feed; fall back to All.
        if (selectedFilter != null && selectedFilter !in available) {
            selectedFilter = null
        }
        rebuildFilterChips(available)
        renderItems(allItems)
    }

    private fun updateFilter(verdict: String?) {
        selectedFilter = verdict
        rebuildFilterChips(availableVerdicts())
        renderItems(allItems)
    }

    /**
     * Only the verdicts the feed actually contains.
     *
     * A fixed chip row let the user pick a verdict that matched nothing and then stare at an
     * empty list. Items still being checked have no verdict, so they contribute no chip.
     */
    private fun availableVerdicts(): List<String> {
        return allItems
            .filter { resultStateOf(it.status, it.verdict) == ResultState.RESOLVED }
            .mapNotNull { item -> item.verdict?.lowercase()?.takeIf { it.isNotBlank() } }
            .distinct()
            .sortedBy { verdict ->
                verdictChipOrder.indexOf(verdict).takeIf { it >= 0 } ?: verdictChipOrder.size
            }
    }

    private fun rebuildFilterChips(available: List<String>) {
        val container = binding.filterChipContainer
        container.removeAllViews()

        // Nothing to narrow down: a lone "All" chip is just noise.
        binding.filterScrollView.isVisible = available.isNotEmpty()
        if (available.isEmpty()) return

        container.addView(buildFilterChip(null, getString(R.string.filter_all), isFirst = true))
        available.forEach { verdict ->
            container.addView(
                buildFilterChip(verdict, verdict.verdictShortLabel(requireContext()), isFirst = false),
            )
        }
    }

    private fun buildFilterChip(verdict: String?, label: String, isFirst: Boolean): TextView {
        val context = requireContext()
        val isSelected = selectedFilter == verdict
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).apply {
                if (!isFirst) marginStart = dp(4)
            }
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            setTypeface(ResourcesCompat.getFont(context, R.font.inter), Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setPaddingRelative(dp(12), 0, dp(12), 0)
            minWidth = dp(56)
            setBackgroundResource(
                if (isSelected) R.drawable.bg_home_filter_chip_selected else R.drawable.bg_home_filter_chip,
            )
            setTextColor(
                ContextCompat.getColor(context, if (isSelected) R.color.white else R.color.brand_text),
            )

            if (verdict != null) {
                val icon = ContextCompat.getDrawable(context, verdict.verdictIconRes())?.mutate()
                icon?.setTint(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) R.color.white else verdict.verdictColorRes(),
                    ),
                )
                icon?.setBounds(0, 0, dp(14), dp(14))
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = dp(4)
            }

            setOnClickListener { updateFilter(verdict) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderItems(items: List<HistoryItemResponse>) {
        val filteredItems = selectedFilter?.let { verdict ->
            items.filter { it.verdict.equals(verdict, ignoreCase = true) }
        } ?: items
        submissionAdapter.submitList(filteredItems)
        binding.emptyStateContainer.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderBrand() {
        val brand = getString(R.string.app_name)
        val span = SpannableString(brand)
        val splitIndex = brand.indexOf("Fact")
        if (splitIndex in 1 until brand.length) {
            span.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.brand_primary)),
                splitIndex,
                brand.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        binding.brandTextView.text = span
    }

    private fun openNotifications() {
        startActivity(Intent(requireContext(), NotificationsActivity::class.java))
    }

    private fun refreshNotificationDot() {
        binding.notificationDotView.isVisible = NotificationStore(requireContext()).unreadCount() > 0
    }

    private fun submitItemFeedback(item: HistoryItemResponse, feedbackType: FeedbackType) {
        if (viewModel.getUserVoteType(item.queryId) != null || !item.currentUserVote.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.already_voted), Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.submitFeedback(item.queryId, feedbackType)
    }

    private fun showItemMenu(anchor: View, item: HistoryItemResponse) {
        PopupMenu(requireContext(), anchor).apply {
            MenuInflater(requireContext()).inflate(R.menu.history_item_menu, menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_share_link -> {
                        shareItemLink(item)
                        true
                    }

                    R.id.action_delete -> {
                        viewModel.deleteHistory(item.queryId)
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    private fun shareItemLink(item: HistoryItemResponse) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.videoUrl)
                },
                getString(R.string.share_link),
            ),
        )
    }

    private fun submitPastedUrl() {
        val videoUrl = binding.pasteUrlEditText.text?.toString()?.trim().orEmpty()
        when {
            videoUrl.isBlank() -> Toast.makeText(
                requireContext(),
                getString(R.string.paste_url_required),
                Toast.LENGTH_SHORT,
            ).show()

            !UrlValidator.isSupportedVideoUrl(videoUrl) -> {
                Analytics.logUnsupportedPlatform(videoUrl.analyticsPlatform())
                UnsupportedPlatformDialog.show(requireContext())
            }

            else -> (activity as? HomeActivity)?.submitVideoUrl(videoUrl) ?: viewModel.submitSharedUrl(videoUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationDot()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
