/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.ui

import android.app.Fragment
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.preference.PreferenceManager

import com.xiaomi.settings.PartsActivity
import com.xiaomi.settings.R
import com.xiaomi.settings.thermal.PerAppProfileStore
import com.xiaomi.settings.thermal.PerAppThermalActivity
import com.xiaomi.settings.thermal.ThermalProfileFragment
import com.xiaomi.settings.turbocharging.TurboChargingFragment
import com.xiaomi.settings.utils.FileUtils
import java.io.BufferedReader
import java.io.FileReader

class HomeFragment : Fragment() {

    interface Navigator {
        fun onNavigate(tab: Int)
    }

    private var navigator: Navigator? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var tvThermalStatus: TextView
    private lateinit var tvChargingStatus: TextView
    private lateinit var tvCoresStatus: TextView
    private lateinit var tvPerAppStatus: TextView

    override fun onAttach(activity: android.app.Activity) {
        super.onAttach(activity)
        navigator = activity as? Navigator
    }

    override fun onDetach() {
        navigator = null
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(activity)

        tvThermalStatus = view.findViewById(R.id.tv_thermal_status)
        tvChargingStatus = view.findViewById(R.id.tv_charging_status)
        tvCoresStatus = view.findViewById(R.id.tv_cores_status)
        tvPerAppStatus = view.findViewById(R.id.tv_perapp_status)

        view.findViewById<View>(R.id.card_thermal).setOnClickListener {
            navigator?.onNavigate(PartsActivity.TAB_THERMAL)
        }
        view.findViewById<View>(R.id.card_charging).setOnClickListener {
            navigator?.onNavigate(PartsActivity.TAB_CHARGING)
        }
        view.findViewById<View>(R.id.card_cores).setOnClickListener {
            navigator?.onNavigate(PartsActivity.TAB_CPU)
        }
        view.findViewById<View>(R.id.card_perapp).setOnClickListener {
            startActivity(Intent(activity, PerAppThermalActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val activity = activity ?: return

        val profile = FileUtils.readLineInt(ThermalProfileFragment.THERMAL_PROFILE_PATH)
        tvThermalStatus.text = when (profile) {
            ThermalProfileFragment.THERMAL_PROFILE_DEFAULT ->
                getString(R.string.thermalprofile_default)
            ThermalProfileFragment.THERMAL_PROFILE_MPERFORMANCE ->
                getString(R.string.thermalprofile_performance)
            ThermalProfileFragment.THERMAL_PROFILE_MBATTERY ->
                getString(R.string.thermalprofile_battery)
            ThermalProfileFragment.THERMAL_PROFILE_MGAME ->
                getString(R.string.thermalprofile_game)
            else -> getString(R.string.thermalprofile_unknown)
        }

        val turbo = prefs.getBoolean(TurboChargingFragment.PREF_TURBO_ENABLED, false)
        val bypassState = readBypassState()
        val bypassText = when {
            bypassState == null -> getString(R.string.home_bypass_unavailable)
            bypassState -> getString(R.string.home_bypass_on)
            else -> getString(R.string.home_bypass_off)
        }
        tvChargingStatus.text = getString(
            if (turbo) R.string.home_charging_on else R.string.home_charging_off
        ) + " · " + bypassText

        var online = 0
        for (i in 0..7) {
            if (isCoreOnline(i)) online++
        }
        tvCoresStatus.text = getString(R.string.home_cores_status, online, 8)

        if (PerAppProfileStore.isEnabled(activity)) {
            val count = PerAppProfileStore.count(activity)
            tvPerAppStatus.text = getString(R.string.perapp_status_on_summary, count)
        } else {
            tvPerAppStatus.text = getString(R.string.perapp_status_off)
        }
    }

    private fun isCoreOnline(core: Int): Boolean {
        return try {
            val path = "/sys/devices/system/cpu/cpu$core/online"
            FileUtils.readLineInt(path) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun readBypassState(): Boolean? {
        return try {
            BufferedReader(FileReader(TurboChargingFragment.BYPASS_CHARGING_FILE)).use { reader ->
                val line = reader.readLine() ?: return null
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size > 1) parts[1] == "1" else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
