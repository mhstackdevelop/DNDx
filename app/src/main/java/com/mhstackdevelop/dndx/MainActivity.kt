package com.mhstackdevelop.dndx

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "DNDx"
            textSize = 30f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Silence interruptions and screen incoming calls. Your selected contacts are allowed through the call screen."
            textSize = 16f
            setPadding(0, 10, 0, 14)
        })

        status = TextView(this).apply { textSize = 15f; setPadding(0, 8, 0, 12) }
        content.addView(status)

        content.addView(button("Enable DND") { enableDnd() })
        content.addView(button("Turn off DND") { disableDnd() })
        content.addView(button("Grant call-screening role") { requestCallScreeningRole() })
        content.addView(button("Grant DND policy access") { openPolicyAccessSettings() })
        content.addView(button("Configure Android DND exceptions") { openZenSettings() })
        content.addView(button("Add exempt contact") { addContact() })

        content.addView(TextView(this).apply {
            text = "Exempt contacts"
            textSize = 20f
            setPadding(0, 18, 0, 6)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        content.addView(TextView(this).apply {
            text = "Calls: Android 10+ call screening can reject non-exempt incoming calls after you grant the call-screening role. Messages: Android DND can silence message notifications, but Android does not let ordinary apps reject or prevent delivery of carrier SMS. Configure starred-contact exceptions in Android DND settings if you want those contacts’ notifications to bypass DND."
            textSize = 14f
            setPadding(0, 20, 0, 10)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
    }

    private fun enableDnd() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Call screening requires Android 10 or later on this device.")
            return
        }
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            requestCallScreeningRole()
            toast("Grant the call-screening role, then tap Enable DND again.")
            return
        }
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            openPolicyAccessSettings()
            toast("Grant DND policy access, then tap Enable DND again.")
            return
        }

        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            prefs.edit()
                .putInt(KEY_PREVIOUS_FILTER, notificationManager.currentInterruptionFilter)
                .putBoolean(KEY_ACTIVE, true)
                .apply()
        }
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        refresh()
        toast("DND enabled. Review Android DND exceptions for notification behavior.")
    }

    private fun disableDnd() {
        if (prefs.getBoolean(KEY_ACTIVE, false) && notificationManager.isNotificationPolicyAccessGranted) {
            val previous = prefs.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            notificationManager.setInterruptionFilter(previous)
        }
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
        refresh()
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Call screening is available on Android 10 and later.")
            return
        }
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_ROLE)
        } else {
            toast("This device does not offer the Android call-screening role.")
        }
    }

    private fun openPolicyAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun openZenSettings() {
        val intent = Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS")
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else openPolicyAccessSettings()
    }

    private fun addContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            return
        }
        val pick = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(pick, REQUEST_PICK_CONTACT)
    }

    @Deprecated("Contact picker result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_CONTACT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            readPickedContact(uri)?.let { (name, number) ->
                val entries = prefs.getStringSet(KEY_EXEMPTIONS, emptySet()).orEmpty().toMutableSet()
                entries.add("$name|$number")
                prefs.edit().putStringSet(KEY_EXEMPTIONS, entries).apply()
                refresh()
            }
        }
    }

    private fun readPickedContact(uri: Uri): Pair<String, String>? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) return null
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Contact"
                val number = cursor.getString(1)?.trim()?.takeIf { it.isNotBlank() } ?: return null
                name to number
            }
        } catch (_: SecurityException) {
            toast("Contacts permission is required to add an exemption.")
            null
        }
    }

    private fun refresh() {
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val roleGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val policyGranted = notificationManager.isNotificationPolicyAccessGranted
        status.text = "Status: ${if (active) "ON" else "OFF"}\n" +
            "Call screening role: ${if (roleGranted) "granted" else "not granted"}\n" +
            "DND policy access: ${if (policyGranted) "granted" else "not granted"}"

        if (!::list.isInitialized) return
        list.removeAllViews()
        val exemptions = prefs.getStringSet(KEY_EXEMPTIONS, emptySet()).orEmpty().toList().sorted()
        if (exemptions.isEmpty()) {
            list.addView(TextView(this).apply { text = "No exempt contacts yet." })
        }
        exemptions.forEach { entry ->
            val pieces = entry.split('|', limit = 2)
            val name = pieces.firstOrNull().orEmpty()
            val number = pieces.getOrNull(1).orEmpty()
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "$name\n$number"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(Button(this@MainActivity).apply {
                    text = "Remove"
                    setOnClickListener {
                        val updated = prefs.getStringSet(KEY_EXEMPTIONS, emptySet()).orEmpty().toMutableSet()
                        updated.remove(entry)
                        prefs.edit().putStringSet(KEY_EXEMPTIONS, updated).apply()
                        refresh()
                    }
                })
            })
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val PREFS = "dndx_settings"
        const val KEY_ACTIVE = "dnd_active"
        const val KEY_PREVIOUS_FILTER = "previous_filter"
        const val KEY_EXEMPTIONS = "exemptions"
        private const val REQUEST_ROLE = 100
        private const val REQUEST_CONTACTS = 101
        private const val REQUEST_PICK_CONTACT = 102
    }
}
