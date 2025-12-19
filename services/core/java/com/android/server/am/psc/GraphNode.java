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
package com.android.server.am.psc;

import static com.android.server.am.psc.PlatformCompatCache.CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY;

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessState;
import android.content.pm.ServiceInfo.ForegroundServiceType;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.Objects;

/**
 * Represents a process within the process graph. Each node is embedded within a
 * {@link ProcessRecordInternal}.
 *
 * This class stores the calculated importance values such as capabilities and the process state
 * for the process. The calculated values are used by specialized controllers such as
 * {@link CapabilityController} to evaluate the node's outgoing edges, which are service or provider
 * bindings, to determine the importance of the edges' target processes during graph traversal.
 */
@RavenwoodKeepWholeClass
class GraphNode {
    /** A reference to the underlying ProcessRecordInternal. */
    private final @NonNull ProcessRecordInternal mProc;

    GraphNode(@NonNull ProcessRecordInternal app) {
        mProc = Objects.requireNonNull(app);
    }

    // TODO: b/483182189 - Move state getters below to ProcessEdge.
    int getMaxAdj() {
        return mProc.getMaxAdj();
    }

    boolean isProcessRunning() {
        return mProc.isProcessRunning();
    }

    boolean hasActiveInstrumentation() {
        return mProc.hasActiveInstrumentation();
    }

    boolean hasForegroundServices() {
        return mProc.getServices().hasForegroundServices();
    }

    boolean hasNonShortForegroundServices() {
        return mProc.getServices().hasNonShortForegroundServices();
    }

    int getNumberOfRunningServices() {
        return mProc.getServices().numberOfRunningServices();
    }

    /**
     * @param index The index of the running service to check.
     * @return {@code true} if the service at the given index is a foreground service.
     */
    boolean isForegroundService(int index) {
        return getRunningServiceAt(index).isForeground();
    }

    /**
     * @param index The index of the running service to check.
     *              <p>Note: The caller is responsible for ensuring that the service at the given
     *              index is a foreground service (e.g., by calling {@link #isForegroundService})
     *              before calling this method.
     * @return {@code true} if the FGS at the given index is allowed to have while-in-use
     * capabilities.
     */
    boolean isFgsAllowedWiuForCapabilities(int index) {
        return getRunningServiceAt(index).isFgsAllowedWiu_forCapabilities();
    }

    /**
     * @param index The index of the running service to get the FGS type from.
     *              <p>Note: The caller is responsible for ensuring that the service at the given
     *              index is a foreground service (e.g., by calling {@link #isForegroundService})
     *              before calling this method.
     * @return The foreground service type of the service at the given index.
     */
    @ForegroundServiceType
    int getForegroundServiceType(int index) {
        return getRunningServiceAt(index).getForegroundServiceType();
    }

    boolean getCachedCompatChangeCameraMicrophoneCapability() {
        return mProc.getCachedCompatChange(
                CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY);
    }

    @ProcessState
    int getProcState() {
        return mProc.getCurProcState();
    }

    private ServiceRecordInternal getRunningServiceAt(int index) {
        return mProc.getServices().getRunningServiceInternalAt(index);
    }
}
