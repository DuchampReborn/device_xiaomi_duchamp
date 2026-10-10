/*
 * Copyright (C) 2025 LineageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaomi.settings.turbocharging;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Single owner of constant_charge_current and the bypass charging node.
 *
 * desired current = bypass ? 0 : (turbo enabled ? turbo_current : DEFAULT_CURRENT)
 *
 * Because the desired value is always recomputed from prefs, leaving bypass
 * restores whatever turbo/default dictates, with no snapshot to go stale.
 */
public final class ChargeController {
    private static final String TAG = "ChargeController";

    public static final String CHARGE_CURRENT_FILE =
            "/sys/class/power_supply/battery/constant_charge_current";
    public static final String BYPASS_CHARGING_FILE = "/proc/mtk_battery_cmd/current_cmd";

    public static final String PREF_TURBO_ENABLED = "turbo_enable";
    public static final String PREF_TURBO_CURRENT = "turbo_current";

    public static final String DEFAULT_CURRENT = "21000000";
    public static final String DEFAULT_TURBO_CURRENT = "22000000";
    public static final String BYPASS_CURRENT = "0";

    private ChargeController() {}

    public static boolean isBypassSupported() {
        return new File(BYPASS_CHARGING_FILE).exists();
    }

    /** Reads the bypass node, which is the source of truth for bypass state. */
    public static synchronized boolean isBypassEnabled() {
        try (BufferedReader reader = new BufferedReader(new FileReader(BYPASS_CHARGING_FILE))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.trim().split("\\s+");
                return parts.length > 1 && parts[1].equals("1");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read Bypass Charging node", e);
        }
        return false;
    }

    /** Toggles bypass and immediately re-applies the charge current. */
    public static synchronized void setBypass(Context context, boolean enabled) {
        String value = enabled ? "0 1" : "0 0";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BYPASS_CHARGING_FILE))) {
            writer.write(value);
            Log.i(TAG, "Updated Bypass Charge to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update Bypass Charge", e);
        }
        apply(context);
    }

    /** Computes the desired constant_charge_current and writes it. */
    public static synchronized void apply(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String value;
        if (isBypassEnabled()) {
            value = BYPASS_CURRENT;
        } else if (prefs.getBoolean(PREF_TURBO_ENABLED, false)) {
            value = prefs.getString(PREF_TURBO_CURRENT, DEFAULT_TURBO_CURRENT);
        } else {
            value = DEFAULT_CURRENT;
        }
        writeChargeCurrent(value);
    }

    private static void writeChargeCurrent(String value) {
        try {
            Integer.parseInt(value);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(CHARGE_CURRENT_FILE))) {
                writer.write(value);
                Log.i(TAG, "Updated Charging current to " + value);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid charge current value: " + value, e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update charge current", e);
        }
    }
}
