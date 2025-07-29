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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;

public class ComputerControlSessionProcessor {

    private final PackageManager mPackageManager;
    private final VirtualDeviceFactory mVirtualDeviceFactory;

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceFactory virtualDeviceFactory) {
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPackageManager = context.getPackageManager();
    }

    /**
     * Process a new session creation request.
     */
    public IComputerControlSession processNewSession(
            @NonNull IBinder token,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params) {
        // TODO(b/430259551, b/432678191): Async creation of sessions triggering a consent dialog
        // TODO(b/419548594): Limit the number of active sessions
        return new ComputerControlSessionImpl(
                token, params, attributionSource, mPackageManager, mVirtualDeviceFactory);
    }

    /**
     * Interface for creating a virtual device for a computer control session.
     */
    public interface VirtualDeviceFactory {
        /**
         * Creates a new virtual device.
         */
        IVirtualDevice createVirtualDevice(
                IBinder token,
                AttributionSource attributionSource,
                VirtualDeviceParams params,
                IVirtualDeviceActivityListener activityListener);
    }
}
