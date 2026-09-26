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
        val rootNode = rootInActiveWindow ?: return

        if (shouldAutoSendWhatsApp && event.packageName == "com.whatsapp") {
            val sendNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            if (!sendNodes.isNullOrEmpty()) {
                sendNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                shouldAutoSendWhatsApp = false
            } else {
                val sendDesc = rootNode.findAccessibilityNodeInfosByText("Send")
                if (!sendDesc.isNullOrEmpty()) {
                    sendDesc[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
