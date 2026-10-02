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

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BypassChargingTileService extends TileService {
    private static final String TAG = "BypassChargingTile";
    private static final String BYPASS_CHARGING_FILE = "/proc/mtk_battery_cmd/current_cmd";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        writeBypassChargingNode(!readBypassChargingNode());
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!new File(BYPASS_CHARGING_FILE).exists()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(readBypassChargingNode() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
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

    private void writeBypassChargingNode(boolean enabled) {
        String value = enabled ? "0 1" : "0 0";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BYPASS_CHARGING_FILE))) {
            writer.write(value);
            Log.i(TAG, "Updated Bypass Charge to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update Bypass Charge", e);
        }
    }
}
