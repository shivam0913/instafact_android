package com.instafact.app.data.repository

import android.content.Context
import com.instafact.app.data.api.ApiService
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

    suspend fun requestOtp(phoneNumber: String): Result<OTPRequestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.register(
                UserRegisterRequest(
                    phoneNumber = phoneNumber,
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

    suspend fun resendOtp(phoneNumber: String): Result<OTPRequestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.resendOtp(
                OTPResendRequest(
                    phoneNumber = phoneNumber,
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
        otp: String,
    ): Result<UserRegisterResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.verifyOtp(
                OTPVerifyRequest(
                    phoneNumber = phoneNumber,
                    otp = otp,
                    fcmToken = preferenceManager.getFcmToken(),
                ),
            )
            preferenceManager.saveSession(
                userId = response.userId,
                authToken = response.token,
                phoneNumber = phoneNumber,
            )
            SessionDebugLogger.logSessionSnapshot("POST /verify-otp", preferenceManager)
            response
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun syncFcmToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        preferenceManager.saveFcmToken(token)
        SessionDebugLogger.logFcmToken("syncFcmToken", token)
        SessionDebugLogger.logSessionSnapshot("syncFcmToken", preferenceManager)
        Result.success(Unit)
    }
}
