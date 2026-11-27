package com.mhstackdevelop.dndx

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku

class OverviewFragment : Fragment() {
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    grantPolicyAccessWithShizuku()
                } else {
                    toast("Shizuku permission was denied.")
                }
            }
        }
    private var shizukuServiceArgs: Shizuku.UserServiceArgs? = null
    private var shizukuServiceConnection: ServiceConnection? = null

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
        view.findViewById<MaterialButton>(R.id.button_shizuku).setOnClickListener {
            confirmShizukuGrant()
        }
        view.findViewById<MaterialButton>(R.id.button_notifications).setOnClickListener {
            requestNotifications()
        }
        view.findViewById<MaterialButton>(R.id.button_battery).setOnClickListener {
            openBatterySettings()
        }
        refresh(view)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onDestroyView() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        val args = shizukuServiceArgs
        val connection = shizukuServiceConnection
        if (args != null && connection != null) {
            Shizuku.unbindUserService(args, connection, true)
            shizukuServiceArgs = null
            shizukuServiceConnection = null
        }
        super.onDestroyView()
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
        val shizukuRunning = Shizuku.pingBinder()
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        view.findViewById<TextView>(R.id.status_text).text =
            if (active) "DNDx is ON" else "DNDx is OFF"
        view.findViewById<TextView>(R.id.status_detail).text = when {
            active && policyGranted -> "Priority mode is enabled. Your previous DND setting will be restored when turned off."
            active -> "DNDx is marked on, but Android policy access is missing. Grant access to control DND."
            policyGranted -> "DND policy access is ready. Enable DND when you want some quiet."
            else -> "Grant DND policy access to let DNDx control Priority mode."
        }
        view.findViewById<MaterialButton>(R.id.button_toggle).text =
            if (active) "Turn off DND" else "Enable DND"

        view.findViewById<MaterialButton>(R.id.button_call_role).text =
            if (roleGranted) "Call role: Granted" else "Grant call-screening role"
        view.findViewById<MaterialButton>(R.id.button_policy).text =
            if (policyGranted) "DND access: Granted" else "Grant DND policy access"
        view.findViewById<MaterialButton>(R.id.button_shizuku).apply {
            text = when {
                policyGranted -> "DND access granted"
                shizukuRunning -> "Grant DND access with Shizuku"
                else -> "Shizuku not running"
            }
            isEnabled = !policyGranted
        }
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
                try {
                    notificationManager.setInterruptionFilter(previous)
                } catch (_: SecurityException) {
                    toast("Android denied access to change DND settings.")
                    return
                }
            }
            prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
        } else {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                toast("Grant DND policy access, then try again.")
                return
            }
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
                toast("Android denied access to change DND settings.")
                return
            }
        }
        view?.let { refresh(it) }
    }

    private fun confirmShizukuGrant() {
        if (!Shizuku.pingBinder()) {
            toast("Start Shizuku, then return here to grant DND access.")
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Grant DND access with Shizuku?")
            .setMessage(
                "DNDx will ask Shizuku to run Android's notification permission command " +
                    "for this app. Shizuku must be running and you must approve its request."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                when {
                    !Shizuku.pingBinder() ->
                        toast("Shizuku is no longer available. Start it and try again.")
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                        grantPolicyAccessWithShizuku()
                    Shizuku.shouldShowRequestPermissionRationale() ->
                        toast("Allow DNDx in the Shizuku app, then try again.")
                    else -> Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
                }
            }
            .show()
    }

    private fun grantPolicyAccessWithShizuku() {
        val args = Shizuku.UserServiceArgs(
            ComponentName(requireContext(), ShizukuPolicyService::class.java)
        ).tag("dndx-policy-access").version(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    toast("Shizuku connected without a policy service.")
                    Shizuku.unbindUserService(args, this, true)
                    shizukuServiceArgs = null
                    shizukuServiceConnection = null
                    return
                }
                val service = IShizukuDndService.Stub.asInterface(binder)
                val currentConnection = this
                Thread {
                    val result = try {
                        service.grantDndPolicyAccess()
                    } catch (exception: RemoteException) {
                        "Shizuku service failed: ${exception.message ?: "remote error"}"
                    }
                    activity?.runOnUiThread {
                        val granted = result == "OK"
                        toast(
                            if (granted) "DND policy access granted with Shizuku."
                            else "Shizuku could not grant DND access: $result"
                        )
                        if (granted) view?.let { refresh(it) }
                        Shizuku.unbindUserService(args, currentConnection, true)
                        shizukuServiceArgs = null
                        shizukuServiceConnection = null
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shizukuServiceArgs = null
                shizukuServiceConnection = null
                view?.let {
                    toast("The Shizuku service disconnected before granting access.")
                    refresh(it)
                }
            }
        }
        shizukuServiceArgs = args
        shizukuServiceConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (exception: SecurityException) {
            shizukuServiceArgs = null
            shizukuServiceConnection = null
            toast("Shizuku denied DNDx permission to start its service.")
        } catch (exception: IllegalStateException) {
            shizukuServiceArgs = null
            shizukuServiceConnection = null
            toast("Shizuku is no longer available. Start it and try again.")
        }
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
        private const val REQUEST_SHIZUKU_PERMISSION = 202
    }
}
