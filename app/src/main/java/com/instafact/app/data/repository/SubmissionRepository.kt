package com.instafact.app.data.repository

import android.content.Context
import retrofit2.HttpException
import com.instafact.app.R
import com.instafact.app.data.api.ApiService
import com.instafact.app.data.model.AppFeedbackRequest
import com.instafact.app.data.model.AppFeedbackResponse
import com.instafact.app.data.model.ChatHistoryResponse
import com.instafact.app.data.model.ChatMessageRequest
import com.instafact.app.data.model.ChatMessageResponse
import com.instafact.app.data.model.DeleteHistoryResponse
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.data.model.FeedbackRequest
import com.instafact.app.data.model.FeedbackResponse
import com.instafact.app.data.model.FeedbackType
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.data.model.SubmitRequest
import com.instafact.app.data.model.SubmitResponse
import com.instafact.app.utils.ApiErrorParser
import com.instafact.app.utils.ClientVideoMetadata
import com.instafact.app.utils.PreferenceManager
import com.instafact.app.utils.SessionDebugLogger
import com.instafact.app.utils.VideoMetadataFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class SubmissionRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager,
) {

    private val metadataFetchLock = Any()
    private val metadataMissCooldownMillis = 6 * 60 * 60 * 1000L

    suspend fun getHistory(): Result<List<HistoryItemResponse>> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getHistory(requireUserId()).map { applyCachedMetadata(it) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun getDetail(queryId: Int): Result<DetailResponse> = withContext(Dispatchers.IO) {
        runCatching {
            applyCachedMetadata(apiService.getDetail(queryId))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun getExplore(limit: Int = 20): Result<List<ExploreItemResponse>> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getExplore(limit).map { applyCachedMetadata(it) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun backfillHistoryMetadata(items: List<HistoryItemResponse>): Result<List<HistoryItemResponse>> =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    items.map { item ->
                        async { backfillMetadata(item) }
                    }.awaitAll()
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
            )
        }

    suspend fun backfillExploreMetadata(items: List<ExploreItemResponse>): Result<List<ExploreItemResponse>> =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    items.map { item ->
                        async { backfillMetadata(item) }
                    }.awaitAll()
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
            )
        }

    suspend fun backfillDetailMetadata(detail: DetailResponse): Result<DetailResponse> = withContext(Dispatchers.IO) {
        runCatching {
            backfillMetadata(detail)
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
            val metadata = resolveMetadata(videoUrl)
            SessionDebugLogger.logMetadataFetchResult("SubmissionRepository.submitVideo", videoUrl, metadata)
            apiService.submitVideo(
                SubmitRequest(
                    userId = requireUserId(),
                    videoUrl = videoUrl,
                    title = metadata?.title,
                    channelName = metadata?.channelName,
                    thumbnailUrl = metadata?.thumbnailUrl,
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
                preferenceManager.saveUserVoteType(queryId, feedbackType.name.lowercase())
                Result.success(it)
            },
            onFailure = { throwable ->
                if (throwable is HttpException && throwable.code() == 409) {
                    preferenceManager.markQueryAsVoted(queryId)
                    preferenceManager.saveUserVoteType(queryId, feedbackType.name.lowercase())
                    Result.failure(IllegalStateException(context.getString(R.string.already_voted)))
                } else {
                    Result.failure(Exception(ApiErrorParser.getMessage(context, throwable), throwable))
                }
            },
        )
    }

    /**
     * Sends an app rating with its full comment.
     *
     * No local vote guard like submitFeedback has: someone whose complaint we fix should
     * be able to tell us so later, and the backend keeps every submission on purpose.
     */
    suspend fun submitAppFeedback(
        rating: Int,
        reasons: List<String>,
        comment: String?,
        trigger: String?,
        appVersion: String?,
    ): Result<AppFeedbackResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.submitAppFeedback(
                AppFeedbackRequest(
                    rating = rating,
                    reasons = reasons,
                    comment = comment?.takeIf { it.isNotBlank() },
                    trigger = trigger,
                    appVersion = appVersion,
                ),
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                Result.failure(Exception(ApiErrorParser.getMessage(context, throwable), throwable))
            },
        )
    }

    suspend fun deleteHistory(queryId: Int): Result<DeleteHistoryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.deleteHistory(
                queryId = queryId,
                userId = requireUserId(),
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    fun hasVoted(queryId: Int): Boolean = preferenceManager.hasUserVoted(queryId)

    fun getUserVoteType(queryId: Int): String? = preferenceManager.getUserVoteType(queryId)

    fun getUserId(): Int? = preferenceManager.getUserId()

    fun getPhoneNumber(): String? = preferenceManager.getPhoneNumber()

    fun getProfileName(): String? = preferenceManager.getProfileName()

    fun getProfileImageUrl(): String? = preferenceManager.getProfileImageUrl()

    fun getCachedVideoMetadata(videoUrl: String): ClientVideoMetadata? = preferenceManager.getVideoMetadata(videoUrl)

    private fun requireUserId(): Int {
        return preferenceManager.getUserId()
            ?: throw IllegalStateException(context.getString(R.string.unknown_error))
    }

    private fun applyCachedMetadata(item: HistoryItemResponse): HistoryItemResponse {
        val metadata = preferenceManager.getVideoMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }

    private fun applyCachedMetadata(item: ExploreItemResponse): ExploreItemResponse {
        val metadata = preferenceManager.getVideoMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }

    private fun applyCachedMetadata(item: DetailResponse): DetailResponse {
        val metadata = preferenceManager.getVideoMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }

    private fun needsMetadata(title: String?, channelName: String?, thumbnailUrl: String?): Boolean {
        return title.isNullOrBlank() || channelName.isNullOrBlank() || thumbnailUrl.isNullOrBlank()
    }

    private fun mergedMetadata(videoUrl: String): ClientVideoMetadata? {
        return resolveMetadata(videoUrl)
    }

    private fun resolveMetadata(videoUrl: String): ClientVideoMetadata? {
        preferenceManager.getVideoMetadata(videoUrl)?.let { return it }
        if (!preferenceManager.shouldRetryVideoMetadata(videoUrl, metadataMissCooldownMillis)) {
            SessionDebugLogger.logMetadataFetchSkipped(
                "SubmissionRepository.resolveMetadata",
                videoUrl,
                "recent_miss",
            )
            return null
        }

        return synchronized(metadataFetchLock) {
            preferenceManager.getVideoMetadata(videoUrl)?.let { return@synchronized it }
            if (!preferenceManager.shouldRetryVideoMetadata(videoUrl, metadataMissCooldownMillis)) {
                SessionDebugLogger.logMetadataFetchSkipped(
                    "SubmissionRepository.resolveMetadata",
                    videoUrl,
                    "recent_miss_after_lock",
                )
                return@synchronized null
            }

            val fetchedMetadata = VideoMetadataFetcher.fetch(videoUrl)
            if (fetchedMetadata != null) {
                preferenceManager.saveVideoMetadata(videoUrl, fetchedMetadata)
            } else {
                preferenceManager.markVideoMetadataMiss(videoUrl)
            }
            fetchedMetadata
        }
    }

    private fun backfillMetadata(item: HistoryItemResponse): HistoryItemResponse {
        if (!needsMetadata(item.title, item.channelName, item.thumbnailUrl)) return item
        val metadata = mergedMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }

    private fun backfillMetadata(item: ExploreItemResponse): ExploreItemResponse {
        if (!needsMetadata(item.title, item.channelName, item.thumbnailUrl)) return item
        val metadata = mergedMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }

    private fun backfillMetadata(item: DetailResponse): DetailResponse {
        if (!needsMetadata(item.title, item.channelName, item.thumbnailUrl)) return item
        val metadata = mergedMetadata(item.videoUrl) ?: return item
        return item.copy(
            title = item.title ?: metadata.title,
            channelName = item.channelName ?: metadata.channelName ?: metadata.creatorId,
            thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
        )
    }
}
