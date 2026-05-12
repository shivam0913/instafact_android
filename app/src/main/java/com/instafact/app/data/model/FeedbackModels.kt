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
