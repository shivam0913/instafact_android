package com.instafact.app.data.model

data class ChatHistoryResponse(
    val queryId: Int,
    val messages: List<ChatMessageItem>,
)

data class ChatMessageRequest(
    val userId: Int,
    val queryId: Int,
    val message: String,
)

data class ChatMessageResponse(
    val queryId: Int,
    val answer: String,
    val messages: List<ChatMessageItem>,
)

data class ChatMessageItem(
    val id: Int,
    val role: String,
    val content: String,
    val createdAt: String,
)
