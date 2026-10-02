/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object PerAppProfileStore {

    const val PREF_ENABLED = "per_app_thermal_enabled"
    const val PREF_PREFIX = "per_app_thermal_pkg_"
    const val PREF_BASE_PROFILE = "per_app_base_profile"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    fun getProfile(context: Context, packageName: String): Int? {
        val raw = prefs(context).getString(PREF_PREFIX + packageName, null) ?: return null
        return raw.toIntOrNull()
    }

    fun setProfile(context: Context, packageName: String, profile: Int?) {
        val key = PREF_PREFIX + packageName
        val editor = prefs(context).edit()
        if (profile == null) {
            editor.remove(key)
        } else {
            editor.putString(key, profile.toString())
        }
        editor.apply()
    }

    fun getConfiguredPackages(context: Context): List<String> {
        val all = prefs(context).all
        return all.keys
            .filter { it.startsWith(PREF_PREFIX) }
            .map { it.removePrefix(PREF_PREFIX) }
            .filter { all[PREF_PREFIX + it] != null }
    }

    fun count(context: Context): Int = getConfiguredPackages(context).size

    fun baseProfile(context: Context, fallback: Int): Int =
        prefs(context).getString(PREF_BASE_PROFILE, null)?.toIntOrNull()
            ?: prefs(context).getString(ThermalProfileFragment.PREF_THERMAL_PROFILE, null)
                ?.toIntOrNull()
            ?: fallback

    fun setBaseProfile(context: Context, profile: Int) {
        prefs(context).edit().putString(PREF_BASE_PROFILE, profile.toString()).apply()
    }
}
