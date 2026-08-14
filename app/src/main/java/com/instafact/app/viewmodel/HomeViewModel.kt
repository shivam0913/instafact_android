package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.data.model.SubmitResponse
import com.instafact.app.data.repository.SubmissionRepository
import com.instafact.app.utils.UiState
import kotlinx.coroutines.launch

class HomeViewModel(
    private val submissionRepository: SubmissionRepository,
) : ViewModel() {

    private val _historyState = MutableLiveData<UiState<List<HistoryItemResponse>>>(UiState.Idle)
    val historyState: LiveData<UiState<List<HistoryItemResponse>>> = _historyState

    private val _submitState = MutableLiveData<UiState<SubmitResponse>>(UiState.Idle)
    val submitState: LiveData<UiState<SubmitResponse>> = _submitState

    private val _exploreState = MutableLiveData<UiState<List<ExploreItemResponse>>>(UiState.Idle)
    val exploreState: LiveData<UiState<List<ExploreItemResponse>>> = _exploreState

    private val _feedbackState = MutableLiveData<UiState<String>>(UiState.Idle)
    val feedbackState: LiveData<UiState<String>> = _feedbackState

    private val _deleteState = MutableLiveData<UiState<String>>(UiState.Idle)
    val deleteState: LiveData<UiState<String>> = _deleteState

    fun loadHistory() {
        _historyState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getHistory()
                .onSuccess { history ->
                    _historyState.value = UiState.Success(history)
                    submissionRepository.backfillHistoryMetadata(history)
                        .onSuccess { enrichedHistory ->
                            if (enrichedHistory != history) {
                                _historyState.postValue(UiState.Success(enrichedHistory))
                            }
                        }
                }
                .onFailure { error ->
                    _historyState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun loadExplore(limit: Int = 20) {
        _exploreState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getExplore(limit)
                .onSuccess { items ->
                    _exploreState.value = UiState.Success(items)
                    submissionRepository.backfillExploreMetadata(items)
                        .onSuccess { enrichedItems ->
                            if (enrichedItems != items) {
                                _exploreState.postValue(UiState.Success(enrichedItems))
                            }
                        }
                }
                .onFailure { error ->
                    _exploreState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun submitSharedUrl(videoUrl: String) {
        _submitState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitVideo(videoUrl)
                .onSuccess { response ->
                    _submitState.value = UiState.Success(response)
                }
                .onFailure { error ->
                    _submitState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun submitFeedback(queryId: Int, feedbackType: FeedbackType) {
        _feedbackState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitFeedback(queryId, feedbackType)
                .onSuccess { response ->
                    _feedbackState.value = UiState.Success(response.message)
                    loadHistory()
                }
                .onFailure { error ->
                    _feedbackState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun deleteHistory(queryId: Int) {
        _deleteState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.deleteHistory(queryId)
                .onSuccess { response ->
                    _deleteState.value = UiState.Success(response.message)
                    loadHistory()
                }
                .onFailure { error ->
                    _deleteState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    fun resetSubmitState() {
        _submitState.value = UiState.Idle
    }

    fun resetFeedbackState() {
        _feedbackState.value = UiState.Idle
    }

    fun resetDeleteState() {
        _deleteState.value = UiState.Idle
    }

    fun getUserId(): Int? = submissionRepository.getUserId()

    fun getPhoneNumber(): String? = submissionRepository.getPhoneNumber()

    fun getProfileName(): String? = submissionRepository.getProfileName()

    fun getProfileImageUrl(): String? = submissionRepository.getProfileImageUrl()

    fun getUserVoteType(queryId: Int): String? = submissionRepository.getUserVoteType(queryId)
}
