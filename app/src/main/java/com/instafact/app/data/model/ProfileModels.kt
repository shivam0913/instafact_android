package com.instafact.app.data.model

data class UserProfileResponse(
    val userId: Int,
    val phoneNumber: String,
    val name: String? = null,
    val gender: String? = null,
    val ageGroup: String? = null,
    val fcmToken: String? = null,
    val profileImageUrl: String? = null,
    val factCheckedContentCount: Int = 0,
    val memberSince: String,
    val referralCode: String,
)

data class UserProfileUpdateRequest(
    val name: String? = null,
    val gender: String? = null,
    val ageGroup: String? = null,
    val profileImageUrl: String? = null,
)

data class ProfileImageUploadUrlRequest(
    val contentType: String = "image/jpeg",
    val fileName: String? = null,
)

data class ProfileImageUploadUrlResponse(
    val uploadUrl: String,
    val publicUrl: String,
    val objectKey: String,
    val method: String,
    val expiresInSeconds: Int,
)
