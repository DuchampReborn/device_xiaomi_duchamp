/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.PREF_THERMAL_PROFILE
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_DEFAULT
import com.xiaomi.settings.thermal.ThermalProfileFragment.Companion.THERMAL_PROFILE_PATH
import com.xiaomi.settings.utils.FileUtils

class PerAppProfileService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var overrideActive = false
    private var screenOn = true

    private val tick = object : Runnable {
        override fun run() {
            if (screenOn) checkForeground(force = false)
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    handler.postDelayed({ checkForeground(force = true) }, 600)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                }
            }
        }
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                key == PerAppProfileStore.PREF_ENABLED &&
                        !PerAppProfileStore.isEnabled(this@PerAppProfileService) -> {
                    restoreBaseProfile()
                    stopSelf()
                }
                key == PerAppProfileStore.PREF_ENABLED ||
                        key?.startsWith(PerAppProfileStore.PREF_PREFIX) == true -> {
                    handler.post { checkForeground(force = true) }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        screenOn = try {
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        } catch (e: Exception) {
            true
        }

        if (PerAppProfileStore.baseProfile(this, -1) == -1) {
            val current = FileUtils.readLineInt(THERMAL_PROFILE_PATH)
            if (current != 0) {
                PerAppProfileStore.setBaseProfile(this, current)
            }
        }

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(prefListener)

        handler.post(tick)
        checkForeground(force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkForeground(force = true)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screenStateReceiver)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForeground(force: Boolean) {
        if (!PerAppProfileStore.isEnabled(this)) return

        val foreground = detectForegroundPackage() ?: return
        if (!force && foreground == lastForegroundPackage) return
        lastForegroundPackage = foreground
        applyForPackage(foreground)
    }

    private fun detectForegroundPackage(): String? {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - EVENT_WINDOW_MS, now)
                val event = UsageEvents.Event()
                var candidate: String? = null
                var latest = 0L
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                        event.timeStamp > latest) {
                        latest = event.timeStamp
                        candidate = event.packageName
                    }
                }
                if (candidate != null) return candidate
            }
        } catch (e: Exception) {
        }

        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val task = am?.getRunningTasks(1)?.firstOrNull()
            return task?.baseActivity?.packageName
        } catch (e: Exception) {
            return null
        }
    }

    private fun applyForPackage(packageName: String) {
        if (!PerAppProfileStore.isEnabled(this)) return

        val mapped = PerAppProfileStore.getProfile(this, packageName)
        if (mapped != null) {
            FileUtils.writeLine(THERMAL_PROFILE_PATH, mapped)
            overrideActive = true
        } else if (overrideActive) {
            restoreBaseProfile()
        } else {
            val current = FileUtils.readLineInt(THERMAL_PROFILE_PATH)
            if (current != 0 && current != PerAppProfileStore.baseProfile(this, current)) {
                PerAppProfileStore.setBaseProfile(this, current)
            }
        }
    }

    private fun restoreBaseProfile() {
        val base = PerAppProfileStore.baseProfile(this, THERMAL_PROFILE_DEFAULT)
        FileUtils.writeLine(THERMAL_PROFILE_PATH, base)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString(PREF_THERMAL_PROFILE, base.toString())
            .apply()
        overrideActive = false
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1500L
        private const val EVENT_WINDOW_MS = 15_000L

        fun start(context: Context) {
            try {
                context.startServiceAsUser(
                    Intent(context, PerAppProfileService::class.java),
                    UserHandle.CURRENT)
            } catch (e: Exception) {
                android.util.Log.w("PerAppProfileService", "start failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PerAppProfileService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("PerAppProfileService", "stop failed", e)
            }
        }
    }
}
