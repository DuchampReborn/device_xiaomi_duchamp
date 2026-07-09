/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;

import androidx.preference.PreferenceManager;

import com.xiaomi.settings.display.ColorService;
import com.xiaomi.settings.thermal.ThermalProfileFragment;
import com.xiaomi.settings.turbocharging.TurboChargingService;
import com.xiaomi.settings.utils.FileUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "XiaomiParts";
    private static final boolean DEBUG = true;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (DEBUG) Log.i(TAG, "Received intent: " + intent.getAction());
        switch (intent.getAction()) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                handleLockedBootCompleted(context);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                handleBootCompleted(context);
                break;
        }
    }

    private void handleLockedBootCompleted(Context context) {
        if (DEBUG) Log.i(TAG, "Handling locked boot completed.");
        try {
            // Start necessary services
            startServices(context);

            // Override HDR types
            overrideHdrTypes(context);
        } catch (Exception e) {
            Log.e(TAG, "Error during locked boot completed processing", e);
        }
    }

    private void handleBootCompleted(Context context) {
        if (DEBUG) Log.i(TAG, "Handling boot completed.");
        // Add additional boot-completed actions if needed
    }

    private void startServices(Context context) {
        if (DEBUG) Log.i(TAG, "Starting services...");

        // Start Color Mode Service
        context.startServiceAsUser(new Intent(context, ColorService.class), UserHandle.CURRENT);

        // Start TurboChargingService
        Intent turboChargingIntent = new Intent(context, TurboChargingService.class);
        context.startService(turboChargingIntent);

        // Restore thermal profile from SharedPreferences
        restoreThermalProfile(context);

        // Re-start the auto mode service if it was enabled before reboot
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ThermalProfileFragment.PREF_AUTO_MODE, false)) {
            context.startService(new Intent(context,
                    com.xiaomi.settings.thermal.ThermalAutoModeService.class));
        }
    }

    private void restoreThermalProfile(Context context) {
        String storedValue = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(ThermalProfileFragment.PREF_THERMAL_PROFILE,
                        String.valueOf(ThermalProfileFragment.THERMAL_PROFILE_DEFAULT));
        int profile;
        try {
            profile = Integer.parseInt(storedValue);
        } catch (NumberFormatException e) {
            profile = ThermalProfileFragment.THERMAL_PROFILE_DEFAULT;
        }
        FileUtils.writeLine(ThermalProfileFragment.THERMAL_PROFILE_PATH, profile);
        if (DEBUG) Log.i(TAG, "Restored thermal profile: " + profile);
    }

    private void overrideHdrTypes(Context context) {
        try {
            final DisplayManager dm = context.getSystemService(DisplayManager.class);
            if (dm != null) {
                dm.overrideHdrTypes(Display.DEFAULT_DISPLAY, new int[]{
                        HdrCapabilities.HDR_TYPE_HDR10,
                        HdrCapabilities.HDR_TYPE_HLG,
                        HdrCapabilities.HDR_TYPE_HDR10_PLUS
                });
                if (DEBUG) Log.i(TAG, "HDR types overridden successfully.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error overriding HDR types", e);
        }
    }

}