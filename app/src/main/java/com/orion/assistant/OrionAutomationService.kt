package com.orion.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OrionAutomationService : AccessibilityService() {
    companion object {
        var instance: OrionAutomationService? = null
        var shouldAutoSendWhatsApp = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow ?: return

        if (shouldAutoSendWhatsApp && event.packageName == "com.whatsapp") {
            val sendBtns = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            if (!sendBtns.isNullOrEmpty()) {
                sendBtns[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                shouldAutoSendWhatsApp = false
            } else {
                val textBtns = root.findAccessibilityNodeInfosByText("Send")
                if (!textBtns.isNullOrEmpty()) {
                    textBtns[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    shouldAutoSendWhatsApp = false
                }
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
