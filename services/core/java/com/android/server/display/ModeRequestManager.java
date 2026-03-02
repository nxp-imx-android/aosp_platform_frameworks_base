/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.display;

import android.annotation.Nullable;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;

import com.android.graphics.surfaceflinger.flags.Flags;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Manages the state of display mode change requests as they propagate through the system.
 */
public class ModeRequestManager {
    public enum RequestStatus {
        IDLE,
        WAITING_FOR_DEVICE_INFO,
        DEVICE_INFO_CREATED,
        WAITING_FOR_MODE_SPECS,
        MODE_SPECS_SET
    }
    private final Object mLock = new Object();
    static final String TAG = "ModeRequestManager";

    // Maps logical displayId to the current state of its mode request.
    @GuardedBy("mLock")
    private final SparseArray<RequestStatus> mStates = new SparseArray<>();

    // Modes requested per displayId
    @GuardedBy("mLock")
    private final SparseArray<Display.Mode> mModes = new SparseArray<>();

    private final boolean mEnabled = Flags.modesetMultiDisplay();

    /**
     * Stores the initial statuses for the requests, their displayIds, and requested modes
     * @param requests The requests to store and follow the status of
     * @return true if all the requests were successfully stored, false otherwise
     */
    public boolean onUserPreferredModesRequestedLocked(UserPreferredModeRequest[] requests) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            for (UserPreferredModeRequest request : requests) {
                if (request.mDisplayId == Display.INVALID_DISPLAY) {
                    throw new IllegalArgumentException("Global display mode is not supported.");
                }
                if (isRequestInProgress(request.mDisplayId)) {
                    Slog.w(TAG, "Mode request already in progress for display "
                            + request.mDisplayId);
                    return false;
                }
                mStates.put(request.mDisplayId, RequestStatus.IDLE);
                mModes.put(request.mDisplayId, request.mMode);
            }
            return true;
        }
    }

    /**
     * Clears all requests.
     */
    public void clearRequests() {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            mStates.clear();
            mModes.clear();
        }
    }

    /**
     * Updates the status of a display mode request after the device info creation.
     * @param displayId The displayId of the request
     * @param displayInfo The display info containing the userPreferredModeId
     */
    public void infoUpdated(int displayId, DisplayInfo displayInfo) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            Display.Mode requestedMode = mModes.get(displayId);
            int requestedModeId = requestedMode == null ? Display.Mode.INVALID_MODE_ID
                    : requestedMode.getModeId();
            if (displayInfo != null && requestedModeId == displayInfo.userPreferredModeId) {
                updateStatus(displayId, RequestStatus.DEVICE_INFO_CREATED);
            } else {
                Slog.w(TAG, "Mismatch between info and expected mode id. Clearing the"
                        + " requests");
            }
        }
    }

    /**
     * Updates the status of a display mode request after the votes are set.
     */
    public void votesReady(int displayId, int userPreferredModeId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            Display.Mode requestedMode = mModes.get(displayId);
            int requestedModeId = requestedMode == null ? Display.Mode.INVALID_MODE_ID
                    : requestedMode.getModeId();
            if (requestedModeId != userPreferredModeId) {
                return;
            }
            updateStatus(displayId, RequestStatus.WAITING_FOR_MODE_SPECS);
        }
    }

    /**
     * Updates the status of a display mode request after the mode specs are set. This is the final
     * state.
     * @param displayId The displayId of the request
     */
    void onSpecsSet(int displayId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            if (getStatus(displayId) == RequestStatus.WAITING_FOR_MODE_SPECS) {
                updateStatus(displayId, RequestStatus.MODE_SPECS_SET);
            }
        }
    }

    void waitingForDeviceInfo(int displayId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            if (getStatus(displayId) == RequestStatus.IDLE) {
                updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);
            }
        }
    }

    void storeMode(int displayId, Display.Mode mode) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            mModes.put(displayId, mode);
        }
    }

    /**
     * Updates the status for a specific display.
     */
    @VisibleForTesting
    void updateStatus(int displayId, RequestStatus newStatus) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            RequestStatus oldStatus = mStates.get(displayId);

            if (oldStatus == null) {
                if (newStatus == RequestStatus.IDLE) {
                    mStates.put(displayId, newStatus);
                }
            } else {
                if (oldStatus.ordinal() + 1 == newStatus.ordinal()) {
                    mStates.put(displayId, newStatus);
                }
            }
        }
    }

    /**
     * Returns the current status of a display mode request.
     */
    RequestStatus getStatus(int displayId) {
        if (isDisabled()) {
            return null;
        }
        synchronized (mLock) {
            RequestStatus status = mStates.get(displayId);
            return status != null ? status : RequestStatus.IDLE;
        }
    }

    /**
     * Returns true if there is an active request for the given display.
     */
    boolean isRequestInProgress(int displayId) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            return getStatus(displayId) != RequestStatus.IDLE;
        }
    }

    /**
     * Returns true if there are any active requests.
     */
    boolean hasActiveRequests() {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            return mStates.size() > 0;
        }
    }

    /**
     * Checks if all displays in a batch have reached a specific status.
     */
    boolean allDisplaysReachedStatus(RequestStatus expectedStatus) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            if (!hasActiveRequests()) {
                return false;
            }
            for (int i = 0; i < mStates.size(); i++) {
                RequestStatus actualStatus = mStates.valueAt(i);
                if (expectedStatus != actualStatus) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean isDisabled() {
        return !mEnabled;
    }

    static class UserPreferredModeRequest{
        int mDisplayId;
        @Nullable Display.Mode mMode;
        boolean mStoreMode;

        UserPreferredModeRequest(int displayId, @Nullable Display.Mode mode, boolean storeMode) {
            mDisplayId = displayId;
            mMode = mode;
            mStoreMode = storeMode;
        }
    }
}
