package com.instafact.app.ui.explore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.databinding.FragmentExploreBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.viewmodel.HomeViewModel

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        ViewModelFactory((requireActivity().application as InstafactApplication).appContainer)
    }

    private lateinit var recentAdapter: ExploreAdapter
    private lateinit var trendingAdapter: ExploreHighlightAdapter
    private lateinit var sharedAdapter: ExploreHighlightAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Analytics.logScreenView("explore", "ExploreFragment")
        // Which row a tap came from is the useful signal here (is trending or recent
        // actually driving opens?); the adapters do not surface an index to report.
        fun clickHandlerFor(section: String): (ExploreItemResponse) -> Unit = { item ->
            Analytics.logExploreItemOpened(item.queryId, section)
            startActivity(
                Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra(IntentExtras.EXTRA_QUERY_ID, item.queryId)
                },
            )
        }
        trendingAdapter = ExploreHighlightAdapter(clickHandlerFor("trending"))
        sharedAdapter = ExploreHighlightAdapter(clickHandlerFor("shared"))
        recentAdapter = ExploreAdapter(clickHandlerFor("recent"))

        binding.trendingRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.trendingRecyclerView.adapter = trendingAdapter
        binding.sharedRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.sharedRecyclerView.adapter = sharedAdapter
        binding.exploreRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.exploreRecyclerView.adapter = recentAdapter
        binding.exploreRecyclerView.isNestedScrollingEnabled = false
        binding.swipeRefreshLayout.setColorSchemeResources(com.instafact.app.R.color.brand_primary)
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadExplore() }
        binding.retryButton.setOnClickListener { viewModel.loadExplore() }
        binding.exploreDrawerButton.setOnClickListener {
            (activity as? HomeActivity)?.openDrawer()
        }

        observeState()
        if (viewModel.exploreState.value == UiState.Idle) {
            viewModel.loadExplore()
        }
    }

    private fun observeState() {
        viewModel.exploreState.observe(viewLifecycleOwner) { state ->
            binding.swipeRefreshLayout.isRefreshing = false
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    if (recentAdapter.itemCount == 0) {
                        binding.exploreProgressBar.visibility = View.VISIBLE
                    }
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.errorStateContainer.visibility = View.GONE
                }

                is UiState.Success -> {
                    binding.exploreProgressBar.visibility = View.GONE
                    binding.errorStateContainer.visibility = View.GONE
                    val items = state.data
                    val trendingItems = items
                        .filter { verdict ->
                            verdict.verdict.equals("false", true) ||
                                verdict.verdict.equals("misleading", true)
                        }
                        .sortedByDescending { it.confidence ?: 0 }
                        .ifEmpty { items }
                        .take(6)
                    val sharedItems = items.sortedByDescending { it.factCheckCount }.take(6)
                    val recentItems = items.take(8)

                    trendingAdapter.submitList(trendingItems)
                    sharedAdapter.submitList(sharedItems)
                    recentAdapter.submitList(recentItems)
                    binding.emptyStateContainer.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }

                is UiState.Error -> {
                    binding.exploreProgressBar.visibility = View.GONE
                    if (recentAdapter.itemCount == 0) {
                        binding.errorStateContainer.visibility = View.VISIBLE
                        binding.errorTextView.text = state.message
                    } else {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
