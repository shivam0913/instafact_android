package com.instafact.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.databinding.FragmentHomeFeedBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.UrlValidator
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.viewmodel.HomeViewModel
import java.util.Calendar

class HomeFeedFragment : Fragment() {

    private var _binding: FragmentHomeFeedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        ViewModelFactory((requireActivity().application as InstafactApplication).appContainer)
    }

    private lateinit var submissionAdapter: SubmissionAdapter
    private var allItems: List<HistoryItemResponse> = emptyList()

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
        submissionAdapter = SubmissionAdapter { item ->
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
        binding.avatarButton.setOnClickListener {
            (activity as? HomeActivity)?.openDrawer()
        }
        binding.settingsButton.setOnClickListener {
            (activity as? HomeActivity)?.navigateToProfileTab()
        }
        updateGreetingForLocalTime()

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
                    updateGreetingForLocalTime()
                    binding.greetingNameTextView.text = viewModel.getPhoneNumber()
                        ?.takeLast(4)
                        ?.let { getString(R.string.home_user_name) + " " + getString(R.string.home_greeting_wave) }
                        ?: getString(R.string.home_user_name) + " " + getString(R.string.home_greeting_wave)
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
    }

    private fun renderItems(items: List<HistoryItemResponse>) {
        submissionAdapter.submitList(items)
        binding.emptyStateContainer.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateGreetingForLocalTime() {
        val greetingRes = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            in 17..21 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
        binding.greetingTextView.setText(greetingRes)
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

            else -> viewModel.submitSharedUrl(videoUrl)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
