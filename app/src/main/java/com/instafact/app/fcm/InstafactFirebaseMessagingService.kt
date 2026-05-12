package com.instafact.app.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstafactFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val appContainer = (application as InstafactApplication).appContainer
        appContainer.preferenceManager.saveFcmToken(token)

        CoroutineScope(Dispatchers.IO).launch {
            appContainer.authRepository.syncFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val queryId = remoteMessage.data[IntentExtras.KEY_QUERY_ID]?.toIntOrNull()
        if (queryId == null) {
            Log.w(TAG, "Notification received without a query_id payload.")
            return
        }

        val title = remoteMessage.notification?.title ?: getString(R.string.notification_title)
        val body = remoteMessage.notification?.body ?: getString(R.string.notification_body)
        NotificationHelper.showResultNotification(
            context = this,
            queryId = queryId,
            title = title,
            body = body,
        )
    }

    companion object {
        private const val TAG = "InstafactFcmService"
    }
}
