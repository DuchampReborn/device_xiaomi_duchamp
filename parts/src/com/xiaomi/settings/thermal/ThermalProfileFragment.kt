/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.app.Fragment
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.xiaomi.settings.R
import com.xiaomi.settings.utils.FileUtils

class ThermalProfileFragment : Fragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mSharedPrefs: SharedPreferences

    // Current-mode hero card views
    private lateinit var mHeroCard: View
    private lateinit var mTvProfileName: TextView
    private lateinit var mTvModeLabel: TextView
    private lateinit var mIconChip: View
    private lateinit var mIvCardIcon: ImageView

    // Mode selection card views (Material 3 Expressive card group)
    private class ModeCard(
        val card: View,
        val icon: ImageView,
        val label: TextView,
        val check: ImageView
    )

    private lateinit var mCards: Map<Int, ModeCard>

    // Auto mode
    private lateinit var mSwitchAutoMode: Switch

    // Profile value -> semantic container color (day/night aware resource)
    private val profileContainers = mapOf(
        THERMAL_PROFILE_DEFAULT      to R.color.thermal_mode_default_container,
        THERMAL_PROFILE_MPERFORMANCE to R.color.thermal_mode_performance_container,
        THERMAL_PROFILE_MBATTERY     to R.color.thermal_mode_battery_container,
        THERMAL_PROFILE_MGAME        to R.color.thermal_mode_game_container
    )

    // Profile value -> semantic "on container" color (day/night aware resource)
    private val profileOnColors = mapOf(
        THERMAL_PROFILE_DEFAULT      to R.color.thermal_mode_default_on,
        THERMAL_PROFILE_MPERFORMANCE to R.color.thermal_mode_performance_on,
        THERMAL_PROFILE_MBATTERY     to R.color.thermal_mode_battery_on,
        THERMAL_PROFILE_MGAME        to R.color.thermal_mode_game_on
    )

    // Profile value -> label string res
    private val profileNames = mapOf(
        THERMAL_PROFILE_DEFAULT      to R.string.thermalprofile_default,
        THERMAL_PROFILE_MPERFORMANCE to R.string.thermalprofile_performance,
        THERMAL_PROFILE_MBATTERY     to R.string.thermalprofile_battery,
        THERMAL_PROFILE_MGAME        to R.string.thermalprofile_game
    )

    // Profile value -> card icon drawable res
    private val profileIcons = mapOf(
        THERMAL_PROFILE_DEFAULT      to R.drawable.ic_thermal_default,
        THERMAL_PROFILE_MPERFORMANCE to R.drawable.ic_thermal_performance,
        THERMAL_PROFILE_MBATTERY     to R.drawable.ic_thermal_battery,
        THERMAL_PROFILE_MGAME        to R.drawable.ic_thermal_gaming
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_thermal_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity)

        // Current-mode hero card
        mHeroCard      = view.findViewById(R.id.card_current_mode)
        mTvProfileName = view.findViewById(R.id.tv_current_profile_name)
        mTvModeLabel   = view.findViewById(R.id.tv_current_mode_label)
        mIconChip      = view.findViewById(R.id.icon_chip)
        mIvCardIcon    = view.findViewById(R.id.iv_current_mode_icon)

        // Mode selection cards
        mCards = mapOf(
            THERMAL_PROFILE_DEFAULT to ModeCard(
                view.findViewById(R.id.card_default),
                view.findViewById(R.id.icon_default),
                view.findViewById(R.id.label_default),
                view.findViewById(R.id.check_default)
            ),
            THERMAL_PROFILE_MPERFORMANCE to ModeCard(
                view.findViewById(R.id.card_performance),
                view.findViewById(R.id.icon_performance),
                view.findViewById(R.id.label_performance),
                view.findViewById(R.id.check_performance)
            ),
            THERMAL_PROFILE_MBATTERY to ModeCard(
                view.findViewById(R.id.card_battery),
                view.findViewById(R.id.icon_battery),
                view.findViewById(R.id.label_battery),
                view.findViewById(R.id.check_battery)
            ),
            THERMAL_PROFILE_MGAME to ModeCard(
                view.findViewById(R.id.card_game),
                view.findViewById(R.id.icon_game),
                view.findViewById(R.id.label_game),
                view.findViewById(R.id.check_game)
            )
        )

        // Auto mode
        mSwitchAutoMode = view.findViewById(R.id.switch_auto_mode)
        mSwitchAutoMode.isChecked = mSharedPrefs.getBoolean(PREF_AUTO_MODE, false)
        mSwitchAutoMode.setOnCheckedChangeListener { _, checked ->
            mSharedPrefs.edit().putBoolean(PREF_AUTO_MODE, checked).apply()
            val serviceIntent = Intent(activity, ThermalAutoModeService::class.java)
            if (checked) activity.startService(serviceIntent)
            else         activity.stopService(serviceIntent)
        }

        // Card click listeners
        mCards.forEach { (profile, modeCard) ->
            modeCard.card.setOnClickListener { selectProfile(profile) }
        }

        syncFromSysfs()
    }

    override fun onResume() {
        super.onResume()
        syncFromSysfs()
        mSharedPrefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == PREF_THERMAL_PROFILE) {
            val profile = prefs.getString(PREF_THERMAL_PROFILE, THERMAL_PROFILE_DEFAULT.toString())
                ?.toIntOrNull() ?: THERMAL_PROFILE_DEFAULT
            updateUi(profile)
        }
    }

    private fun selectProfile(profile: Int) {
        FileUtils.writeLine(THERMAL_PROFILE_PATH, profile)
        mSharedPrefs.edit().putString(PREF_THERMAL_PROFILE, profile.toString()).apply()
        updateUi(profile)
    }

    private fun syncFromSysfs() {
        val current = FileUtils.readLineInt(THERMAL_PROFILE_PATH)
        if (mSharedPrefs.getString(PREF_THERMAL_PROFILE, null) != current.toString()) {
            mSharedPrefs.edit().putString(PREF_THERMAL_PROFILE, current.toString()).apply()
        }
        updateUi(current)
    }

    private fun updateUi(activeProfile: Int) {
        val density = resources.displayMetrics.density

        val containerRes = profileContainers[activeProfile]
            ?: R.color.thermal_mode_default_container
        val onColorRes = profileOnColors[activeProfile]
            ?: R.color.thermal_mode_default_on
        val container = ContextCompat.getColor(activity, containerRes)
        val onColor = ContextCompat.getColor(activity, onColorRes)

        // ── Hero card: tonal container fill + on-container typography ──
        val heroRadius = resources.getDimensionPixelSize(R.dimen.m3_corner_hero)
        mHeroCard.background = tonalBackground(container, heroRadius, 0, 0)
        mTvProfileName.text = getString(profileNames[activeProfile] ?: R.string.thermalprofile_unknown)
        mTvProfileName.setTextColor(onColor)
        mTvModeLabel.setTextColor(onColor)
        mTvModeLabel.alpha = 0.75f
        mIvCardIcon.setImageResource(profileIcons[activeProfile] ?: R.drawable.ic_thermal_default)
        mIvCardIcon.imageTintList = ColorStateList.valueOf(onColor)

        // Icon chip: soft scrim circle derived from the on-container color
        mIconChip.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((onColor and 0x00FFFFFF) or ICON_CHIP_SCRIM_ALPHA)
        }

        // ── Mode selection cards ──
        val surfaceContainer = ContextCompat.getColor(activity, R.color.m3_surface_container)
        val outlineVariant = ContextCompat.getColor(activity, R.color.m3_outline_variant)
        val onSurface = ContextCompat.getColor(activity, R.color.m3_on_surface)
        val onSurfaceVariant = ContextCompat.getColor(activity, R.color.m3_on_surface_variant)

        val cardRadius = resources.getDimensionPixelSize(R.dimen.m3_corner_card)
        val selectedStroke = (density * 2).toInt()
        val idleStroke = (density * 1).toInt()

        mCards.forEach { (profile, modeCard) ->
            val selected = profile == activeProfile
            if (selected) {
                modeCard.card.background = tonalBackground(
                    container, cardRadius, onColor, selectedStroke)
                modeCard.icon.imageTintList = ColorStateList.valueOf(onColor)
                modeCard.label.setTextColor(onColor)
                modeCard.check.imageTintList = ColorStateList.valueOf(onColor)
                modeCard.check.visibility = View.VISIBLE
            } else {
                modeCard.card.background = tonalBackground(
                    surfaceContainer, cardRadius, outlineVariant, idleStroke)
                modeCard.icon.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
                modeCard.label.setTextColor(onSurface)
                modeCard.check.visibility = View.GONE
            }
        }
    }

    /**
     * Rounded tonal card background with an optional outline stroke and a
     * bounded ripple on top (Material 3 Expressive card).
     */
    private fun tonalBackground(fill: Int, radiusPx: Int, stroke: Int, strokeWidthPx: Int): Drawable {
        val content = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx.toFloat()
            if (strokeWidthPx > 0) setStroke(strokeWidthPx, stroke)
        }
        val rippleColor = if (strokeWidthPx > 0) stroke else fill
        val ripple = ColorStateList.valueOf((rippleColor and 0x00FFFFFF) or CARD_RIPPLE_ALPHA)
        val mask = GradientDrawable().apply {
            cornerRadius = radiusPx.toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(ripple, content, mask)
    }

    companion object {
        private const val ICON_CHIP_SCRIM_ALPHA = 0x2E000000 // ~18% alpha
        private const val CARD_RIPPLE_ALPHA = 0x33000000     // ~20% alpha

        const val THERMAL_PROFILE_PATH         = "/sys/class/thermal/thermal_message/sconfig"
        const val PREF_THERMAL_PROFILE         = "thermal_profile"
        const val PREF_AUTO_MODE               = "thermal_auto_mode"
        const val THERMAL_PROFILE_DEFAULT      = 50
        const val THERMAL_PROFILE_MPERFORMANCE = 18
        const val THERMAL_PROFILE_MBATTERY     = 700
        const val THERMAL_PROFILE_MGAME        = 19
    }
}
