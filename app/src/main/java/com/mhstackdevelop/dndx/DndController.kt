package com.mhstackdevelop.dndx

import android.app.NotificationManager
import android.content.Context

object DndController {
    enum class ToggleResult {
        ENABLED,
        DISABLED,
        POLICY_ACCESS_REQUIRED,
        ACCESS_DENIED
    }

    fun toggle(context: Context): ToggleResult {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (prefs.getBoolean(MainActivity.KEY_ACTIVE, false)) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val previous = prefs.getInt(
                    MainActivity.KEY_PREVIOUS_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                try {
                    notificationManager.setInterruptionFilter(previous)
                } catch (_: SecurityException) {
                    return ToggleResult.ACCESS_DENIED
                }
            }
            prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
            return ToggleResult.DISABLED
        }

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return ToggleResult.POLICY_ACCESS_REQUIRED
        }

        return try {
            val previous = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            prefs.edit()
                .putInt(MainActivity.KEY_PREVIOUS_FILTER, previous)
                .putBoolean(MainActivity.KEY_ACTIVE, true)
                .apply()
            ToggleResult.ENABLED
        } catch (_: SecurityException) {
            ToggleResult.ACCESS_DENIED
        }
    }
}
