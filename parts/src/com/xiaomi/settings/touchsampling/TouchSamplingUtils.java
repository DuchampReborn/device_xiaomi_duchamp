/*
 * Copyright (C) 2024 The LineageOS Project
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

public final class TouchSamplingUtils {
    public static final String HTSR_FILE =
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";
    public static final String SCONFIG_FILE =
            "/sys/class/thermal/thermal_message/sconfig";

    private TouchSamplingUtils() {
    }
}
