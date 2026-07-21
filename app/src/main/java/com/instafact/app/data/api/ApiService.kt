package com.instafact.app.data.api

import com.instafact.app.data.model.ChatHistoryResponse
import com.instafact.app.data.model.ChatMessageRequest
import com.instafact.app.data.model.ChatMessageResponse
import com.instafact.app.data.model.DetailResponse
import com.instafact.app.data.model.ExploreItemResponse
import com.instafact.app.data.model.FeedbackRequest
import com.instafact.app.data.model.FeedbackResponse
import com.instafact.app.data.model.HistoryItemResponse
import com.instafact.app.data.model.OTPRequestResponse
import com.instafact.app.data.model.OTPResendRequest
import com.instafact.app.data.model.OTPVerifyRequest
import com.instafact.app.data.model.ProfileImageUploadUrlRequest
import com.instafact.app.data.model.ProfileImageUploadUrlResponse
import com.instafact.app.data.model.SubmitRequest
import com.instafact.app.data.model.SubmitResponse
import com.instafact.app.data.model.TokenRefreshRequest
import com.instafact.app.data.model.TokenRefreshResponse
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.model.UserProfileUpdateRequest
import com.instafact.app.data.model.UserRegisterRequest
import com.instafact.app.data.model.UserRegisterResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface ApiService {

    @POST("register")
    suspend fun register(
        @Body request: UserRegisterRequest,
    ): OTPRequestResponse

    @POST("resend-otp")
    suspend fun resendOtp(
        @Body request: OTPResendRequest,
    ): OTPRequestResponse

    @POST("verify-otp")
    suspend fun verifyOtp(
        @Body request: OTPVerifyRequest,
    ): UserRegisterResponse

    @POST("refresh-token")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest,
    ): TokenRefreshResponse

    @GET("profile")
    suspend fun getProfile(): UserProfileResponse

    @PUT("profile")
    suspend fun updateProfile(
        @Body request: UserProfileUpdateRequest,
    ): UserProfileResponse

    @POST("profile/image-upload-url")
    suspend fun createProfileImageUploadUrl(
        @Body request: ProfileImageUploadUrlRequest,
    ): ProfileImageUploadUrlResponse

    @POST("submit")
    suspend fun submitVideo(
        @Body request: SubmitRequest,
    ): SubmitResponse

    @POST("feedback")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest,
    ): FeedbackResponse

    @GET("history")
    suspend fun getHistory(
        @Query("user_id") userId: Int,
    ): List<HistoryItemResponse>

    @GET("detail/{id}")
    suspend fun getDetail(
        @Path("id") queryId: Int,
    ): DetailResponse

    @GET("explore")
    suspend fun getExplore(
        @Query("limit") limit: Int = 20,
    ): List<ExploreItemResponse>

    @GET("chat/history")
    suspend fun getChatHistory(
        @Query("user_id") userId: Int,
        @Query("query_id") queryId: Int,
    ): ChatHistoryResponse

    @POST("chat/message")
    suspend fun sendChatMessage(
        @Body request: ChatMessageRequest,
    ): ChatMessageResponse
}
