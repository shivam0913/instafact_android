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
    val status: String,
    val verdict: String?,
)

data class DetailResponse(
    val queryId: Int,
    val videoUrl: String,
    val status: String,
    val verdict: String?,
    val confidence: Int?,
    val explanation: String?,
)
