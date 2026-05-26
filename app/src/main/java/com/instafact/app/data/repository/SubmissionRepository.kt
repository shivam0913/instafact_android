package com.instafact.app.data.repository

import android.content.Context
import retrofit2.HttpException
import com.instafact.app.R
import com.instafact.app.data.api.ApiService
import com.instafact.app.data.model.ChatHistoryResponse
import com.instafact.app.data.model.ChatMessageRequest
import com.instafact.app.data.model.ChatMessageResponse
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.data.model.FeedbackRequest
import com.instafact.app.data.model.FeedbackResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.data.model.SubmitRequest
import com.instafact.app.data.model.SubmitResponse
import com.instafact.app.utils.ApiErrorParser
import com.instafact.app.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubmissionRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager,
) {

    suspend fun getHistory(): Result<List<HistoryItemResponse>> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getHistory(requireUserId())
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun getDetail(queryId: Int): Result<DetailResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getDetail(queryId)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun getExplore(limit: Int = 20): Result<List<ExploreItemResponse>> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getExplore(limit)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun getChatHistory(queryId: Int): Result<ChatHistoryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getChatHistory(
                userId = requireUserId(),
                queryId = queryId,
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun sendChatMessage(
        queryId: Int,
        message: String,
    ): Result<ChatMessageResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.sendChatMessage(
                ChatMessageRequest(
                    userId = requireUserId(),
                    queryId = queryId,
                    message = message,
                ),
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun submitVideo(videoUrl: String): Result<SubmitResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.submitVideo(
                SubmitRequest(
                    userId = requireUserId(),
                    videoUrl = videoUrl,
                ),
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun submitFeedback(
        queryId: Int,
        feedbackType: FeedbackType,
    ): Result<FeedbackResponse> = withContext(Dispatchers.IO) {
        if (hasVoted(queryId)) {
            return@withContext Result.failure(IllegalStateException(context.getString(R.string.already_voted)))
        }

        runCatching {
            apiService.submitFeedback(
                FeedbackRequest(
                    userId = requireUserId(),
                    queryId = queryId,
                    type = feedbackType,
                ),
            )
        }.fold(
            onSuccess = {
                preferenceManager.markQueryAsVoted(queryId)
                Result.success(it)
            },
            onFailure = { throwable ->
                if (throwable is HttpException && throwable.code() == 409) {
                    preferenceManager.markQueryAsVoted(queryId)
                    Result.failure(IllegalStateException(context.getString(R.string.already_voted)))
                } else {
                    Result.failure(Exception(ApiErrorParser.getMessage(context, throwable), throwable))
                }
            },
        )
    }

    fun hasVoted(queryId: Int): Boolean = preferenceManager.hasUserVoted(queryId)

    fun getUserId(): Int? = preferenceManager.getUserId()

    fun getPhoneNumber(): String? = preferenceManager.getPhoneNumber()

    private fun requireUserId(): Int {
        return preferenceManager.getUserId()
            ?: throw IllegalStateException(context.getString(R.string.unknown_error))
    }
}
