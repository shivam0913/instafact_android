package com.instafact.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

class UnauthorizedInterceptor(
    private val sessionExpiryHandler: SessionExpiryHandler,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val hadAuthorizationHeader = !request.header("Authorization").isNullOrBlank()
        if (response.code == 401 && hadAuthorizationHeader) {
            sessionExpiryHandler.handleUnauthorized()
        }

        return response
    }
}
