package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.model.ChatMessageItem
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.repository.SubmissionRepository
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

    fun sendChatMessage(queryId: Int, message: String) {
        _chatSendState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.sendChatMessage(queryId, message)
                .onSuccess { response ->
                    _chatState.value = UiState.Success(response.messages)
                    _chatSendState.value = UiState.Success(response.answer)
                }
                .onFailure { error ->
                    _chatSendState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun submitFeedback(queryId: Int, feedbackType: FeedbackType) {
        _feedbackState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitFeedback(queryId, feedbackType)
                .onSuccess { response ->
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
