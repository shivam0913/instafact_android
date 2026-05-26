package com.instafact.app.utils

import android.content.Context
import com.instafact.app.data.api.NetworkModule
import com.instafact.app.data.repository.AuthRepository
import com.instafact.app.data.repository.ProfileRepository
import com.instafact.app.data.repository.SubmissionRepository

class AppContainer(
    context: Context,
) {

    private val appContext = context.applicationContext

    val preferenceManager: PreferenceManager = PreferenceManager(appContext)

    private val apiService = NetworkModule.createApiService(
        context = appContext,
        preferenceManager = preferenceManager,
    )

    val authRepository: AuthRepository = AuthRepository(
        context = appContext,
        apiService = apiService,
        preferenceManager = preferenceManager,
    )

    val submissionRepository: SubmissionRepository = SubmissionRepository(
        context = appContext,
        apiService = apiService,
        preferenceManager = preferenceManager,
    )

    val profileRepository: ProfileRepository = ProfileRepository(
        context = appContext,
        apiService = apiService,
        preferenceManager = preferenceManager,
    )
}
