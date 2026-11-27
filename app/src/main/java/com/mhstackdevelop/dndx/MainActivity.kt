package com.mhstackdevelop.dndx

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_rules -> RulesFragment()
                R.id.nav_exemptions -> ExemptionsFragment()
                else -> OverviewFragment()
            }
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_overview
        }
    }

    companion object {
        const val PREFS = "dndx_settings"
        const val KEY_ACTIVE = "dnd_active"
        const val KEY_PREVIOUS_FILTER = "previous_filter"
        const val KEY_EXEMPTIONS = "exemptions"
        const val KEY_SCREEN_NON_EXEMPT = "screen_non_exempt"
        const val KEY_RESTORE_ON_BOOT = "restore_on_boot"
    }
}
