package com.orion.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.util.Log

class OrionNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (packageName.contains("whatsapp") || packageName.contains("instagram")) {
            Log.d("ORION", "Message from $sender: $message")
        }
    }
}
