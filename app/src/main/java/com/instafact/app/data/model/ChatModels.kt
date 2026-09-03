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

/**
 * Ids for messages that exist only on this device while a reply is in flight.
 *
 * Negative so they can never collide with a server row id, which lets the adapter tell an
 * optimistic bubble from a real one and lets DiffUtil replace them cleanly when the real
 * messages arrive.
 */
object LocalChatIds {
    const val PENDING_QUESTION = -1
    const val PENDING_REPLY = -2
}
