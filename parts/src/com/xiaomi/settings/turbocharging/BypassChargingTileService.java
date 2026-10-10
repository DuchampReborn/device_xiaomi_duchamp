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

public class BypassChargingTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        ChargeController.setBypass(this, !ChargeController.isBypassEnabled());
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!ChargeController.isBypassSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(ChargeController.isBypassEnabled()
                    ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
