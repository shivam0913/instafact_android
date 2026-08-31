package com.instafact.app.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.NotificationHelper
import com.instafact.app.utils.NotificationStore
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
        val data = remoteMessage.data
        val queryId = data[IntentExtras.KEY_QUERY_ID]?.toIntOrNull()

        // The backend sends data-only messages so this runs in the background too; the
        // notification block is only a fallback for older payloads.
        val title = data[IntentExtras.KEY_TITLE]
            ?: remoteMessage.notification?.title
            ?: getString(R.string.notification_title)
        val body = data[IntentExtras.KEY_BODY]
            ?: remoteMessage.notification?.body
            ?: getString(R.string.notification_body)

        // Record it first so the in-app list stays correct even if posting the tray
        // notification is blocked (permission denied, channel muted).
        NotificationStore(this).add(title = title, body = body, queryId = queryId)
        // Paired with push_opened, this gives the delivered -> opened rate.
        Analytics.logPushReceived(queryId)

        if (queryId == null) {
            Log.w(TAG, "Push received without a query_id payload; stored without a deep link.")
            return
        }

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
