/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.app.Activity
import android.app.DialogFragment
import android.app.Fragment
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View

import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragment

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

import com.xiaomi.settings.corecontrol.CoreControlFragment
import com.xiaomi.settings.touchsampling.TouchSamplingFragment
import com.xiaomi.settings.touchsampling.TouchSamplingTileService
import com.xiaomi.settings.turbocharging.TurboChargingFragment
import com.xiaomi.settings.thermal.ThermalProfileFragment
import com.xiaomi.settings.ui.HomeFragment
import com.xiaomi.settings.ui.PillNavBarView

class PartsActivity : Activity(), HomeFragment.Navigator,
        PreferenceFragment.OnPreferenceDisplayDialogCallback {

    private lateinit var navBar: PillNavBarView
    private lateinit var content: View
    private var currentTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(R.layout.activity_parts)

        content = findViewById(R.id.parts_content)
        navBar = findViewById(R.id.pill_nav)

        navBar.addItem(TAB_HOME, R.drawable.ic_m3_home, R.string.nav_home)
        navBar.addItem(TAB_THERMAL, R.drawable.ic_thermal_tile, R.string.nav_thermals)
        navBar.addItem(TAB_CHARGING, R.drawable.ic_m3_bolt, R.string.nav_charging)
        navBar.addItem(TAB_CPU, R.drawable.ic_nav_cpu, R.string.nav_cpu)
        navBar.addItem(TAB_HTSR, R.drawable.ic_touch_sampling_tile, R.string.nav_htsr)

        navBar.onItemSelectedListener = { tab -> switchTo(tab) }

        applyWindowInsets()

        currentTab = savedInstanceState?.getInt(STATE_TAB, TAB_HOME)
                ?: resolveLaunchTab(intent) ?: TAB_HOME
        if (savedInstanceState == null ||
                fragmentManager.findFragmentById(R.id.parts_content) == null) {
            switchTo(currentTab)
        } else {
            navBar.setSelectedItem(currentTab)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        resolveLaunchTab(intent)?.let { if (it != currentTab) switchTo(it) }
    }

    private fun resolveLaunchTab(intent: Intent?): Int? {
        if (intent == null) return null
        val extra = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        if (extra in TAB_HOME..TAB_HTSR) return extra
        val comp: ComponentName? = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        if (comp != null &&
                comp.className == TouchSamplingTileService::class.java.name) {
            return TAB_HTSR
        }
        return null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTab)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.parts_root)
        val pillBottom = resources.getDimensionPixelSize(R.dimen.nav_pill_margin_bottom)
        val pillHeight = resources.getDimensionPixelSize(R.dimen.nav_item_height) +
                2 * resources.getDimensionPixelSize(R.dimen.nav_pill_padding_v)
        val contentTopPad = resources.getDimensionPixelSize(R.dimen.m3_space_s)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())

            content.setPadding(0, systemBars.top + contentTopPad, 0,
                    systemBars.bottom + pillHeight + pillBottom * 2)

            navBar.minimumHeight = pillHeight
            (navBar.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = systemBars.bottom + pillBottom
                navBar.layoutParams = lp
            }

            insets
        }
    }

    fun switchTo(tab: Int) {
        currentTab = tab
        navBar.setSelectedItem(tab)

        val fragment: Fragment = when (tab) {
            TAB_THERMAL -> ThermalProfileFragment()
            TAB_CHARGING -> TurboChargingFragment()
            TAB_CPU -> CoreControlFragment()
            TAB_HTSR -> TouchSamplingFragment()
            else -> HomeFragment()
        }

        fragmentManager
            .beginTransaction()
            .setTransition(android.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.parts_content, fragment, "parts_tab_$tab")
            .commitAllowingStateLoss()
    }

    override fun onNavigate(tab: Int) {
        switchTo(tab)
    }

    override fun onPreferenceDisplayDialog(caller: PreferenceFragment, pref: Preference): Boolean {
        if (pref is ListPreference) {
            val dialog: DialogFragment = ListPreferenceDialogFragment.newInstance(pref.key)
            dialog.setTargetFragment(caller, 0)
            dialog.show(fragmentManager, DIALOG_FRAGMENT_TAG)
            return true
        }
        return false
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_THERMAL = 1
        const val TAB_CHARGING = 2
        const val TAB_CPU = 3
        const val TAB_HTSR = 4
        const val EXTRA_OPEN_TAB = "extra_open_tab"

        private const val STATE_TAB = "selected_tab"
        private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
