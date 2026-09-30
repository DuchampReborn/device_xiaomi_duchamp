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

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.xiaomi.settings.R;

public class TurboChargingFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "TurboChargingFragment";
    private static final String CHARGE_CURRENT_FILE = "/sys/class/power_supply/battery/constant_charge_current";
    private static final String BYPASS_CHARGING_FILE = "/proc/mtk_battery_cmd/current_cmd";
    private static final String PREF_TURBO_ENABLED = "turbo_enable";
    private static final String PREF_TURBO_CURRENT = "turbo_current";
    private static final String PREF_BYPASS_CHARGING = "bypass_charging_enable";

    private SwitchPreferenceCompat mTurboEnabled;
    private ListPreference mTurboCurrent;
    private SwitchPreferenceCompat mBypassChargingEnabled;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_panel, rootKey);

        mTurboEnabled = (SwitchPreferenceCompat) findPreference(PREF_TURBO_ENABLED);
        mTurboEnabled.setOnPreferenceChangeListener(this);

        mTurboCurrent = (ListPreference) findPreference(PREF_TURBO_CURRENT);
        mTurboCurrent.setOnPreferenceChangeListener(this);
        mTurboCurrent.setEnabled(mTurboEnabled.isChecked());

        mBypassChargingEnabled = (SwitchPreferenceCompat) findPreference(PREF_BYPASS_CHARGING);
        mBypassChargingEnabled.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncBypassChargingToggle();
    }

    private void syncBypassChargingToggle() {
        boolean nodeValue = readBypassChargingNode();
        Log.i(TAG, "syncBypassChargingToggle: node=" + nodeValue);

        mBypassChargingEnabled.setOnPreferenceChangeListener(null);
        mBypassChargingEnabled.setChecked(nodeValue);
        mBypassChargingEnabled.setOnPreferenceChangeListener(this);
    }

    private boolean readBypassChargingNode() {
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

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mTurboEnabled) {
            boolean enabled = (boolean) newValue;
            mTurboCurrent.setEnabled(enabled);
            return true;
        } else if (preference == mTurboCurrent) {
            String value = (String) newValue;
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putString(PREF_TURBO_CURRENT, value)
                    .apply();
            return true;
        } else if (preference == mBypassChargingEnabled) {
            boolean enabled = (boolean) newValue;
            updateBypassCharging(enabled);
            return true;
        }
        return false;
    }

    private void updateChargeCurrent() {
        boolean turboEnabled = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(PREF_TURBO_ENABLED, false);
        Log.i(TAG, "isTurbo=" + turboEnabled);
        String defaultValue = "21000000";
        if (turboEnabled) {
            String currentValue = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(PREF_TURBO_CURRENT, "22000000");
            Log.i(TAG, "currentValue=" + currentValue);
            writeChargeCurrent(currentValue);
        } else {
            writeChargeCurrent(defaultValue);
        }
    }

    private void writeChargeCurrent(String value) {
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

    private void updateBypassCharging(boolean enabled) {
        String value = enabled ? "0 1" : "0 0";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BYPASS_CHARGING_FILE))) {
            writer.write(value);
            Log.i(TAG, "Updated Bypass Charge to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update Bypass Charge", e);
        }
    }
}