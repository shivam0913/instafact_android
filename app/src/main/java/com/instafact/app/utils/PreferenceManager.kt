package com.instafact.app.utils

import android.content.Context
import androidx.core.content.edit

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        userId: Int,
        authToken: String,
        phoneNumber: String,
    ) {
        sharedPreferences.edit {
            putInt(KEY_USER_ID, userId)
            putString(KEY_AUTH_TOKEN, authToken)
            putString(KEY_PHONE_NUMBER, phoneNumber)
        }
    }

    fun saveFcmToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_FCM_TOKEN, token)
        }
    }

    fun getUserId(): Int? {
        if (!sharedPreferences.contains(KEY_USER_ID)) return null
        return sharedPreferences.getInt(KEY_USER_ID, -1).takeIf { it > 0 }
    }

    fun getAuthToken(): String? = sharedPreferences.getString(KEY_AUTH_TOKEN, null)

    fun getPhoneNumber(): String? = sharedPreferences.getString(KEY_PHONE_NUMBER, null)

    fun getFcmToken(): String? = sharedPreferences.getString(KEY_FCM_TOKEN, null)

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

    companion object {
        private const val PREFS_NAME = "instafact_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_VOTED_QUERY_IDS = "voted_query_ids"
    }
}
