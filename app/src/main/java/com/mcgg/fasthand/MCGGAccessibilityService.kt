package com.mcgg.fasthand

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/**
 * Observes foreground package changes only. It does not inspect game internals or click anything.
 * Android requires the user to enable this service explicitly in Accessibility settings.
 */
class MCGGAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_GAME_FOREGROUND = "com.mcgg.fasthand.GAME_FOREGROUND"
        const val EXTRA_IS_GAME = "is_game"
        const val GAME_PACKAGE = "com.mobilechess.gp"

        @Volatile
        var isGameForeground: Boolean = false
            private set
    }

    private fun publishForegroundState(isGame: Boolean) {
        isGameForeground = isGame
        sendBroadcast(Intent(ACTION_GAME_FOREGROUND)
            .setPackage(packageName)
            .putExtra(EXTRA_IS_GAME, isGame))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        publishForegroundState(packageName == GAME_PACKAGE)
    }

    override fun onInterrupt() {
        publishForegroundState(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        publishForegroundState(false)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        publishForegroundState(false)
        return super.onUnbind(intent)
    }
}
