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

package com.android.server.am;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.IntArray;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.utils.Slogf;

import java.util.Arrays;

/**
 * A multi-user-aware CoreSettingsObserver that manages core settings on a per-user basis.
 *
 * <p>This class is instantiated when the {@code core_settings_multi_user} feature flag is enabled.
 */
final class CoreSettingsObserverMultiUser extends CoreSettingsObserver {
    private static final String TAG = CoreSettingsObserverMultiUser.class.getSimpleName();

    /**
     * Holds the core settings for each running user. The key is the user ID.
     * This ensures that settings for one user do not leak to another.
     */
    @GuardedBy("mLock")
    private final SparseArray<Bundle> mCoreSettingsPerUser = new SparseArray<>();

    protected CoreSettingsObserverMultiUser(ActivityManagerService activityManagerService) {
        super(activityManagerService, /* initialize */ false);

        beginObserveCoreSettings(/* allUsers */ true);
        sendCoreSettings();
    }

    @Override
    public Bundle getCoreSettings(@UserIdInt int userId) {
        synchronized (mLock) {
            Bundle settings = mCoreSettingsPerUser.get(userId);
            if (settings == null) {
                IntArray currentUsers = new IntArray(mCoreSettingsPerUser.size());
                for (int i = 0; i < mCoreSettingsPerUser.size(); i++) {
                    currentUsers.add(mCoreSettingsPerUser.keyAt(i));
                }
                Slogf.w(TAG, "No core settings found for user %d. Current users: %s",
                        userId, currentUsers);
                return Bundle.EMPTY;
            }
            return settings.deepCopy();
        }
    }

    /**
     * {@inheritDoc}
     * This triggers a refresh of the core settings for all currently running users to ensure the
     * new user's settings are populated and sent to the relevant processes.
     */
    @Override
    public void onUserStarting(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserStarting %d", userId);
        }
        synchronized (mLock) {
            if (mCoreSettingsPerUser.contains(userId)) {
                // The boot process commonly calls this method for the system user,
                // at which point this class has already been initialized for the same user.
                if (DEBUG) {
                    Slogf.d(TAG, "Core settings for user %d already exist.", userId);
                }
                return;
            }
        }
        sendCoreSettings();
    }

    /**
     * {@inheritDoc}
     * This removes the stopped user's settings from the cache.
     */
    @Override
    public void onUserStopping(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserStopping %d", userId);
        }
        synchronized (mLock) {
            mCoreSettingsPerUser.remove(userId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void sendCoreSettings() {
        Context context = mActivityManagerService.mContext;
        int[] runningUserIds = mActivityManagerService.getRunningUserIds();
        if (DEBUG) {
            Slogf.d(TAG, "sendCoreSettings for users: %s", Arrays.toString(runningUserIds));
        }

        // Create a temporary bundle to store the settings that will be sent.
        SparseArray<Bundle> settingsToSendPerUser = new SparseArray<>(runningUserIds.length);

        // Global settings and device config values do not vary across users or devices, so we can
        // populate them once.
        Bundle globalSettingsBundle = new Bundle(sGlobalSettingToTypeMap.size());
        populateSettings(context, UserHandle.USER_SYSTEM, globalSettingsBundle,
                sGlobalSettingToTypeMap);
        Bundle deviceConfigBundle = new Bundle(sDeviceConfigEntries.size());
        populateSettingsFromDeviceConfig(deviceConfigBundle);

        for (int userId : runningUserIds) {
            Bundle userSettingsBundle;
            if (android.companion.virtualdevice.flags.Flags.deviceAwareSettingsOverride()) {
                IntArray deviceIds = getVirtualDeviceIds();
                deviceIds.add(Context.DEVICE_ID_DEFAULT);
                userSettingsBundle = new Bundle(deviceIds.size());

                for (int i = 0; i < deviceIds.size(); i++) {
                    int deviceId = deviceIds.get(i);
                    Context deviceContext;
                    if (deviceId == Context.DEVICE_ID_DEFAULT) {
                        deviceContext = context;
                    } else {
                        try {
                            deviceContext = context.createDeviceContext(deviceId);
                        } catch (IllegalArgumentException e) {
                            Slogf.e(TAG, e, "Exception during Context#createDeviceContext "
                                    + "for deviceId: %d", deviceId);
                            continue;
                        }
                    }

                    if (DEBUG) {
                        Slogf.d(TAG, "Populating settings for userId: %d, deviceId: %d",
                                userId, deviceId);
                    }
                    Bundle deviceBundle = new Bundle();
                    populateSettings(deviceContext, userId, deviceBundle, sSecureSettingToTypeMap);
                    populateSettings(deviceContext, userId, deviceBundle, sSystemSettingToTypeMap);

                    // Copy global settings and device config values.
                    deviceBundle.putAll(globalSettingsBundle);
                    deviceBundle.putAll(deviceConfigBundle);

                    userSettingsBundle.putBundle(String.valueOf(deviceId), deviceBundle);
                }
            } else {
                if (DEBUG) {
                    Slogf.d(TAG, "Populating settings for userId: %d, default device", userId);
                }
                // For non-device-aware case, populate all settings into the single bundle.
                userSettingsBundle = new Bundle();
                populateSettings(context, userId, userSettingsBundle, sSecureSettingToTypeMap);
                populateSettings(context, userId, userSettingsBundle, sSystemSettingToTypeMap);
                userSettingsBundle.putAll(globalSettingsBundle);
                userSettingsBundle.putAll(deviceConfigBundle);
            }
            settingsToSendPerUser.put(userId, userSettingsBundle);
        }

        synchronized (mLock) {
            mCoreSettingsPerUser.clear();
            for (int i = 0; i < settingsToSendPerUser.size(); i++) {
                mCoreSettingsPerUser.put(settingsToSendPerUser.keyAt(i),
                        settingsToSendPerUser.valueAt(i));
            }
        }

        mActivityManagerService.onCoreSettingsChange(settingsToSendPerUser);
    }
}
