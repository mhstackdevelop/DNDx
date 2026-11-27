package com.mhstackdevelop.dndx

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class OverviewFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_overview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.button_toggle).setOnClickListener { toggleDnd() }
        view.findViewById<MaterialButton>(R.id.button_call_role).setOnClickListener { requestCallRole() }
        view.findViewById<MaterialButton>(R.id.button_policy).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        view.findViewById<MaterialButton>(R.id.button_notifications).setOnClickListener {
            requestNotifications()
        }
        view.findViewById<MaterialButton>(R.id.button_battery).setOnClickListener {
            openBatterySettings()
        }
        refresh(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View) {
        val context = requireContext()
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val active = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)
        val roleGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val policyGranted = notificationManager.isNotificationPolicyAccessGranted
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        view.findViewById<TextView>(R.id.status_text).text =
            if (active) "DNDx is ON" else "DNDx is OFF"
        view.findViewById<MaterialButton>(R.id.button_toggle).text =
            if (active) "Turn off DND" else "Enable DND"

        view.findViewById<MaterialButton>(R.id.button_call_role).text =
            if (roleGranted) "Call role: Granted" else "Grant call-screening role"
        view.findViewById<MaterialButton>(R.id.button_policy).text =
            if (policyGranted) "DND access: Granted" else "Grant DND policy access"
        view.findViewById<MaterialButton>(R.id.button_notifications).text =
            if (notificationsGranted) "Notifications: Granted" else "Enable notifications"
    }

    private fun toggleDnd() {
        val context = requireContext()
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (prefs.getBoolean(MainActivity.KEY_ACTIVE, false)) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val previous = prefs.getInt(
                    MainActivity.KEY_PREVIOUS_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                notificationManager.setInterruptionFilter(previous)
            }
            prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !context.getSystemService(RoleManager::class.java)
                    .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                requestCallRole()
                toast("Grant the call-screening role, then try again.")
                return
            }
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                toast("Grant DND policy access, then try again.")
                return
            }
            prefs.edit()
                .putInt(
                    MainActivity.KEY_PREVIOUS_FILTER,
                    notificationManager.currentInterruptionFilter
                )
                .putBoolean(MainActivity.KEY_ACTIVE, true)
                .apply()
            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        }
        view?.let { refresh(it) }
    }

    private fun requestCallRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = requireContext().getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } else {
            toast("The call-screening role is unavailable on this device.")
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
            return
        }
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            toast(if (granted) "Notifications enabled." else "Notification permission denied.")
            view?.let { refresh(it) }
        }
    }

    private fun openBatterySettings() {
        val context = requireContext()
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (direct.resolveActivity(context.packageManager) != null) {
            startActivity(direct)
        } else {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 201
    }
}
