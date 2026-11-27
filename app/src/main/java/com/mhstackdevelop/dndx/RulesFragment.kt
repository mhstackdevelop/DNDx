package com.mhstackdevelop.dndx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class RulesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_rules, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences(MainActivity.PREFS, 0)

        view.findViewById<MaterialSwitch>(R.id.switch_screen_calls).apply {
            isChecked = prefs.getBoolean(MainActivity.KEY_SCREEN_NON_EXEMPT, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(MainActivity.KEY_SCREEN_NON_EXEMPT, checked).apply()
            }
        }

        view.findViewById<MaterialSwitch>(R.id.switch_restore_boot).apply {
            isChecked = prefs.getBoolean(MainActivity.KEY_RESTORE_ON_BOOT, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(MainActivity.KEY_RESTORE_ON_BOOT, checked).apply()
            }
        }
    }
}
