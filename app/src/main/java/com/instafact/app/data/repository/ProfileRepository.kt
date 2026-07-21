package com.instafact.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.instafact.app.data.api.ApiService
import com.instafact.app.data.model.ProfileImageUploadUrlRequest
import com.instafact.app.data.model.UserProfileUpdateRequest
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.utils.ApiErrorParser
import com.instafact.app.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager,
) {

    private val uploadClient = OkHttpClient()

    suspend fun getProfile(): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getProfile()
        }.fold(
            onSuccess = {
                cacheProfile(it)
                Result.success(it)
            },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun updateProfile(request: UserProfileUpdateRequest): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.updateProfile(request)
        }.fold(
            onSuccess = {
                cacheProfile(it)
                Result.success(it)
            },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    suspend fun uploadProfileImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val contentType = contentResolver.getType(imageUri).orEmpty().ifBlank { "image/jpeg" }
            val fileName = contentResolver.queryDisplayName(imageUri)
            val uploadConfig = apiService.createProfileImageUploadUrl(
                ProfileImageUploadUrlRequest(
                    contentType = contentType,
                    fileName = fileName,
                ),
            )
            val imageBytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected image.")

            val request = Request.Builder()
                .url(uploadConfig.uploadUrl)
                .put(imageBytes.toRequestBody(contentType.toMediaTypeOrNull()))
                .addHeader("Content-Type", contentType)
                .build()

            uploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Profile image upload failed with code ${response.code}.")
                }
            }

            uploadConfig.publicUrl
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(ApiErrorParser.getMessage(context, it), it)) },
        )
    }

    fun getPhoneNumber(): String? = preferenceManager.getPhoneNumber()

    fun getProfileName(): String? = preferenceManager.getProfileName()

    fun getProfileImageUrl(): String? = preferenceManager.getProfileImageUrl()

    fun logout() {
        preferenceManager.clearUserSession()
    }

    private fun cacheProfile(profile: UserProfileResponse) {
        preferenceManager.saveProfileName(profile.name)
        preferenceManager.saveProfileImageUrl(profile.profileImageUrl)
    }

    private fun ContentResolver.queryDisplayName(uri: Uri): String? {
        return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}
