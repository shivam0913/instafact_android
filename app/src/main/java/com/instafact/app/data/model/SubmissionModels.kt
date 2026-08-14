package com.instafact.app.data.model

data class SubmitRequest(
    val userId: Int,
    val videoUrl: String,
    val title: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
)

data class SubmitResponse(
    val queryId: Int,
    val status: String,
)

data class DeleteHistoryResponse(
    val queryId: Int,
    val message: String,
)

data class HistoryItemResponse(
    val queryId: Int,
    val videoUrl: String,
    val createdAt: String? = null,
    val title: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val status: String,
    val verdict: String?,
    val confidence: Int? = null,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val currentUserVote: String? = null,
)

data class DetailResponse(
    val queryId: Int,
    val videoUrl: String,
    val createdAt: String? = null,
    val title: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val status: String,
    val verdict: String?,
    val confidence: Int?,
    val explanation: String?,
    val summaryHtml: String? = null,
    val detailsHtml: String? = null,
    val referencesHtml: String? = null,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val currentUserVote: String? = null,
)

data class ExploreItemResponse(
    val queryId: Int,
    val videoUrl: String,
    val createdAt: String? = null,
    val title: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val platform: String,
    val verdict: String?,
    val confidence: Int?,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val currentUserVote: String? = null,
)
