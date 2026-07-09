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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.UEventObserver;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class TurboChargingService extends Service {
    private static final String TAG = "TurboCharging";
    private static final String CHARGE_CURRENT_FILE = "/sys/class/power_supply/battery/constant_charge_current";
    private static final String USB_ONLINE_FILE = "/sys/class/power_supply/usb/online";
    private static final String PD_VERIFIED_FILE = "/sys/devices/platform/pd-adapter/Charging_Adapter/pd_adapter/usbpd_verifed";

    private UEventObserver mObserver;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    public void onCreate() {
        Log.d(TAG, "Starting");

        // Set up the UEventObserver to monitor charger status
        mObserver = new UEventObserver() {
            @Override
            public void onUEvent(UEvent event) {
                String chargerStatus = event.get("POWER_SUPPLY_ONLINE");
                if (chargerStatus != null) {
                    if (chargerStatus.equals("1")) {
                        // Charger connected - apply settings
                        updateChargeCurrent();
                        updatePdVerified();
                    } else if (chargerStatus.equals("0")) {
                        // Charger disconnected - system resets to 0, we'll reapply on next connect
                        Log.i(TAG, "Charger disconnected, settings will be reapplied on reconnect");
                    }
                }
            }
        };
        mObserver.startObserving("DEVPATH=/sys/class/power_supply/usb");

        // Listen for changes in preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("turbo_enable") || key.equals("turbo_current")) {
                    updateChargeCurrent();  // Update the charge current when preferences change
                } else if (key.equals("pd_verified_enable")) {
                    updatePdVerified();  // Update PD verified when preference changes
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        updateChargeCurrent();  // Initial update
        updatePdVerified();  // Initial PD verified update
    }

    private void updateChargeCurrent() {
        boolean turboEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("turbo_enable", false);
        Log.i(TAG, "isTurbo=" + turboEnabled);
        String defaultValue = "21000000";
        if (turboEnabled) {
            String currentValue = PreferenceManager.getDefaultSharedPreferences(this).getString("turbo_current", "22000000");
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

    private void updatePdVerified() {
        boolean pdVerifiedEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pd_verified_enable", false);
        Log.i(TAG, "isPdVerified=" + pdVerifiedEnabled);
        String value = pdVerifiedEnabled ? "1" : "0";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PD_VERIFIED_FILE))) {
            writer.write(value);
            Log.i(TAG, "Updated PD Verified to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update PD Verified", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the preference listener to avoid memory leaks
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }
}
