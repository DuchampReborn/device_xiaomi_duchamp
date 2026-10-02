/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.xiaomi.settings.R
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.PREF_THERMAL_PROFILE
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_DEFAULT
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_MGAME
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_MBATTERY
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_MPERFORMANCE

class PerAppThermalFragment : Fragment() {

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    private lateinit var prefsMaster: Switch
    private lateinit var searchBox: EditText
    private lateinit var loadingView: TextView
    private lateinit var adapter: PerAppAdapter

    private val allApps = mutableListOf<AppEntry>()
    private var profileValues: List<Int?> = emptyList()
    private var profileLabels: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_per_app_thermal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildProfileOptions()

        prefsMaster = view.findViewById(R.id.switch_perapp_master)
        searchBox = view.findViewById(R.id.et_search)
        loadingView = view.findViewById(R.id.tv_loading)

        val recycler = view.findViewById<RecyclerView>(R.id.rv_apps)
        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = PerAppAdapter { app -> showProfilePicker(app) }
        recycler.adapter = adapter

        prefsMaster.isChecked = if (activity != null) {
            PerAppProfileStore.isEnabled(activity)
        } else {
            false
        }
        prefsMaster.setOnCheckedChangeListener { _, checked ->
            handleMasterSwitch(checked)
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })

        loadAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun handleMasterSwitch(enabled: Boolean) {
        val activity = activity ?: return
        PerAppProfileStore.setEnabled(activity, enabled)
        if (enabled) {
            PerAppProfileService.start(activity)
        } else {
            val base = PerAppProfileStore.baseProfile(activity, THERMAL_PROFILE_DEFAULT)
            com.xiaomi.settings.utils.FileUtils.writeLine(
                ThermalProfileFragment.THERMAL_PROFILE_PATH, base)
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(PREF_THERMAL_PROFILE, base.toString())
                .apply()
            PerAppProfileService.stop(activity)
        }
    }

    private fun buildProfileOptions() {
        profileValues = listOf(
            null,
            THERMAL_PROFILE_DEFAULT,
            THERMAL_PROFILE_MPERFORMANCE,
            THERMAL_PROFILE_MBATTERY,
            THERMAL_PROFILE_MGAME
        )
        profileLabels = listOf(
            getString(R.string.perapp_profile_system),
            getString(R.string.thermalprofile_default),
            getString(R.string.thermalprofile_performance),
            getString(R.string.thermalprofile_battery),
            getString(R.string.thermalprofile_game)
        )
    }

    private fun loadAppsAsync() {
        val activity = activity ?: return
        val pm = activity.packageManager
        Thread {
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = try {
                pm.queryIntentActivities(launcherIntent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            val seen = HashSet<String>()
            val entries = resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (!seen.add(pkg)) return@mapNotNull null
                try {
                    AppEntry(
                        label = info.loadLabel(pm)?.toString() ?: pkg,
                        packageName = pkg,
                        icon = info.loadIcon(pm)
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.label.lowercase() }

            activity.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allApps.clear()
                allApps.addAll(entries)
                loadingView.visibility = View.GONE
                adapter.submit(allApps)
            }
        }.start()
    }

    private fun showProfilePicker(app: AppEntry) {
        val activity = activity ?: return
        val current = PerAppProfileStore.getProfile(activity, app.packageName)
        val checked = profileValues.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(activity)
            .setTitle(app.label)
            .setSingleChoiceItems(profileLabels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                PerAppProfileStore.setProfile(activity, app.packageName, profileValues[which])
                adapter.refreshChips()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class PerAppAdapter(
        private val onAppClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<PerAppAdapter.Holder>() {

        private val visible = mutableListOf<AppEntry>()
        private var query: String = ""

        fun submit(apps: List<AppEntry>) {
            visible.clear()
            visible.addAll(apps)
            applyFilter()
        }

        fun filter(newQuery: String) {
            query = newQuery.trim()
            applyFilter()
        }

        private fun applyFilter() {
            visible.clear()
            val source = if (query.isEmpty()) allApps else allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
            visible.addAll(source)
            notifyDataSetChanged()
        }

        fun refreshChips() {
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_per_app, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = visible.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = visible[position]
            val activity = activity ?: return

            app.icon?.let { holder.icon.setImageDrawable(it) }
                ?: run {
                    holder.icon.setImageResource(R.drawable.ic_m3_apps)
                }
            holder.name.text = app.label
            holder.pkg.text = app.packageName

            val assigned = PerAppProfileStore.getProfile(activity, app.packageName)
            if (assigned != null) {
                val index = profileValues.indexOf(assigned)
                holder.chip.text = if (index >= 0) profileLabels[index]
                    else getString(R.string.thermalprofile_unknown)
                holder.chip.setBackgroundResource(R.drawable.bg_chip_green)
                holder.chip.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        activity, R.color.nav_on_green))
            } else {
                holder.chip.text = getString(R.string.perapp_default_chip)
                holder.chip.setBackgroundResource(R.drawable.bg_chip_idle)
                holder.chip.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        activity, R.color.m3_on_surface_variant))
            }

            holder.itemView.setOnClickListener { onAppClick(app) }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_app_icon)
            val name: TextView = view.findViewById(R.id.tv_app_name)
            val pkg: TextView = view.findViewById(R.id.tv_app_package)
            val chip: TextView = view.findViewById(R.id.tv_app_profile_chip)
        }
    }
}
