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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class TurboChargingService extends Service {
    private static final String TAG = "TurboCharging";

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                disableBypassIfActive();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Starting");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (ChargeController.PREF_TURBO_ENABLED.equals(key)
                        || ChargeController.PREF_TURBO_CURRENT.equals(key)) {
                    ChargeController.apply(TurboChargingService.this);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        registerReceiver(mPowerReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));

        if (!isPluggedIn()) {
            disableBypassIfActive();
        }
        ChargeController.apply(this);
    }

    private void disableBypassIfActive() {
        if (ChargeController.isBypassEnabled()) {
            Log.i(TAG, "Power disconnected, disabling bypass charging");
            ChargeController.setBypass(this, false);
        }
    }

    private boolean isPluggedIn() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        unregisterReceiver(mPowerReceiver);
    }
}
