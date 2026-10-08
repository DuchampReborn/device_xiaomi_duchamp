/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touchsampling

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

class TouchSamplingFragment : Fragment() {

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    private lateinit var prefsMaster: Switch
    private lateinit var prefsAuto: Switch
    private lateinit var searchBox: EditText
    private lateinit var loadingView: TextView
    private lateinit var adapter: TouchSamplingAdapter

    private val allApps = mutableListOf<AppEntry>()
    private val autoApps = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_touch_sampling, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefsMaster = view.findViewById(R.id.switch_touch_master)
        prefsAuto = view.findViewById(R.id.switch_touch_auto)
        searchBox = view.findViewById(R.id.et_search)
        loadingView = view.findViewById(R.id.tv_loading)

        val recycler = view.findViewById<RecyclerView>(R.id.rv_apps)
        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = TouchSamplingAdapter()
        recycler.adapter = adapter

        val activity = activity ?: return
        val shared = activity.getSharedPreferences(SHARED_HTSR, android.content.Context.MODE_PRIVATE)
        prefsMaster.isChecked = shared.getBoolean(PREF_HTSR_STATE, false)
        prefsAuto.isChecked = shared.getBoolean(PREF_HTSR_AUTO, true)

        prefsMaster.setOnCheckedChangeListener { _, checked ->
            shared.edit().putBoolean(PREF_HTSR_STATE, checked).apply()
            syncServiceState()
        }

        prefsAuto.setOnCheckedChangeListener { _, checked ->
            shared.edit().putBoolean(PREF_HTSR_AUTO, checked).apply()
            syncServiceState()
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })

        loadAutoApps()
        loadAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        loadAutoApps()
        adapter.notifyDataSetChanged()
    }

    private fun loadAutoApps() {
        val activity = activity ?: return
        autoApps.clear()
        autoApps.addAll(
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getStringSet(PREF_HTSR_AUTO_APPS, emptySet()) ?: emptySet())
    }

    internal fun syncServiceState() {
        val activity = activity ?: return
        val shared = activity.getSharedPreferences(SHARED_HTSR, android.content.Context.MODE_PRIVATE)
        val main = shared.getBoolean(PREF_HTSR_STATE, false)
        val auto = shared.getBoolean(PREF_HTSR_AUTO, true)
        val intent = Intent(activity, TouchSamplingService::class.java)
        if (main || auto) {
            activity.startService(intent)
        } else {
            activity.stopService(intent)
            com.xiaomi.settings.utils.FileUtils.writeLine(
                TouchSamplingUtils.HTSR_FILE, "0")
        }
    }

    internal fun setAutoEnabled(pkg: String, enabled: Boolean) {
        val activity = activity ?: return
        val updated = autoApps.toMutableSet()
        if (enabled) {
            updated.add(pkg)
        } else {
            updated.remove(pkg)
        }
        if (updated == autoApps) return
        autoApps.clear()
        autoApps.addAll(updated)
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(activity)
            .edit()
            .putStringSet(PREF_HTSR_AUTO_APPS, autoApps)
            .apply()
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

    private inner class TouchSamplingAdapter : RecyclerView.Adapter<TouchSamplingAdapter.Holder>() {

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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_touch_sampling_app, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = visible.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = visible[position]

            app.icon?.let { holder.icon.setImageDrawable(it) }
                ?: run {
                    holder.icon.setImageResource(R.drawable.ic_m3_apps)
                }
            holder.name.text = app.label
            holder.pkg.text = app.packageName

            holder.switchView.setOnCheckedChangeListener(null)
            holder.switchView.isChecked = autoApps.contains(app.packageName)
            holder.switchView.setOnCheckedChangeListener { _, checked ->
                setAutoEnabled(app.packageName, checked)
            }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_app_icon)
            val name: TextView = view.findViewById(R.id.tv_app_name)
            val pkg: TextView = view.findViewById(R.id.tv_app_package)
            val switchView: Switch = view.findViewById(R.id.switch_touch_app)
        }
    }

    companion object {
        const val SHARED_HTSR = "SHAREDHTSR"
        const val PREF_HTSR_STATE = "htsr_state"
        const val PREF_HTSR_AUTO = "htsr_auto_enable_selected_apps"
        const val PREF_HTSR_AUTO_APPS = "htsr_auto_apps"
    }
}
