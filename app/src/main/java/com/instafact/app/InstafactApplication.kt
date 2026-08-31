package com.instafact.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.AppContainer
import com.instafact.app.utils.NotificationHelper
import com.instafact.app.utils.SessionDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstafactApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        NotificationHelper.createNotificationChannel(this)
        SessionDebugLogger.logSessionSnapshot("Application.onCreate", appContainer.preferenceManager)
        initializeFirebase()
    }

    private fun initializeFirebase() {
        val firebaseApp = runCatching {
            FirebaseApp.initializeApp(this) ?: FirebaseApp.getApps(this).firstOrNull()
        }.getOrNull()

        if (firebaseApp == null) {
            Log.w(TAG, "Firebase is not configured. Add google-services.json to enable FCM.")
            return
        }

        // Analytics shares the FirebaseApp, so it can only start once that exists.
        Analytics.initialize(this)
        // Restores the id on every cold start, so a returning user's sessions stay stitched.
        Analytics.setUserId(appContainer.preferenceManager.getUserId())

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                appContainer.preferenceManager.saveFcmToken(token)
                SessionDebugLogger.logFcmToken("FirebaseMessaging.getToken", token)
                SessionDebugLogger.logSessionSnapshot("FCM token saved", appContainer.preferenceManager)

                // The token often lands after sign-in, so verify-otp may have sent nothing.
                // Registering it here is what keeps the server able to reach this device.
                CoroutineScope(Dispatchers.IO).launch {
                    appContainer.authRepository.ensureFcmTokenRegistered()
                        .onFailure { Log.w(TAG, "Could not register FCM token with the backend.", it) }
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Unable to fetch FCM token.", throwable)
            }
    }

    companion object {
        private const val TAG = "InstafactApplication"
    }
}
