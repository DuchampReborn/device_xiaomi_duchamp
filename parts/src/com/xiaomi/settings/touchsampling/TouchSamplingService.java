/*
 * Copyright (C) 2025 The LineageOS Project
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

package com.xiaomi.settings.touchsampling;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.xiaomi.settings.R;
import com.xiaomi.settings.utils.FileUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TouchSamplingService extends Service {
    private static final String TAG = "TouchSamplingService";

    private static final int NOTIFICATION_ID = 3;
    private static final String NOTIFICATION_CHANNEL_ID = "touch_sampling_tile_service_channel";

    private BroadcastReceiver mScreenReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener;
    private FileObserver mSconfigObserver;
    private Handler mHandler;
    private Runnable mPollRunnable;
    private NotificationManager mNotificationManager;
    private boolean mLastEffectiveState = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        setupNotificationChannel();

        registerScreenReceiver();
        registerPreferenceChangeListener();

        updateEffectiveStateAndApply();

        mSconfigObserver = new FileObserver(TouchSamplingUtils.SCONFIG_FILE, FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if ((event & FileObserver.MODIFY) != 0) {
                    updateEffectiveStateAndApply();
                }
            }
        };
        mSconfigObserver.startWatching();

        mHandler = new Handler();
        mPollRunnable = new Runnable() {
            @Override
            public void run() {
                updateEffectiveStateAndApply();
                long interval = isScreenOn() ? 5000 : 15000;
                mHandler.postDelayed(this, interval);
            }
        };
        mHandler.post(mPollRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateEffectiveStateAndApply();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mScreenReceiver != null) {
            unregisterReceiver(mScreenReceiver);
        }

        if (mPreferenceChangeListener != null) {
            SharedPreferences sharedPref = getSharedPreferences(
                    TouchSamplingFragment.SHARED_HTSR, Context.MODE_PRIVATE);
            sharedPref.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        }

        if (mSconfigObserver != null) {
            mSconfigObserver.stopWatching();
        }
        if (mHandler != null && mPollRunnable != null) {
            mHandler.removeCallbacks(mPollRunnable);
        }
        applyTouchSamplingRate(0);
        cancelNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerScreenReceiver() {
        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction()) ||
                        Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    updateEffectiveStateAndApply();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenReceiver, filter);
    }

    private void registerPreferenceChangeListener() {
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingFragment.SHARED_HTSR, Context.MODE_PRIVATE);
        mPreferenceChangeListener = (sharedPreferences, key) -> {
            if (TouchSamplingFragment.PREF_HTSR_STATE.equals(key)
                    || TouchSamplingFragment.PREF_HTSR_AUTO.equals(key)) {
                updateEffectiveStateAndApply();
            }
        };
        sharedPref.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    private void updateEffectiveStateAndApply() {
        boolean effectiveState = isTouchSamplingActive(this);
        applyTouchSamplingRate(effectiveState ? 1 : 0);
        if (effectiveState != mLastEffectiveState) {
            if (effectiveState) {
                showNotification();
            } else {
                cancelNotification();
            }
            mLastEffectiveState = effectiveState;
        }
    }

    private void applyTouchSamplingRate(int state) {
        String currentState = FileUtils.readOneLine(TouchSamplingUtils.HTSR_FILE);
        if (currentState == null || !currentState.equals(Integer.toString(state))) {
            FileUtils.writeLine(TouchSamplingUtils.HTSR_FILE, Integer.toString(state));
        }
    }

    private boolean isScreenOn() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return powerManager != null && powerManager.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    private void setupNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.touch_sampling_mode_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setBlockable(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void showNotification() {
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.touch_sampling_mode_title))
                .setContentText(getString(R.string.touch_sampling_mode_notification))
                .setSmallIcon(R.drawable.ic_touch_sampling_tile)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void cancelNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    public static boolean isMainEnabled(Context context) {
        return context.getSharedPreferences(TouchSamplingFragment.SHARED_HTSR,
                Context.MODE_PRIVATE).getBoolean(TouchSamplingFragment.PREF_HTSR_STATE, false);
    }

    public static boolean isAutoEnabled(Context context) {
        return context.getSharedPreferences(TouchSamplingFragment.SHARED_HTSR,
                Context.MODE_PRIVATE).getBoolean(TouchSamplingFragment.PREF_HTSR_AUTO, true);
    }

    public static Set<String> getAutoApps(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(TouchSamplingFragment.PREF_HTSR_AUTO_APPS, new HashSet<>());
    }

    public static boolean isTouchSamplingActive(Context context) {
        if (isMainEnabled(context)) {
            return true;
        }
        if (isAutoEnabled(context)) {
            String foreground = getForegroundApp(context);
            return foreground != null && getAutoApps(context).contains(foreground);
        }
        return false;
    }

    private static String getForegroundApp(Context context) {
        ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                return tasks.get(0).topActivity.getPackageName();
            }
        }
        return null;
    }
}
