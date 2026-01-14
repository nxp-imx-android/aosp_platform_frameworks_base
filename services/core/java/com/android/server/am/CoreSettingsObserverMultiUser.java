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
 * <p>This class was created to fix a bug in {@link CoreSettingsObserver} only when a flag is
 * enabled, but the flag has been removed already.
 *
 * TODO(b/473966894): Merge this class into CoreSettingsObserver once the flag removal is confirmed
 * to be stable.
 */
final class CoreSettingsObserverMultiUser extends CoreSettingsObserver {
    private static final String TAG = CoreSettingsObserverMultiUser.class.getSimpleName();

    /**
     * Holds the core settings for each running user. The key is the user ID.
     * This ensures that settings for one user do not leak to another.
     */
    @GuardedBy("mLock")
    private final SparseArray<Bundle> mCoreSettingsPerUser = new SparseArray<>();

    @GuardedBy("mLock")
    private final Bundle mGlobalSettingsBundle;
    @GuardedBy("mLock")
    private final Bundle mDeviceConfigBundle;

    protected CoreSettingsObserverMultiUser(ActivityManagerService activityManagerService) {
        super(activityManagerService);
        mGlobalSettingsBundle = new Bundle(sGlobalSettingToTypeMap.size());
        mDeviceConfigBundle = new Bundle(sDeviceConfigEntries.size());
        updateGlobalSettings();

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

    @Override
    public void onChange(boolean selfChange) {
        if (DEBUG) {
            Slogf.d(TAG, "onChange(%b)", selfChange);
        }
        updateGlobalSettings();
        sendCoreSettings();
    }

    /**
     * {@inheritDoc}
     * This triggers a refresh of the core settings for the starting user to ensure the
     * new user's settings are populated and sent to the relevant processes.
     *
     * <p>This method builds and dispatches core settings exclusively for the new user,
     * avoiding redundant updates for already running users. The resulting {@link SparseArray}
     * sent to {@link ActivityManagerService#onCoreSettingsChange} contains settings for only
     * the starting user, which {@link com.android.server.os.ProcessList#updateCoreSettings}
     * will then apply selectively.
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
        Bundle userSettings = buildSettingsForUser(userId);
        synchronized (mLock) {
            mCoreSettingsPerUser.put(userId, userSettings);
        }
        SparseArray<Bundle> settingsToSend = new SparseArray<>(1);
        settingsToSend.put(userId, userSettings);
        mActivityManagerService.onCoreSettingsChange(settingsToSend);
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
     * This will send the latest settings to all running users.
     */
    @Override
    protected void sendCoreSettings() {
        int[] runningUserIds = mActivityManagerService.getRunningUserIds();
        if (DEBUG) {
            Slogf.d(TAG, "sendCoreSettings for users: %s", Arrays.toString(runningUserIds));
        }

        SparseArray<Bundle> settingsToSendPerUser = new SparseArray<>(runningUserIds.length);
        for (int userId : runningUserIds) {
            settingsToSendPerUser.put(userId, buildSettingsForUser(userId));
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

    private void updateGlobalSettings() {
        synchronized (mLock) {
            mGlobalSettingsBundle.clear();
            populateSettings(mActivityManagerService.mContext, UserHandle.USER_SYSTEM,
                    mGlobalSettingsBundle, sGlobalSettingToTypeMap);
            mDeviceConfigBundle.clear();
            populateSettingsFromDeviceConfig(mDeviceConfigBundle);
        }
    }

    /**
     * Builds a Bundle containing all core settings for a specific user.
     *
     * <p>This includes user-specific settings (Secure and System) as well as cached global
     * settings. It also handles device-aware settings by creating a nested structure if needed.
     *
     * @param userId The user to build the settings for.
     * @return A {@link Bundle} containing the core settings.
     */
    private Bundle buildSettingsForUser(@UserIdInt int userId) {
        Context context = mActivityManagerService.mContext;
        Bundle globalSettingsBundle;
        Bundle deviceConfigBundle;
        synchronized (mLock) {
            globalSettingsBundle = new Bundle(mGlobalSettingsBundle);
            deviceConfigBundle = new Bundle(mDeviceConfigBundle);
        }

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
        return userSettingsBundle;
    }
}
