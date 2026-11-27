package com.mhstackdevelop.dndx

import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
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
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            openApp()
            return
        }

        when (DndController.toggle(this)) {
            DndController.ToggleResult.ENABLED,
            DndController.ToggleResult.DISABLED -> Unit
            DndController.ToggleResult.POLICY_ACCESS_REQUIRED,
            DndController.ToggleResult.ACCESS_DENIED -> {
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

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
