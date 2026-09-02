package com.instafact.app.utils

import android.util.Log
import com.instafact.app.BuildConfig
import com.instafact.app.data.model.OTPRequestResponse

/**
 * Verbose session tracing for local debugging.
 *
 * These lines carry auth tokens, refresh tokens, phone numbers and FCM tokens - anything
 * that reads logcat (a bug report capture, a diagnostics tool, an attached cable) would
 * otherwise pick up a working bearer token. Every entry point is therefore gated on
 * [enabled], so a release build produces nothing regardless of who adds a new call site.
 *
 * The release ProGuard rules also strip Log.d/Log.v as a second layer; neither is meant
 * to be the only thing standing between a token and a log file.
 */
object SessionDebugLogger {

    private const val TAG = "InstafactDebug"

    private val enabled = BuildConfig.DEBUG

    fun logSessionSnapshot(source: String, preferenceManager: PreferenceManager) {
        if (!enabled) return
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
        if (!enabled) return
        Log.d(TAG, "[$source] fcm_token=$token")
    }

    fun logTokenRefresh(source: String, success: Boolean, detail: String? = null) {
        if (!enabled) return
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
        if (!enabled) return
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
        if (!enabled) return
        Log.d(TAG, "[$source] metadata_fetch=start, video_url=$videoUrl")
    }

    fun logMetadataFetchResult(source: String, videoUrl: String, metadata: ClientVideoMetadata?) {
        if (!enabled) return
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
        if (!enabled) return
        Log.w(TAG, "[$source] metadata_fetch=failure, video_url=$videoUrl", throwable)
    }

    fun logMetadataFetchSkipped(source: String, videoUrl: String, reason: String) {
        if (!enabled) return
        Log.d(TAG, "[$source] metadata_fetch=skipped, video_url=$videoUrl, reason=$reason")
    }

    fun logMetadataAttempt(
        source: String,
        requestUrl: String,
        finalUrl: String?,
        statusCode: Int,
        bodySnippet: String?,
    ) {
        if (!enabled) return
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

    fun logProfileImageLoad(source: String, imageUrl: String?, status: String, detail: String? = null) {
        if (!enabled) return
        Log.d(
            TAG,
            buildString {
                append("[").append(source).append("] ")
                append("profile_image_load=").append(status).append(", ")
                append("image_url=").append(imageUrl)
                if (!detail.isNullOrBlank()) {
                    append(", detail=").append(detail)
                }
            },
        )
    }
}
