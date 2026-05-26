package com.instafact.app.data.model

data class SubmitRequest(
    val userId: Int,
    val videoUrl: String,
)

data class SubmitResponse(
    val queryId: Int,
    val status: String,
)

data class HistoryItemResponse(
    val queryId: Int,
    val videoUrl: String,
    val title: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val status: String,
    val verdict: String?,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
)

data class DetailResponse(
    val queryId: Int,
    val videoUrl: String,
    val title: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val status: String,
    val verdict: String?,
    val confidence: Int?,
    val explanation: String?,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
)

data class ExploreItemResponse(
    val queryId: Int,
    val videoUrl: String,
    val title: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val platform: String,
    val verdict: String?,
    val confidence: Int?,
    val tags: List<String> = emptyList(),
    val factCheckCount: Int = 0,
)
