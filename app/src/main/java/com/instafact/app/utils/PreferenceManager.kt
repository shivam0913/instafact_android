package com.instafact.app.utils

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        userId: Int,
        authToken: String,
        refreshToken: String,
        phoneNumber: String,
    ) {
        sharedPreferences.edit {
            putInt(KEY_USER_ID, userId)
            putString(KEY_AUTH_TOKEN, authToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_PHONE_NUMBER, phoneNumber)
        }
    }

    fun updateAuthTokens(
        authToken: String,
        refreshToken: String,
    ) {
        sharedPreferences.edit {
            putString(KEY_AUTH_TOKEN, authToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
        }
    }

    fun saveProfileName(name: String?) {
        sharedPreferences.edit {
            if (name.isNullOrBlank()) {
                remove(KEY_PROFILE_NAME)
            } else {
                putString(KEY_PROFILE_NAME, name.trim())
            }
        }
    }

    fun saveProfileImageUrl(imageUrl: String?) {
        sharedPreferences.edit {
            if (imageUrl.isNullOrBlank()) {
                remove(KEY_PROFILE_IMAGE_URL)
            } else {
                putString(KEY_PROFILE_IMAGE_URL, imageUrl.trim())
            }
        }
    }

    fun saveFcmToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_FCM_TOKEN, token)
        }
    }

    /** The token last accepted by the backend, so re-registration only runs when it changes. */
    fun saveSyncedFcmToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_SYNCED_FCM_TOKEN, token)
        }
    }

    fun clearUserSession() {
        val fcmToken = getFcmToken()
        sharedPreferences.edit {
            remove(KEY_USER_ID)
            remove(KEY_AUTH_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_PHONE_NUMBER)
            remove(KEY_PROFILE_NAME)
            remove(KEY_PROFILE_IMAGE_URL)
            remove(KEY_VOTED_QUERY_IDS)
            remove(KEY_VOTE_TYPES)
            // The next account on this device has to register the token for itself.
            remove(KEY_SYNCED_FCM_TOKEN)
            if (fcmToken != null) {
                putString(KEY_FCM_TOKEN, fcmToken)
            }
        }
    }

    fun getUserId(): Int? {
        if (!sharedPreferences.contains(KEY_USER_ID)) return null
        return sharedPreferences.getInt(KEY_USER_ID, -1).takeIf { it > 0 }
    }

    fun getAuthToken(): String? = sharedPreferences.getString(KEY_AUTH_TOKEN, null)

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun getPhoneNumber(): String? = sharedPreferences.getString(KEY_PHONE_NUMBER, null)

    fun getProfileName(): String? = sharedPreferences.getString(KEY_PROFILE_NAME, null)

    fun getProfileImageUrl(): String? = sharedPreferences.getString(KEY_PROFILE_IMAGE_URL, null)

    fun getFcmToken(): String? = sharedPreferences.getString(KEY_FCM_TOKEN, null)

    fun getSyncedFcmToken(): String? = sharedPreferences.getString(KEY_SYNCED_FCM_TOKEN, null)

    fun isLoggedIn(): Boolean = !getAuthToken().isNullOrBlank() && getUserId() != null

    fun hasUserVoted(queryId: Int): Boolean {
        return sharedPreferences.getStringSet(KEY_VOTED_QUERY_IDS, emptySet()).orEmpty()
            .contains(queryId.toString())
    }

    fun markQueryAsVoted(queryId: Int) {
        val updatedVotes = sharedPreferences.getStringSet(KEY_VOTED_QUERY_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
        updatedVotes.add(queryId.toString())
        sharedPreferences.edit {
            putStringSet(KEY_VOTED_QUERY_IDS, updatedVotes)
        }
    }

    fun saveUserVoteType(queryId: Int, voteType: String) {
        val updatedMap = getUserVoteTypes().toMutableMap()
        updatedMap[queryId.toString()] = voteType
        sharedPreferences.edit {
            putString(KEY_VOTE_TYPES, updatedMap.entries.joinToString(separator = ",") { "${it.key}:${it.value}" })
        }
    }

    fun getUserVoteType(queryId: Int): String? = getUserVoteTypes()[queryId.toString()]

    private fun getUserVoteTypes(): Map<String, String> {
        val encoded = sharedPreferences.getString(KEY_VOTE_TYPES, null).orEmpty()
        if (encoded.isBlank()) return emptyMap()
        return encoded.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()
    }

    fun saveVideoMetadata(videoUrl: String, metadata: ClientVideoMetadata) {
        val hashedKey = hashKey(videoUrl)
        sharedPreferences.edit {
            putString("${KEY_VIDEO_METADATA_TITLE}_$hashedKey", metadata.title)
            putString("${KEY_VIDEO_METADATA_CHANNEL_NAME}_$hashedKey", metadata.channelName)
            putString("${KEY_VIDEO_METADATA_CREATOR_ID}_$hashedKey", metadata.creatorId)
            putString("${KEY_VIDEO_METADATA_THUMBNAIL_URL}_$hashedKey", metadata.thumbnailUrl)
            remove("${KEY_VIDEO_METADATA_MISS_TS}_$hashedKey")
        }
    }

    fun markVideoMetadataMiss(videoUrl: String) {
        val hashedKey = hashKey(videoUrl)
        sharedPreferences.edit {
            putLong("${KEY_VIDEO_METADATA_MISS_TS}_$hashedKey", System.currentTimeMillis())
        }
    }

    fun shouldRetryVideoMetadata(videoUrl: String, cooldownMillis: Long): Boolean {
        val hashedKey = hashKey(videoUrl)
        val lastMissAt = sharedPreferences.getLong("${KEY_VIDEO_METADATA_MISS_TS}_$hashedKey", 0L)
        return lastMissAt <= 0L || (System.currentTimeMillis() - lastMissAt) >= cooldownMillis
    }

    fun getVideoMetadata(videoUrl: String): ClientVideoMetadata? {
        val hashedKey = hashKey(videoUrl)
        val metadata = ClientVideoMetadata(
            title = sharedPreferences.getString("${KEY_VIDEO_METADATA_TITLE}_$hashedKey", null),
            channelName = sharedPreferences.getString("${KEY_VIDEO_METADATA_CHANNEL_NAME}_$hashedKey", null),
            creatorId = sharedPreferences.getString("${KEY_VIDEO_METADATA_CREATOR_ID}_$hashedKey", null),
            thumbnailUrl = sharedPreferences.getString("${KEY_VIDEO_METADATA_THUMBNAIL_URL}_$hashedKey", null),
        )
        return if (
            metadata.title.isNullOrBlank() &&
            metadata.channelName.isNullOrBlank() &&
            metadata.creatorId.isNullOrBlank() &&
            metadata.thumbnailUrl.isNullOrBlank()
        ) {
            null
        } else {
            metadata
        }
    }

    private fun hashKey(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFS_NAME = "instafact_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_IMAGE_URL = "profile_image_url"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_SYNCED_FCM_TOKEN = "synced_fcm_token"
        private const val KEY_VOTED_QUERY_IDS = "voted_query_ids"
        private const val KEY_VOTE_TYPES = "vote_types"
        private const val KEY_VIDEO_METADATA_TITLE = "video_metadata_title"
        private const val KEY_VIDEO_METADATA_CHANNEL_NAME = "video_metadata_channel_name"
        private const val KEY_VIDEO_METADATA_CREATOR_ID = "video_metadata_creator_id"
        private const val KEY_VIDEO_METADATA_THUMBNAIL_URL = "video_metadata_thumbnail_url"
        private const val KEY_VIDEO_METADATA_MISS_TS = "video_metadata_miss_ts"
    }
}
