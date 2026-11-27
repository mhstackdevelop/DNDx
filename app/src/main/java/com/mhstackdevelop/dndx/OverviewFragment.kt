package com.mhstackdevelop.dndx

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku

class OverviewFragment : Fragment() {
    private val callRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val roleGranted = isCallScreeningRoleGranted()
            toast(
                if (roleGranted) "Call screening enabled. DNDx can now reject non-exempt calls while active."
                else "Call screening role was not granted; calls will not be rejected by DNDx."
            )
            view?.let { refresh(it) }
        }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        view?.let { refresh(it) }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        view?.let { refresh(it) }
    }

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
        view.findViewById<MaterialButton>(R.id.button_device_admin).setOnClickListener {
            handleDeviceAdminAction()
        }
        view.findViewById<MaterialButton>(R.id.button_remove_admin).setOnClickListener {
            confirmRemoveDeviceAdmin()
        }
        view.findViewById<MaterialButton>(R.id.button_overlay).setOnClickListener {
            toggleOverlay()
        }
        refresh(view)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        view?.let { refresh(it) }
    }

    override fun onStop() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        super.onStop()
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
        completeOverlayPermissionFlow()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View) {
        val context = requireContext()
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val active = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)
        val roleAvailable = isCallScreeningRoleAvailable()
        val roleGranted = isCallScreeningRoleGranted()
        val screenNonExempt = prefs.getBoolean(MainActivity.KEY_SCREEN_NON_EXEMPT, true)
        val policyGranted = notificationManager.isNotificationPolicyAccessGranted
        val shizukuRunning = Shizuku.pingBinder()
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        val adminActive = devicePolicyManager.isAdminActive(deviceAdminComponent())
        val overlayAllowed = Settings.canDrawOverlays(context)
        val overlayEnabled = prefs.getBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)

        view.findViewById<TextView>(R.id.status_text).text =
            if (active) "DNDx is ON" else "DNDx is OFF"
        view.findViewById<TextView>(R.id.status_detail).text = when {
            active && policyGranted -> "Priority mode is enabled. Your previous DND setting will be restored when turned off."
            active -> "DNDx is marked on, but Android policy access is missing. Grant access to control DND."
            policyGranted -> "DND policy access is ready. Enable DND when you want some quiet."
            else -> "Grant DND policy access to let DNDx control Priority mode."
        }
        view.findViewById<TextView>(R.id.call_screening_status).text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                "Call rejection requires Android 10 or later. DND can still silence calls."
            !roleAvailable ->
                "This device does not offer Android's call-screening role. DND can still silence calls."
            !roleGranted ->
                "Calls are not being rejected yet. Grant the call-screening role below; DND access alone only controls silencing."
            !screenNonExempt ->
                "Call-screening role granted, but rejection is turned off in Rules."
            else ->
                "Call screening is ready. Non-exempt incoming calls are rejected only while DNDx is on."
        }
        view.findViewById<MaterialButton>(R.id.button_toggle).text =
            if (active) "Turn off DND" else "Enable DND"

        view.findViewById<MaterialButton>(R.id.button_call_role).text =
            when {
                !roleAvailable -> "Call blocking unavailable"
                roleGranted -> "Call-screening role: Granted"
                else -> "Grant role to block calls"
            }
        view.findViewById<MaterialButton>(R.id.button_call_role).isEnabled = roleAvailable
        view.findViewById<MaterialButton>(R.id.button_policy).text =
            if (policyGranted) "DND access: Granted" else "Grant DND policy access"
        view.findViewById<MaterialButton>(R.id.button_shizuku).apply {
            text = when {
                policyGranted -> "DND access granted"
                shizukuRunning -> "Request DND access from Shizuku"
                context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null ->
                    "Open Shizuku to start service"
                else -> "Install Shizuku to grant access"
            }
            isEnabled = !policyGranted
        }
        view.findViewById<MaterialButton>(R.id.button_notifications).text =
            if (notificationsGranted) "Notifications: Granted" else "Enable notifications"
        view.findViewById<MaterialButton>(R.id.button_device_admin).text =
            if (adminActive) "Lock screen now" else "Enable Device Admin"
        view.findViewById<MaterialButton>(R.id.button_remove_admin).visibility =
            if (adminActive) View.VISIBLE else View.GONE
        view.findViewById<MaterialButton>(R.id.button_overlay).text = when {
            overlayEnabled && overlayAllowed -> "Disable floating DND control"
            overlayEnabled -> "Grant overlay access to restore control"
            overlayAllowed -> "Enable floating DND control"
            else -> "Allow display over other apps"
        }
    }

    private fun toggleDnd() {
        when (DndController.toggle(requireContext())) {
            DndController.ToggleResult.ENABLED,
            DndController.ToggleResult.DISABLED -> Unit
            DndController.ToggleResult.POLICY_ACCESS_REQUIRED -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                toast("Grant DND policy access, then try again.")
            }
            DndController.ToggleResult.ACCESS_DENIED ->
                toast("Android denied access to change DND settings.")
        }
        view?.let { refresh(it) }
    }

    private fun handleDeviceAdminAction() {
        val context = requireContext()
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        val component = deviceAdminComponent()
        if (manager.isAdminActive(component)) {
            try {
                manager.lockNow()
            } catch (_: SecurityException) {
                toast("Android denied the screen-lock request.")
            }
            return
        }

        val request = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_description)
            )
        }
        startActivity(request)
    }

    private fun confirmRemoveDeviceAdmin() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Device Admin?")
            .setMessage("DNDx will no longer be able to lock the screen.")
            .setNegativeButton("Keep enabled", null)
            .setPositiveButton("Remove") { _, _ ->
                requireContext().getSystemService(DevicePolicyManager::class.java)
                    .removeActiveAdmin(deviceAdminComponent())
                view?.let { refresh(it) }
            }
            .show()
    }

    private fun deviceAdminComponent() =
        ComponentName(requireContext(), DndDeviceAdminReceiver::class.java)

    private fun toggleOverlay() {
        val context = requireContext()
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        if (!Settings.canDrawOverlays(context)) {
            prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_PENDING, true).apply()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        }

        prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_PENDING, false).apply()
        val enabled = prefs.getBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
        if (enabled) {
            prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false).apply()
            context.stopService(Intent(context, OverlayControlService::class.java))
            toast("Floating DND control turned off.")
        } else {
            prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_ENABLED, true).apply()
            startOverlayService()
            toast("Floating DND control is on. Drag it to move.")
        }
        view?.let { refresh(it) }
    }

    private fun completeOverlayPermissionFlow() {
        val context = context ?: return
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        val pending = prefs.getBoolean(MainActivity.KEY_OVERLAY_PENDING, false)
        val enabled = prefs.getBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
        val allowed = Settings.canDrawOverlays(context)

        if (pending) {
            prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_PENDING, false).apply()
            if (allowed) {
                prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_ENABLED, true).apply()
                startOverlayService()
                toast("Floating DND control enabled.")
            } else {
                toast("Display-over-other-apps access was not granted.")
            }
        } else if (enabled && allowed) {
            startOverlayService()
        } else if (enabled) {
            prefs.edit().putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false).apply()
            context.stopService(Intent(context, OverlayControlService::class.java))
        }
    }

    private fun startOverlayService() {
        val context = requireContext()
        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayControlService::class.java)
        )
    }

    private fun confirmShizukuGrant() {
        if (!Shizuku.pingBinder()) {
            val managerIntent = requireContext().packageManager
                .getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (managerIntent != null) {
                startActivity(managerIntent)
                toast("Start Shizuku, then return here. DNDx will request Shizuku permission.")
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Shizuku is not installed")
                    .setMessage(
                        "Install Shizuku and start its service first. DNDx will then request " +
                            "Shizuku permission before granting DND access."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Get Shizuku") { _, _ ->
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://shizuku.rikka.app/download/")
                            )
                        )
                    }
                    .show()
            }
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
                    Shizuku.isPreV11() ->
                        toast("This Shizuku service version is not supported. Update Shizuku.")
                    else -> requestShizukuPermission()
                }
            }
            .show()
    }

    private fun requestShizukuPermission() {
        try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    grantPolicyAccessWithShizuku()
                Shizuku.shouldShowRequestPermissionRationale() ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Shizuku permission needed")
                        .setMessage(
                            "DNDx needs Shizuku permission to grant Android DND policy access. " +
                                "Approve DNDx in Shizuku, then try again."
                        )
                        .setPositiveButton("Open Shizuku") { _, _ ->
                            requireContext().packageManager
                                .getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                                ?.let(::startActivity)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                else -> {
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
                    toast("Approve the DNDx permission request in Shizuku.")
                }
            }
        } catch (_: IllegalStateException) {
            toast("Shizuku disconnected. Start its service and try again.")
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Call rejection requires Android 10 or later and device support.")
            return
        }
        val roleManager = requireContext().getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            callRoleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            )
        } else {
            toast("Call-screening is unavailable on this device.")
        }
    }

    private fun isCallScreeningRoleAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            requireContext().getSystemService(RoleManager::class.java)
                .isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun isCallScreeningRoleGranted(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            requireContext().getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
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
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_NOTIFICATIONS = 201
        private const val REQUEST_SHIZUKU_PERMISSION = 202
    }
}
