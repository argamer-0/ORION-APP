package com.orion.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {
    companion object {
        var onNewNotification: ((String, String) -> Unit)? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val pkg = sbn.packageName ?: ""

        if (pkg.contains("whatsapp") && title.isNotEmpty()) {
            onNewNotification?.invoke(title, text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
