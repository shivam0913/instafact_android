package com.instafact.app.data.model

import com.google.gson.annotations.SerializedName

enum class FeedbackType {
    @SerializedName("up")
    UP,

    @SerializedName("down")
    DOWN,
}

data class FeedbackRequest(
    val userId: Int,
    val queryId: Int,
    val type: FeedbackType,
)

data class FeedbackResponse(
    val feedbackId: Int,
    val queryId: Int,
    val type: String,
    val message: String,
)

/**
 * App rating feedback. Unlike [FeedbackRequest] this is about the app itself, not one
 * fact-check, and it carries the full comment - the analytics copy is capped at 100
 * characters, so the backend is the only complete record.
 */
data class AppFeedbackRequest(
    val rating: Int,
    val reasons: List<String>,
    val comment: String?,
    val trigger: String?,
    val appVersion: String?,
    val platform: String = "android",
)

data class AppFeedbackResponse(
    val feedbackId: Int,
    val rating: Int,
    val message: String,
)
