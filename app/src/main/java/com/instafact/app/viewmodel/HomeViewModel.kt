package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.data.model.SubmitResponse
import com.instafact.app.data.repository.SubmissionRepository
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.UiState
import com.instafact.app.utils.analyticsPlatform
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

    private var pollJob: Job? = null

    /**
     * Loads the user's checks.
     *
     * [showLoading] is false for background refreshes - a resume, or a poll while
     * something is still processing. Those must not flip the screen to a spinner and
     * blank the list the user is already reading.
     */
    fun loadHistory(showLoading: Boolean = true) {
        if (showLoading) _historyState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.getHistory()
                .onSuccess { history ->
                    _historyState.value = UiState.Success(history)
                    syncPollingWith(history)
                    submissionRepository.backfillHistoryMetadata(history)
                        .onSuccess { enrichedHistory ->
                            if (enrichedHistory != history) {
                                _historyState.postValue(UiState.Success(enrichedHistory))
                            }
                        }
                }
                .onFailure { error ->
                    // A failed background refresh keeps whatever is on screen; only a
                    // deliberate load is allowed to replace the list with an error.
                    if (showLoading) _historyState.value = UiState.Error(error.message.orEmpty())
                }
        }
    }

    /**
     * Re-reads the list without disturbing what is on screen.
     *
     * Called when Home comes back into view. A reel submitted from the share sheet, or
     * one that finished while the app was backgrounded, would otherwise sit stale until
     * the user pulled to refresh or restarted the app.
     */
    fun refreshHistoryQuietly() = loadHistory(showLoading = false)

    /**
     * Keeps polling while any check is still running, and stops as soon as none are.
     *
     * A fact-check takes tens of seconds and finishes server-side, so without this a row
     * sits on "pending" until something else happens to reload it. The poll exists only
     * while there is something to watch, and gives up after [MAX_POLL_DURATION_MS] so a
     * query wedged in processing cannot leave the app polling forever.
     */
    private fun syncPollingWith(history: List<HistoryItemResponse>) {
        val hasWorkInFlight = history.any { it.status.lowercase() in IN_FLIGHT_STATUSES }
        if (!hasWorkInFlight) {
            stopPolling()
            return
        }
        if (pollJob?.isActive == true) return

        pollJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < MAX_POLL_DURATION_MS) {
                delay(POLL_INTERVAL_MS)
                loadHistory(showLoading = false)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
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

    /**
     * [source] records how the link reached the app (share sheet vs pasted in-app).
     * Both entry points funnel through here, so this is the one place the whole submit
     * funnel can be measured.
     */
    fun submitSharedUrl(videoUrl: String, source: String = Analytics.SOURCE_IN_APP) {
        val platform = videoUrl.analyticsPlatform()
        Analytics.logSubmitStarted(platform, source)
        _submitState.value = UiState.Loading
        viewModelScope.launch {
            submissionRepository.submitVideo(videoUrl)
                .onSuccess { response ->
                    Analytics.logSubmitSucceeded(platform, response.queryId)
                    _submitState.value = UiState.Success(response)
                    // Pull the new row in here rather than leaving it to whichever screen
                    // happens to be observing: both the share sheet and the in-app paste
                    // funnel through this, and the reel has to show up either way.
                    loadHistory(showLoading = false)
                }
                .onFailure { error ->
                    Analytics.logSubmitFailed(platform, error.message.orEmpty())
                    _submitState.value = UiState.Error(error.message.orEmpty())
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
                    Analytics.logHistoryItemDeleted(queryId)
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

    private companion object {
        /** Statuses that mean the server is still working on a check. */
        val IN_FLIGHT_STATUSES = setOf("pending", "processing")
        const val POLL_INTERVAL_MS = 5_000L
        /** Give up after this long so a wedged query cannot poll forever. */
        const val MAX_POLL_DURATION_MS = 5 * 60 * 1000L
    }
}
