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
                append("fcm_token=").append(preferenceManager.getFcmToken())
            },
        )
    }

    fun logFcmToken(source: String, token: String) {
        Log.d(TAG, "[$source] fcm_token=$token")
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
}
