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

package com.android.server.wm;

import android.provider.Settings;

/**
 * Syncs ACCELEROMETER_ROTATION and DEVICE_STATE_ROTATION_LOCK setting to consistent values.
 * <ul>
 * <li>On device state change: Reads value of DEVICE_STATE_ROTATION_LOCK for new device state and
 * writes into ACCELEROMETER_ROTATION.</li>
 * <li>On ACCELEROMETER_ROTATION setting change: Write updated ACCELEROMETER_ROTATION value into
 * DEVICE_STATE_ROTATION_LOCK setting for current device state.</li>
 * <li>On DEVICE_STATE_ROTATION_LOCK setting change: If the key for the changed value matches
 * current device state, write updated auto rotate value to ACCELEROMETER_ROTATION.</li>
 * </ul>
 *
 * @see Settings.System#ACCELEROMETER_ROTATION
 * @see Settings.Secure#DEVICE_STATE_ROTATION_LOCK
 */

public class DeviceStateAutoRotateSettingController {
    // TODO(b/350946537) Refactor implementation
    public DeviceStateAutoRotateSettingController() {
    }
}
