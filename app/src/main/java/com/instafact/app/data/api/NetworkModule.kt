package com.instafact.app.data.api

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.instafact.app.BuildConfig
import com.instafact.app.utils.PreferenceManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * The single Gson the whole app must serialize with.
     *
     * None of the models in data.model carry @SerializedName - every wire name is derived
     * from the Kotlin property name by this naming policy. A bare Gson() therefore does
     * not produce the same JSON, so anything talking to the API has to use this instance
     * rather than building its own.
     */
    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    fun createApiService(
        context: Context,
        preferenceManager: PreferenceManager,
    ): ApiService {
        val sessionExpiryHandler = SessionExpiryHandler(
            appContext = context.applicationContext,
            preferenceManager = preferenceManager,
        )
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .authenticator(TokenRefreshAuthenticator(preferenceManager, sessionExpiryHandler, gson))
            .addInterceptor(AuthInterceptor(preferenceManager))
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
