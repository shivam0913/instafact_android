package com.instafact.app.data.repository

import android.content.Context
import com.instafact.app.data.api.ApiService
import com.instafact.app.data.model.FcmTokenUpdateRequest
import com.instafact.app.data.model.OTPRequestResponse
import com.instafact.app.data.model.OTPResendRequest
import com.instafact.app.data.model.OTPVerifyRequest
import com.instafact.app.data.model.UserRegisterRequest
import com.instafact.app.data.model.UserRegisterResponse
import com.instafact.app.utils.ApiErrorParser
import com.instafact.app.utils.PreferenceManager
import com.instafact.app.utils.SessionDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager,
) {

    suspend fun requestOtp(
        phoneNumber: String,
        countryCode: String,
    ): Result<OTPRequestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.register(
                UserRegisterRequest(
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                ),
            )
        }.fold(
            onSuccess = {
                SessionDebugLogger.logOtpRequest("POST /register", phoneNumber, it)
                Result.success(it)
            },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun resendOtp(
        phoneNumber: String,
        countryCode: String,
    ): Result<OTPRequestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.resendOtp(
                OTPResendRequest(
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                ),
            )
        }.fold(
            onSuccess = {
                SessionDebugLogger.logOtpRequest("POST /resend-otp", phoneNumber, it)
                Result.success(it)
            },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun verifyOtp(
        phoneNumber: String,
        countryCode: String,
        otp: String,
    ): Result<UserRegisterResponse> = withContext(Dispatchers.IO) {
        val fcmToken = preferenceManager.getFcmToken()
        runCatching {
            val response = apiService.verifyOtp(
                OTPVerifyRequest(
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                    otp = otp,
                    fcmToken = fcmToken,
                ),
            )
            preferenceManager.saveSession(
                userId = response.userId,
                authToken = response.token,
                refreshToken = response.refreshToken,
                phoneNumber = phoneNumber,
            )
            // This request already carried the token, so it does not need re-registering.
            if (!fcmToken.isNullOrBlank()) preferenceManager.saveSyncedFcmToken(fcmToken)
            SessionDebugLogger.logSessionSnapshot("POST /verify-otp", preferenceManager)
            response
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    /**
     * Store the push token locally and register it with the backend.
     *
     * verify-otp carries whatever token existed at sign-in, but Firebase may not have
     * issued one yet at that point and rotates it later. Without this call the server
     * keeps a null or stale token and every push is dropped before it reaches the device.
     */
    suspend fun syncFcmToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        preferenceManager.saveFcmToken(token)
        SessionDebugLogger.logFcmToken("syncFcmToken", token)
        SessionDebugLogger.logSessionSnapshot("syncFcmToken", preferenceManager)

        if (token.isBlank() || !preferenceManager.isLoggedIn()) {
            // Signed out: nothing to attach the token to. verify-otp will carry it instead.
            return@withContext Result.success(Unit)
        }

        runCatching {
            apiService.updateFcmToken(FcmTokenUpdateRequest(fcmToken = token))
        }.fold(
            onSuccess = {
                preferenceManager.saveSyncedFcmToken(token)
                Result.success(Unit)
            },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    /** Registers the current token when it has not reached the backend yet. */
    suspend fun ensureFcmTokenRegistered(): Result<Unit> {
        val token = preferenceManager.getFcmToken().orEmpty()
        if (token.isBlank() || !preferenceManager.isLoggedIn()) return Result.success(Unit)
        if (token == preferenceManager.getSyncedFcmToken()) return Result.success(Unit)
        return syncFcmToken(token)
    }
}
