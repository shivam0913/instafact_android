package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun loadHistory() {
        _historyState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getHistory()
                .onSuccess { history ->
                    _historyState.value = UiState.Success(history)
                }
                .onFailure { error ->
                    _historyState.value = UiState.Error(error.message.orEmpty())
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

    fun resetSubmitState() {
        _submitState.value = UiState.Idle
    }
}
