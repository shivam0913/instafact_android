package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.model.ChatMessageItem
import com.instafact.app.data.model.LocalChatIds
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.repository.SubmissionRepository
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.UiState
import kotlinx.coroutines.launch

class DetailViewModel(
    private val submissionRepository: SubmissionRepository,
) : ViewModel() {

    private val _detailState = MutableLiveData<UiState<DetailResponse>>(UiState.Idle)
    val detailState: LiveData<UiState<DetailResponse>> = _detailState

    private val _feedbackState = MutableLiveData<UiState<String>>(UiState.Idle)
    val feedbackState: LiveData<UiState<String>> = _feedbackState

    private val _chatState = MutableLiveData<UiState<List<ChatMessageItem>>>(UiState.Idle)
    val chatState: LiveData<UiState<List<ChatMessageItem>>> = _chatState

    private val _chatSendState = MutableLiveData<UiState<String>>(UiState.Idle)
    val chatSendState: LiveData<UiState<String>> = _chatSendState

    private val _retryState = MutableLiveData<UiState<Int>>(UiState.Idle)
    val retryState: LiveData<UiState<Int>> = _retryState

    /**
     * Resubmits a failed reel and reports the query id to open.
     *
     * The backend may return a new query id (it retires the failed one and creates a
     * replacement), so the caller has to follow that id rather than keep polling the old.
     */
    fun retryCheck(videoUrl: String) {
        _retryState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitVideo(videoUrl)
                .onSuccess { _retryState.value = UiState.Success(it.queryId) }
                .onFailure { _retryState.value = UiState.Error(it.message.orEmpty()) }
        }
    }

    fun resetRetryState() {
        _retryState.value = UiState.Idle
    }

    fun loadDetail(queryId: Int) {
        _detailState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getDetail(queryId)
                .onSuccess { detail ->
                    _detailState.value = UiState.Success(detail)
                    submissionRepository.backfillDetailMetadata(detail)
                        .onSuccess { enrichedDetail ->
                            if (enrichedDetail != detail) {
                                _detailState.postValue(UiState.Success(enrichedDetail))
                            }
                        }
                }
                .onFailure { error ->
                    _detailState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun loadChatHistory(queryId: Int) {
        _chatState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getChatHistory(queryId)
                .onSuccess { response ->
                    _chatState.value = UiState.Success(response.messages)
                }
                .onFailure { error ->
                    _chatState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    /**
     * Sends a follow-up question and shows it immediately.
     *
     * The reply takes ten to thirty seconds. Waiting for it before showing anything meant
     * the screen sat completely still after a tap and then jumped straight to a finished
     * conversation, which reads as the app having ignored you. The question and a typing
     * bubble are appended locally first, then replaced by the server's list.
     */
    fun sendChatMessage(queryId: Int, message: String) {
        // Length only. The question itself is user content and never leaves the device.
        Analytics.logChatMessageSent(queryId, message.length)
        _chatSendState.value = UiState.Loading

        val existingMessages = (_chatState.value as? UiState.Success)?.data.orEmpty()
        _chatState.value = UiState.Success(
            existingMessages + listOf(
                ChatMessageItem(
                    id = LocalChatIds.PENDING_QUESTION,
                    role = "user",
                    content = message,
                    createdAt = "",
                ),
                ChatMessageItem(
                    id = LocalChatIds.PENDING_REPLY,
                    role = "assistant",
                    content = "",
                    createdAt = "",
                ),
            ),
        )

        viewModelScope.launch {
            submissionRepository.sendChatMessage(queryId, message)
                .onSuccess { response ->
                    _chatState.value = UiState.Success(response.messages)
                    _chatSendState.value = UiState.Success(response.answer)
                }
                .onFailure { error ->
                    // Roll the optimistic pair back so the screen matches the server
                    // again; the Activity puts the text back in the box to retry.
                    _chatState.value = UiState.Success(existingMessages)
                    _chatSendState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun submitFeedback(queryId: Int, feedbackType: FeedbackType, verdict: String? = null) {
        _feedbackState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitFeedback(queryId, feedbackType)
                .onSuccess { response ->
                    Analytics.logFeedbackSubmitted(queryId, feedbackType.name.lowercase(), verdict)
                    _feedbackState.value = UiState.Success(response.message)
                    loadDetail(queryId)
                }
                .onFailure { error ->
                    _feedbackState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun hasUserVoted(queryId: Int): Boolean = submissionRepository.hasVoted(queryId)

    fun resetFeedbackState() {
        _feedbackState.value = UiState.Idle
    }

    fun resetChatSendState() {
        _chatSendState.value = UiState.Idle
    }
}
