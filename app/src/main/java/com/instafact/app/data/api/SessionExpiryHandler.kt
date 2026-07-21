package com.instafact.app.data.api

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.instafact.app.ui.login.LoginActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.PreferenceManager
import java.util.concurrent.atomic.AtomicBoolean

class SessionExpiryHandler(
    private val appContext: Context,
    private val preferenceManager: PreferenceManager,
) {

    fun handleUnauthorized() {
        if (!preferenceManager.isLoggedIn()) return
        if (!redirectInProgress.compareAndSet(false, true)) return

        preferenceManager.clearUserSession()

        Handler(Looper.getMainLooper()).post {
            val intent = Intent(appContext, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(IntentExtras.EXTRA_SESSION_EXPIRED, true)
            }
            appContext.startActivity(intent)
        }
    }

    companion object {
        private val redirectInProgress = AtomicBoolean(false)

        fun resetRedirectState() {
            redirectInProgress.set(false)
        }
    }
}
