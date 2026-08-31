package com.instafact.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.ActivityChatBinding
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.ui.webview.InAppBrowserActivity
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.viewmodel.DetailViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter

    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var queryId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Analytics.logScreenView("chat", "ChatActivity")
        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_surface,
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
        observeState()
        if (savedInstanceState == null) {
            viewModel.loadChatHistory(queryId)
        }
    }

    private fun setupUi() {
        binding.backButton.setOnClickListener { finish() }
        chatAdapter = ChatAdapter()
        chatAdapter.setOnLinkClicked { openInAppBrowser(it) }
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter
        binding.sendChatButton.setOnClickListener {
            sendCurrentMessage()
        }
        binding.suggestionOneButton.setOnClickListener { appendSuggestion(binding.suggestionOneButton.text.toString()) }
        binding.suggestionTwoButton.setOnClickListener { appendSuggestion(binding.suggestionTwoButton.text.toString()) }
        binding.suggestionThreeButton.setOnClickListener { appendSuggestion(binding.suggestionThreeButton.text.toString()) }
    }

    private fun observeState() {
        viewModel.chatState.observe(this) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    binding.chatProgressBar.isVisible = true
                    binding.chatEmptyTextView.isVisible = false
                }

                is UiState.Success -> {
                    binding.chatProgressBar.isVisible = false
                    chatAdapter.submitList(state.data)
                    binding.chatEmptyTextView.isVisible = state.data.isEmpty()
                    if (state.data.isNotEmpty()) {
                        binding.chatRecyclerView.scrollToPosition(state.data.lastIndex)
                    }
                }

                is UiState.Error -> {
                    binding.chatProgressBar.isVisible = false
                    binding.chatEmptyTextView.isVisible = true
                    binding.chatEmptyTextView.text = state.message
                }
            }
        }

        viewModel.chatSendState.observe(this) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> binding.sendChatButton.isEnabled = false
                is UiState.Success -> {
                    binding.sendChatButton.isEnabled = true
                    binding.chatInputEditText.text?.clear()
                    viewModel.resetChatSendState()
                }

                is UiState.Error -> {
                    binding.sendChatButton.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetChatSendState()
                }
            }
        }
    }

    private fun appendSuggestion(value: String) {
        binding.chatInputEditText.setText(value)
        binding.chatInputEditText.setSelection(value.length)
    }

    private fun sendCurrentMessage() {
        val message = binding.chatInputEditText.text?.toString().orEmpty().trim()
        if (message.isBlank()) {
            Toast.makeText(this, getString(R.string.chat_empty_error), Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.sendChatMessage(queryId, message)
    }

    private fun openInAppBrowser(url: String) {
        startActivity(
            Intent(this, InAppBrowserActivity::class.java).apply {
                putExtra(IntentExtras.EXTRA_URL, url)
                putExtra(IntentExtras.EXTRA_TITLE, getString(R.string.chat_source_title))
            },
        )
    }
}
