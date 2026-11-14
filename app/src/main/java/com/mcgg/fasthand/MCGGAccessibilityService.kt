package com.mcgg.fasthand

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/**
 * Observes the foreground package only. It does not inspect game internals or click anything.
 * Android requires the user to enable this service explicitly in Accessibility settings.
 */
class MCGGAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_GAME_FOREGROUND = "com.mcgg.fasthand.GAME_FOREGROUND"
        const val EXTRA_IS_GAME = "is_game"
        const val GAME_PACKAGE = "com.mobilechess.gp"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val isGame = packageName == GAME_PACKAGE
        sendBroadcast(Intent(ACTION_GAME_FOREGROUND)
            .setPackage(this.packageName)
            .putExtra(EXTRA_IS_GAME, isGame))
    }

    override fun onInterrupt() {
        sendBroadcast(Intent(ACTION_GAME_FOREGROUND).setPackage(this.packageName)
            .putExtra(EXTRA_IS_GAME, false))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sendBroadcast(Intent(ACTION_GAME_FOREGROUND).setPackage(this.packageName)
            .putExtra(EXTRA_IS_GAME, false))
    }
}
