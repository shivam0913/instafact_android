package com.instafact.app.data.api

import com.google.gson.Gson
import com.instafact.app.BuildConfig
import com.instafact.app.data.model.TokenRefreshRequest
import com.instafact.app.data.model.TokenRefreshResponse
import com.instafact.app.utils.PreferenceManager
import com.instafact.app.utils.SessionDebugLogger
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class TokenRefreshAuthenticator(
    private val preferenceManager: PreferenceManager,
    private val sessionExpiryHandler: SessionExpiryHandler,
) : Authenticator {

    private val gson = Gson()
    private val refreshClient = OkHttpClient()

    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            sessionExpiryHandler.handleUnauthorized()
            return null
        }

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer")
            ?.trim()
            .orEmpty()
        if (requestToken.isBlank()) {
            return null
        }

        synchronized(this) {
            val latestAccessToken = preferenceManager.getAuthToken().orEmpty()
            if (latestAccessToken.isNotBlank() && latestAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestAccessToken")
                    .build()
            }

            val refreshToken = preferenceManager.getRefreshToken().orEmpty()
            if (refreshToken.isBlank()) {
                SessionDebugLogger.logTokenRefresh("TokenRefreshAuthenticator", false, "missing_refresh_token")
                sessionExpiryHandler.handleUnauthorized()
                return null
            }

            val refreshedTokens = refreshTokens(refreshToken)
            if (refreshedTokens == null) {
                sessionExpiryHandler.handleUnauthorized()
                return null
            }

            preferenceManager.updateAuthTokens(
                authToken = refreshedTokens.token,
                refreshToken = refreshedTokens.refreshToken,
            )
            SessionDebugLogger.logTokenRefresh("TokenRefreshAuthenticator", true)
            SessionDebugLogger.logSessionSnapshot("Token refresh saved", preferenceManager)

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshedTokens.token}")
                .build()
        }
    }

    private fun refreshTokens(refreshToken: String): TokenRefreshResponse? {
        return runCatching {
            val requestBody = gson.toJson(TokenRefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}refresh-token")
                .post(requestBody)
                .build()

            refreshClient.newCall(request).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) {
                    SessionDebugLogger.logTokenRefresh(
                        "TokenRefreshAuthenticator",
                        false,
                        "http_${refreshResponse.code}",
                    )
                    return null
                }
                val responseBody = refreshResponse.body?.string().orEmpty()
                gson.fromJson(responseBody, TokenRefreshResponse::class.java)
            }
        }.getOrElse { throwable ->
            SessionDebugLogger.logTokenRefresh(
                "TokenRefreshAuthenticator",
                false,
                throwable.message,
            )
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var currentResponse: Response? = response
        var result = 1
        while (currentResponse?.priorResponse != null) {
            result++
            currentResponse = currentResponse.priorResponse
        }
        return result
    }
}
