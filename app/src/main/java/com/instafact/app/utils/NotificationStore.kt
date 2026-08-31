package com.instafact.app.utils

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Locally persisted record of a push the app has received.
 *
 * Note: FCM only routes a message through [com.instafact.app.fcm.InstafactFirebaseMessagingService]
 * while the app is in the foreground when the payload carries a `notification` block. Pushes that
 * land while the app is backgrounded go straight to the system tray and are not recorded here.
 */
data class NotificationRecord(
    val id: String,
    val title: String,
    val body: String,
    val queryId: Int?,
    val receivedAt: Long,
    val isRead: Boolean,
)

class NotificationStore(context: Context) {

    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<NotificationRecord> {
        val raw = sharedPreferences.getString(KEY_ITEMS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.toRecord() }
            .sortedByDescending { it.receivedAt }
    }

    fun add(title: String, body: String, queryId: Int?) {
        val record = NotificationRecord(
            id = "${System.currentTimeMillis()}-${queryId ?: 0}",
            title = title,
            body = body,
            queryId = queryId,
            receivedAt = System.currentTimeMillis(),
            isRead = false,
        )
        val updated = (listOf(record) + getAll()).take(MAX_ITEMS)
        persist(updated)
    }

    fun markRead(id: String) {
        persist(getAll().map { if (it.id == id) it.copy(isRead = true) else it })
    }

    fun markAllRead() {
        persist(getAll().map { it.copy(isRead = true) })
    }

    fun clear() {
        sharedPreferences.edit { remove(KEY_ITEMS) }
    }

    fun unreadCount(): Int = getAll().count { !it.isRead }

    private fun persist(records: List<NotificationRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        sharedPreferences.edit { putString(KEY_ITEMS, array.toString()) }
    }

    private fun NotificationRecord.toJson(): JSONObject = JSONObject().apply {
        put(FIELD_ID, id)
        put(FIELD_TITLE, title)
        put(FIELD_BODY, body)
        put(FIELD_QUERY_ID, queryId ?: JSONObject.NULL)
        put(FIELD_RECEIVED_AT, receivedAt)
        put(FIELD_IS_READ, isRead)
    }

    private fun JSONObject.toRecord(): NotificationRecord? {
        val id = optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return null
        return NotificationRecord(
            id = id,
            title = optString(FIELD_TITLE),
            body = optString(FIELD_BODY),
            queryId = if (isNull(FIELD_QUERY_ID)) null else optInt(FIELD_QUERY_ID).takeIf { it > 0 },
            receivedAt = optLong(FIELD_RECEIVED_AT),
            isRead = optBoolean(FIELD_IS_READ, false),
        )
    }

    companion object {
        private const val PREFS_NAME = "instafact_notifications"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 100
        private const val FIELD_ID = "id"
        private const val FIELD_TITLE = "title"
        private const val FIELD_BODY = "body"
        private const val FIELD_QUERY_ID = "query_id"
        private const val FIELD_RECEIVED_AT = "received_at"
        private const val FIELD_IS_READ = "is_read"
    }
}
