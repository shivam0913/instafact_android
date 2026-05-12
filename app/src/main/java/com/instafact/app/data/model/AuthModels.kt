package com.instafact.app.data.model

data class UserRegisterRequest(
    val phoneNumber: String,
    val fcmToken: String? = null,
)

data class OTPResendRequest(
    val phoneNumber: String,
)

data class OTPRequestResponse(
    val message: String,
    val expiresInSeconds: Int,
    val verificationId: String?,
    val debugOtp: String?,
)

data class OTPVerifyRequest(
    val phoneNumber: String,
    val otp: String,
    val fcmToken: String? = null,
)

data class UserRegisterResponse(
    val userId: Int,
    val token: String,
)
