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
    private static final String USB_ONLINE_FILE = "/sys/class/power_supply/usb/online";
    private static final String PD_VERIFIED_FILE = "/sys/devices/platform/pd-adapter/Charging_Adapter/pd_adapter/usbpd_verifed";
    private static final String PREF_TURBO_ENABLED = "turbo_enable";
    private static final String PREF_TURBO_CURRENT = "turbo_current";
    private static final String PREF_PD_VERIFIED = "pd_verified_enable";

    private SwitchPreferenceCompat mTurboEnabled;
    private ListPreference mTurboCurrent;
    private SwitchPreferenceCompat mPdVerifiedEnabled;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_panel, rootKey);

        mTurboEnabled = (SwitchPreferenceCompat) findPreference(PREF_TURBO_ENABLED);
        mTurboEnabled.setOnPreferenceChangeListener(this);

        mTurboCurrent = (ListPreference) findPreference(PREF_TURBO_CURRENT);
        mTurboCurrent.setOnPreferenceChangeListener(this);
        mTurboCurrent.setEnabled(mTurboEnabled.isChecked());

        mPdVerifiedEnabled = (SwitchPreferenceCompat) findPreference(PREF_PD_VERIFIED);
        mPdVerifiedEnabled.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncPdVerifiedToggle();
    }

    /**
     * Reads the actual value from the PD verified sysfs node and updates the toggle
     * to reflect it. This handles the case where the charger disconnects and the
     * kernel resets the node to 0 without the UI knowing.
     */
    private void syncPdVerifiedToggle() {
        boolean nodeValue = readPdVerifiedNode();
        Log.i(TAG, "syncPdVerifiedToggle: node=" + nodeValue);

        // If the node was reset (e.g. charger disconnect), also update the stored pref
        // so the service doesn't immediately re-write stale data on next connect.
        boolean prefValue = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(PREF_PD_VERIFIED, false);

        if (nodeValue != prefValue) {
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putBoolean(PREF_PD_VERIFIED, nodeValue)
                    .apply();
        }

        // Update toggle UI without triggering onPreferenceChange
        mPdVerifiedEnabled.setOnPreferenceChangeListener(null);
        mPdVerifiedEnabled.setChecked(nodeValue);
        mPdVerifiedEnabled.setOnPreferenceChangeListener(this);
    }

    private boolean readPdVerifiedNode() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PD_VERIFIED_FILE))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim().equals("1");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read PD Verified node", e);
        }
        return false;
    }

    @Override
       public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mTurboEnabled) {
        boolean enabled = (boolean) newValue;
        mTurboCurrent.setEnabled(enabled);

        // Always update the charge current when the toggle changes
        updateChargeCurrent();

        return true;
          } else if (preference == mTurboCurrent) {
        String value = (String) newValue;
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(PREF_TURBO_CURRENT, value)
                .apply();

        // Update the charge current when the option is changed
        updateChargeCurrent();

            return true;
        } else if (preference == mPdVerifiedEnabled) {
            boolean enabled = (boolean) newValue;
            updatePdVerified(enabled);
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

    private void updatePdVerified(boolean enabled) {
        String value = enabled ? "1" : "0";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PD_VERIFIED_FILE))) {
            writer.write(value);
            Log.i(TAG, "Updated PD Verified to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update PD Verified", e);
        }
    }
}