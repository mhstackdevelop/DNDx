package com.mhstackdevelop.dndx

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.role.RoleManager
import android.os.Build

class DndBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MainActivity.KEY_ACTIVE, false)) return
        if (!prefs.getBoolean(MainActivity.KEY_RESTORE_ON_BOOT, true)) return

        val notificationManager =
            context.getSystemService(NotificationManager::class.java) ?: return
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val roleManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)
            } else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (roleManager == null || !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
        ) return

        notificationManager.setInterruptionFilter(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }
}
