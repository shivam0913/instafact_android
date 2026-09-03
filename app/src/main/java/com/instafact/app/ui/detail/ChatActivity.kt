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
import com.instafact.app.utils.applySystemBarAndImeInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.viewmodel.DetailViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter

    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private var queryId: Int = -1

    /** Held while a send is in flight so a failure can restore it to the input. */
    private var lastSentMessage: String? = null

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
        // Not applySystemBarInsets: this screen has a text field, so it has to react
        // to the keyboard as well or the input ends up underneath it.
        binding.rootLayout.applySystemBarAndImeInsets()

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
        // The keyboard shortens the list; without this the newest message ends up hidden
        // behind it, which looks identical to the message not having been sent.
        binding.chatRecyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) scrollToLatest()
        }
        binding.chatInputEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToLatest()
        }
    }

    /**
     * Parks the list on its newest item.
     *
     * Posted rather than called inline because the item that was just added has not been
     * laid out yet at the point the callers run, and re-read from the adapter so a list
     * that changed again in the meantime cannot scroll past the end.
     */
    private fun scrollToLatest() {
        binding.chatRecyclerView.post {
            val lastIndex = chatAdapter.itemCount - 1
            if (lastIndex >= 0) {
                binding.chatRecyclerView.scrollToPosition(lastIndex)
            }
        }
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
                    binding.chatEmptyTextView.isVisible = state.data.isEmpty()
                    // The scroll has to wait for the commit callback: submitList diffs on a
                    // background thread, so scrolling on the next line would run against the
                    // old item count and leave the newest message off screen.
                    chatAdapter.submitList(state.data) { scrollToLatest() }
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
                UiState.Loading -> {
                    binding.sendChatButton.isEnabled = false
                    // Visibly dimmed, so a second tap plainly does nothing.
                    binding.sendChatButton.alpha = 0.45f
                }
                is UiState.Success -> {
                    binding.sendChatButton.isEnabled = true
                    binding.sendChatButton.alpha = 1f
                    lastSentMessage = null
                    viewModel.resetChatSendState()
                }

                is UiState.Error -> {
                    binding.sendChatButton.isEnabled = true
                    binding.sendChatButton.alpha = 1f
                    // Give the question back rather than making them retype it.
                    lastSentMessage?.let { failed ->
                        binding.chatInputEditText.setText(failed)
                        binding.chatInputEditText.setSelection(failed.length)
                        lastSentMessage = null
                    }
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetChatSendState()
                }
            }
        }
    }

    /**
     * Sends the typed question.
     *
     * The box is emptied here rather than on success: the message is already on screen by
     * the time this returns, so leaving the text sitting in the input made it look like
     * nothing had been sent. It is held in [lastSentMessage] so a failure can put it back
     * instead of losing what the user typed.
     */
    private fun sendCurrentMessage() {
        val message = binding.chatInputEditText.text?.toString().orEmpty().trim()
        if (message.isBlank()) {
            Toast.makeText(this, getString(R.string.chat_empty_error), Toast.LENGTH_SHORT).show()
            return
        }
        lastSentMessage = message
        binding.chatInputEditText.text?.clear()
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
