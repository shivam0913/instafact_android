package com.instafact.app.utils

import android.util.Log
import com.instafact.app.data.model.OTPRequestResponse

object SessionDebugLogger {

    private const val TAG = "InstafactDebug"

    fun logSessionSnapshot(source: String, preferenceManager: PreferenceManager) {
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("user_id=").append(preferenceManager.getUserId()).append(", ")
                append("phone_number=").append(preferenceManager.getPhoneNumber()).append(", ")
                append("auth_token=").append(preferenceManager.getAuthToken()).append(", ")
                append("refresh_token=").append(preferenceManager.getRefreshToken()).append(", ")
                append("fcm_token=").append(preferenceManager.getFcmToken())
            },
        )
    }

    fun logFcmToken(source: String, token: String) {
        Log.d(TAG, "[$source] fcm_token=$token")
    }

    fun logTokenRefresh(source: String, success: Boolean, detail: String? = null) {
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("token_refresh=").append(if (success) "success" else "failure")
                if (!detail.isNullOrBlank()) {
                    append(", detail=").append(detail)
                }
            },
        )
    }

    fun logOtpRequest(source: String, phoneNumber: String, response: OTPRequestResponse) {
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("phone_number=").append(phoneNumber).append(", ")
                append("message=").append(response.message).append(", ")
                append("verification_id=").append(response.verificationId).append(", ")
                append("expires_in_seconds=").append(response.expiresInSeconds).append(", ")
                append("debug_otp=").append(response.debugOtp)
            },
        )
    }

    fun logMetadataFetchStart(source: String, videoUrl: String) {
        Log.d(TAG, "[$source] metadata_fetch=start, video_url=$videoUrl")
    }

    fun logMetadataFetchResult(source: String, videoUrl: String, metadata: ClientVideoMetadata?) {
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("metadata_fetch=").append(if (metadata == null) "empty" else "success").append(", ")
                append("video_url=").append(videoUrl).append(", ")
                append("title=").append(metadata?.title).append(", ")
                append("channel_name=").append(metadata?.channelName).append(", ")
                append("creator_id=").append(metadata?.creatorId).append(", ")
                append("thumbnail_url=").append(metadata?.thumbnailUrl)
            },
        )
    }

    fun logMetadataFetchFailure(source: String, videoUrl: String, throwable: Throwable) {
        Log.w(TAG, "[$source] metadata_fetch=failure, video_url=$videoUrl", throwable)
    }

    fun logMetadataFetchSkipped(source: String, videoUrl: String, reason: String) {
        Log.d(TAG, "[$source] metadata_fetch=skipped, video_url=$videoUrl, reason=$reason")
    }

    fun logMetadataAttempt(
        source: String,
        requestUrl: String,
        finalUrl: String?,
        statusCode: Int,
        bodySnippet: String?,
    ) {
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("metadata_attempt_status=").append(statusCode).append(", ")
                append("request_url=").append(requestUrl).append(", ")
                append("final_url=").append(finalUrl).append(", ")
                append("body_snippet=").append(bodySnippet)
            },
        )
    }
}
