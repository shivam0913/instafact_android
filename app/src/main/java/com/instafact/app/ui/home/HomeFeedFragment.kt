package com.instafact.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.FragmentHomeFeedBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
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
    private var selectedFilter: HomeFilter = HomeFilter.ALL

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
        submissionAdapter = SubmissionAdapter(
            userVoteLookup = { queryId -> viewModel.getUserVoteType(queryId) },
            onFeedbackClicked = { item, feedbackType -> submitItemFeedback(item, feedbackType) },
            onMoreClicked = { anchor, item -> showItemMenu(anchor, item) },
        ) { item ->
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
        binding.notificationButton.setOnClickListener { openNotificationSettings() }
        binding.filterButton.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.filter_coming_soon), Toast.LENGTH_SHORT).show()
        }
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
        bindFilters()
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
                    renderItems(allItems)
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

    private fun bindFilters() {
        binding.filterAllChip.setOnClickListener { updateFilter(HomeFilter.ALL) }
        binding.filterTrueChip.setOnClickListener { updateFilter(HomeFilter.TRUE) }
        binding.filterMisleadingChip.setOnClickListener { updateFilter(HomeFilter.MISLEADING) }
        binding.filterFalseChip.setOnClickListener { updateFilter(HomeFilter.FALSE) }
        updateFilter(HomeFilter.ALL)
    }

    private fun updateFilter(filter: HomeFilter) {
        selectedFilter = filter
        updateFilterChipStyles()
        renderItems(allItems)
    }

    private fun updateFilterChipStyles() {
        styleFilterChip(binding.filterAllChip, selectedFilter == HomeFilter.ALL)
        styleFilterChip(binding.filterTrueChip, selectedFilter == HomeFilter.TRUE)
        styleFilterChip(binding.filterMisleadingChip, selectedFilter == HomeFilter.MISLEADING)
        styleFilterChip(binding.filterFalseChip, selectedFilter == HomeFilter.FALSE)
    }

    private fun styleFilterChip(chip: View, isSelected: Boolean) {
        chip.setBackgroundResource(
            if (isSelected) R.drawable.bg_home_filter_chip_selected else R.drawable.bg_home_filter_chip,
        )
        if (chip is android.widget.TextView) {
            chip.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.white else R.color.brand_text,
                ),
            )
            chip.compoundDrawablesRelative.filterNotNull().forEach { drawable ->
                drawable.mutate().setTint(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isSelected) R.color.white else iconTintForChip(chip.id),
                    ),
                )
            }
        }
    }

    private fun iconTintForChip(chipId: Int): Int {
        return when (chipId) {
            R.id.filterTrueChip -> R.color.brand_status_true
            R.id.filterMisleadingChip -> R.color.brand_status_misleading
            R.id.filterFalseChip -> R.color.brand_status_false
            else -> R.color.brand_text
        }
    }

    private fun renderItems(items: List<HistoryItemResponse>) {
        val filteredItems = when (selectedFilter) {
            HomeFilter.ALL -> items
            HomeFilter.TRUE -> items.filter { it.verdict.equals("true", true) }
            HomeFilter.MISLEADING -> items.filter { it.verdict.equals("misleading", true) }
            HomeFilter.FALSE -> items.filter { it.verdict.equals("false", true) }
        }
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

    private fun openNotificationSettings() {
        val context = requireContext()
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        startActivity(intent)
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

            !UrlValidator.isSupportedVideoUrl(videoUrl) -> Toast.makeText(
                requireContext(),
                getString(R.string.unsupported_url),
                Toast.LENGTH_SHORT,
            ).show()

            else -> (activity as? HomeActivity)?.submitVideoUrl(videoUrl) ?: viewModel.submitSharedUrl(videoUrl)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private enum class HomeFilter {
        ALL,
        TRUE,
        MISLEADING,
        FALSE,
    }
}
