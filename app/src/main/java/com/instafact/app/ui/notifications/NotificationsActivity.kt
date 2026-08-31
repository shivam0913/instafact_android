package com.instafact.app.ui.notifications

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.R
import com.instafact.app.databinding.ActivityNotificationsBinding
import com.instafact.app.ui.detail.DetailActivity
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.NotificationRecord
import com.instafact.app.utils.NotificationStore
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var notificationStore: NotificationStore

    private val adapter by lazy { NotificationAdapter(::onNotificationClicked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Analytics.logScreenView("notifications", "NotificationsActivity")
        configureSystemBars(
            statusBarColorRes = R.color.brand_background,
            navigationBarColorRes = R.color.brand_background,
            lightStatusBar = true,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

        notificationStore = NotificationStore(this)

        binding.backButton.setOnClickListener { finish() }
        binding.markAllReadTextView.setOnClickListener {
            notificationStore.markAllRead()
            render()
        }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val records = notificationStore.getAll()
        val isEmpty = records.isEmpty()

        binding.notificationsRecyclerView.isVisible = !isEmpty
        binding.emptyStateContainer.isVisible = isEmpty
        binding.notificationsSubtitleTextView.isVisible = !isEmpty
        binding.markAllReadTextView.isVisible = records.any { !it.isRead }
        adapter.submitList(records)
    }

    private fun onNotificationClicked(record: NotificationRecord) {
        notificationStore.markRead(record.id)
        val queryId = record.queryId
        if (queryId != null) {
            startActivity(
                Intent(this, DetailActivity::class.java).apply {
                    putExtra(IntentExtras.EXTRA_QUERY_ID, queryId)
                },
            )
        } else {
            render()
        }
    }
}
