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

package com.android.server.companion.datatransfer.continuity.settings;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffFeatureStateListener;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.Objects;

/** Manages settings for Handoff across all users on a device. */
public class HandoffSettingsManager {

    private static final String TAG = HandoffSettingsManager.class.getSimpleName();

    private final HandoffPreferenceStore mHandoffPreferenceStore;
    private final HandoffPolicyManager mHandoffPolicyManager;

    @GuardedBy("mHandoffFeatureStateListeners")
    private final RemoteCallbackList<IHandoffFeatureStateListener> mHandoffFeatureStateListeners =
            new RemoteCallbackList<>();

    public HandoffSettingsManager(
            @NonNull HandoffPreferenceStore handoffPreferenceStore,
            @NonNull HandoffPolicyManager handoffPolicyManager) {
        mHandoffPreferenceStore = Objects.requireNonNull(handoffPreferenceStore);
        mHandoffPolicyManager = handoffPolicyManager;
    }

    /**
     * Returns whether handoff is active for the given user (available and enabled).
     *
     * @param userId The user ID of the user to check.
     * @return True if handoff is active for the user, false otherwise.
     */
    public boolean isHandoffActiveForUser(int userId) {
        if (getHandoffAvailabilityForUser(userId)
                != TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE) {
            return false;
        }
        return mHandoffPreferenceStore.isHandoffEnabledForUser(userId);
    }

    /**
     * Sets whether handoff is enabled for the given user. Handoff availability has precedence over
     * this setting.
     *
     * @param userId The user ID of the user to set.
     * @param enabled True if handoff is enabled, false otherwise.
     */
    public void setHandoffEnabledForUser(int userId, boolean enabled) {
        mHandoffPreferenceStore.setHandoffEnabledForUser(userId, enabled);
        notifyHandoffFeatureStateChanged(userId);
    }

    public void registerHandoffFeatureStateListener(
            int userId, @NonNull IHandoffFeatureStateListener listener) {
        synchronized (mHandoffFeatureStateListeners) {
            mHandoffFeatureStateListeners.register(Objects.requireNonNull(listener), userId);
            try {
                listener.onHandoffFeatureStateChanged(
                        getHandoffAvailabilityForUser(userId),
                        mHandoffPreferenceStore.isHandoffEnabledForUser(userId));
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to notify handoff feature state change", e);
            }
        }
    }

    public void unregisterHandoffFeatureStateListener(
            int userId, @NonNull IHandoffFeatureStateListener listener) {
        synchronized (mHandoffFeatureStateListeners) {
            mHandoffFeatureStateListeners.unregister(Objects.requireNonNull(listener));
        }
    }

    private int getHandoffAvailabilityForUser(int userId) {
        if (!mHandoffPolicyManager.isHandoffAllowedForUser(userId)) {
            return TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_DISABLED_BY_POLICY;
        }

        return TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE;
    }

    private void notifyHandoffFeatureStateChanged(int userId) {
        synchronized (mHandoffFeatureStateListeners) {
            mHandoffFeatureStateListeners.broadcast(
                    (listener, token) -> {
                        if ((int) token == userId) {
                            try {
                                listener.onHandoffFeatureStateChanged(
                                        getHandoffAvailabilityForUser(userId),
                                        mHandoffPreferenceStore.isHandoffEnabledForUser(userId));
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Failed to notify handoff feature state change", e);
                            }
                        }
                    });
        }
    }
}
