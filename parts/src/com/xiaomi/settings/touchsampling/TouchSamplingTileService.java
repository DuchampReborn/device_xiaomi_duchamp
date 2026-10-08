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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.xiaomi.settings.R;

public class TouchSamplingTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        SharedPreferences prefs = getSharedPreferences(
                TouchSamplingFragment.SHARED_HTSR, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(TouchSamplingFragment.PREF_HTSR_STATE, false);
        prefs.edit().putBoolean(TouchSamplingFragment.PREF_HTSR_STATE, !enabled).apply();

        boolean main = !enabled;
        boolean auto = prefs.getBoolean(TouchSamplingFragment.PREF_HTSR_AUTO, true);
        Intent serviceIntent = new Intent(this, TouchSamplingService.class);
        if (main || auto) {
            startService(serviceIntent);
        } else {
            stopService(serviceIntent);
        }
        updateTileState();
    }

    private void updateTileState() {
        SharedPreferences prefs = getSharedPreferences(
                TouchSamplingFragment.SHARED_HTSR, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(TouchSamplingFragment.PREF_HTSR_STATE, false);
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.touch_sampling_tile_label));
            tile.setSubtitle(getString(enabled ? R.string.tile_on : R.string.tile_off));
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_touch_sampling_tile));
            tile.updateTile();
        }
    }
}
