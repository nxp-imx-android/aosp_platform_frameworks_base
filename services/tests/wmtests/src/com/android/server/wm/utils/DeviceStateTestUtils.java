/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wm.utils;

import android.hardware.devicestate.DeviceState;

/**
 * Utility class for creating {@link DeviceState} objects for testing.
 */
public final class DeviceStateTestUtils {
    private DeviceStateTestUtils() {
    }

    // TODO: Update these with the correct properties
    public static final DeviceState UNKNOWN = new DeviceState(
            new DeviceState.Configuration.Builder(0, "UNKNOWN").build());
    public static final DeviceState OPEN = new DeviceState(
            new DeviceState.Configuration.Builder(1, "OPEN").build());
    public static final DeviceState FOLDED = new DeviceState(
            new DeviceState.Configuration.Builder(2, "FOLDED").build());
    public static final DeviceState HALF_FOLDED = new DeviceState(
            new DeviceState.Configuration.Builder(3, "HALF_FOLDED").build());
    public static final DeviceState REAR = new DeviceState(
            new DeviceState.Configuration.Builder(4, "REAR").build());
    public static final DeviceState CONCURRENT = new DeviceState(
            new DeviceState.Configuration.Builder(5, "CONCURRENT").build());
}
