package com.mhstackdevelop.dndx

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DndQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            openApp()
            return
        }

        val active = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)
        if (active) {
            val previous = prefs.getInt(
                MainActivity.KEY_PREVIOUS_FILTER,
                NotificationManager.INTERRUPTION_FILTER_ALL
            )
            try {
                notificationManager.setInterruptionFilter(previous)
            } catch (_: SecurityException) {
                openApp()
                return
            }
            prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
        } else {
            try {
                val previous = notificationManager.currentInterruptionFilter
                notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                )
                prefs.edit()
                    .putInt(MainActivity.KEY_PREVIOUS_FILTER, previous)
                    .putBoolean(MainActivity.KEY_ACTIVE, true)
                    .apply()
            } catch (_: SecurityException) {
                openApp()
                return
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val active = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_ACTIVE, false)
        tile.label = "DNDx"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "On" else "Off"
        }
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        startActivityAndCollapse(Intent(this, MainActivity::class.java))
    }
}
