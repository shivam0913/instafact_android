package com.instafact.app.data.api

import com.instafact.app.utils.PreferenceManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferenceManager: PreferenceManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = preferenceManager.getAuthToken()
        val request = chain.request()
            .newBuilder()
            .apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(request)
    }
}
