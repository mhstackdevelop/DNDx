package com.mhstackdevelop.dndx

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class ExemptionsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_exemptions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.button_add_exemption)
            .setOnClickListener { addContact() }
        refresh(view)
    }

    private fun addContact() {
        if (requireContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS
            )
            return
        }
        val pick = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        startActivityForResult(pick, REQUEST_PICK_CONTACT)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) addContact()
            else toast("Contacts permission is required to add an exemption.")
        }
    }

    @Deprecated("Use modern Activity Result APIs")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_CONTACT && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            readPickedContact(uri)?.let { (name, number) ->
                val prefs = requireContext().getSharedPreferences(MainActivity.PREFS, 0)
                val entries = prefs.getStringSet(
                    MainActivity.KEY_EXEMPTIONS,
                    emptySet()
                ).orEmpty().toMutableSet()
                entries.add("$name|$number")
                prefs.edit().putStringSet(MainActivity.KEY_EXEMPTIONS, entries).apply()
                view?.let {
                    refresh(it)
                    it.announceForAccessibility("$name added to exemptions.")
                }
            }
        }
    }

    private fun readPickedContact(uri: Uri): Pair<String, String>? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            requireContext().contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) return null
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Contact"
                val number = cursor.getString(1)?.trim()?.takeIf { it.isNotBlank() }
                    ?: return null
                name to number
            }
        } catch (_: SecurityException) {
            toast("Contacts permission is required.")
            null
        }
    }

    private fun refresh(view: View) {
        val context = requireContext()
        val prefs = context.getSharedPreferences(MainActivity.PREFS, 0)
        val list = view.findViewById<LinearLayout>(R.id.exemption_list)
        list.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        list.removeAllViews()
        val entries = prefs.getStringSet(MainActivity.KEY_EXEMPTIONS, emptySet())
            .orEmpty().toList().sorted()

        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "No exempt contacts yet"
                textSize = 16f
                setPadding(16, 24, 16, 24)
            })
            return
        }

        entries.forEach { entry ->
            val pieces = entry.split('|', limit = 2)
            val name = pieces.firstOrNull().orEmpty()
            val number = pieces.getOrNull(1).orEmpty()
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
                addView(TextView(context).apply {
                    text = "$name\n$number"
                    contentDescription = "$name, phone number $number"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                addView(MaterialButton(context).apply {
                    text = "Remove"
                    contentDescription = "Remove $name from exemptions"
                    setOnClickListener {
                        val updated = prefs.getStringSet(
                            MainActivity.KEY_EXEMPTIONS,
                            emptySet()
                        ).orEmpty().toMutableSet()
                        updated.remove(entry)
                        prefs.edit()
                            .putStringSet(MainActivity.KEY_EXEMPTIONS, updated)
                            .apply()
                        view.let {
                            refresh(it)
                            it.announceForAccessibility("$name removed from exemptions.")
                        }
                    }
                })
            })
        }
    }

    private fun toast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_CONTACTS = 301
        private const val REQUEST_PICK_CONTACT = 302
    }
}
