/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.xiaomi.settings.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class PerAppThermalActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false

        setContentView(R.layout.activity_per_app_thermal)

        val root = findViewById<View>(R.id.perapp_root)
        val header = findViewById<View>(R.id.perapp_header)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            header.setPadding(
                header.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(R.dimen.m3_space_m),
                header.paddingRight,
                header.paddingBottom)
            root.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        if (fragmentManager.findFragmentById(R.id.perapp_content) == null) {
            fragmentManager
                .beginTransaction()
                .replace(R.id.perapp_content, PerAppThermalFragment())
                .commit()
        }
    }
}
