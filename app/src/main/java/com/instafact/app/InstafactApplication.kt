package com.instafact.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.instafact.app.utils.AppContainer
import com.instafact.app.utils.NotificationHelper
import com.instafact.app.utils.SessionDebugLogger

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

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                appContainer.preferenceManager.saveFcmToken(token)
                SessionDebugLogger.logFcmToken("FirebaseMessaging.getToken", token)
                SessionDebugLogger.logSessionSnapshot("FCM token saved", appContainer.preferenceManager)
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Unable to fetch FCM token.", throwable)
            }
    }

    companion object {
        private const val TAG = "InstafactApplication"
    }
}
