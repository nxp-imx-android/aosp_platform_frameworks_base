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
package com.android.settingslib.devicestate;

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.R;
import com.android.internal.view.RotationPolicy;
import com.android.window.flags.Flags;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link DeviceStateAutoRotateSettingManager}. This implementation is a part of
 * refactoring, it should be used when
 * {@link Flags#FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR}
 * is enabled.
 */
public class DeviceStateAutoRotateSettingManagerImpl implements
        DeviceStateAutoRotateSettingManager {
    // TODO: b/397928958 rename the fields and apis from rotationLock to autoRotate.

    private static final String TAG = "DSAutoRotateMngr";
    private static final String SEPARATOR_REGEX = ":";

    private final Set<DeviceStateAutoRotateSettingListener> mSettingListeners = new HashSet<>();
    private final SparseIntArray mFallbackPostureMap = new SparseIntArray();
    private final SparseIntArray mDefaultDeviceStateAutoRotateSetting = new SparseIntArray();
    private final List<SettableDeviceState> mSettableDeviceState = new ArrayList<>();
    private final SecureSettings mSecureSettings;
    private final Handler mMainHandler;
    private final PostureDeviceStateConverter mPostureDeviceStateConverter;

    public DeviceStateAutoRotateSettingManagerImpl(
            Context context,
            Executor backgroundExecutor,
            SecureSettings secureSettings,
            Handler mainHandler,
            PostureDeviceStateConverter postureDeviceStateConverter
    ) {
        mSecureSettings = secureSettings;
        mMainHandler = mainHandler;
        mPostureDeviceStateConverter = postureDeviceStateConverter;

        loadAutoRotateDeviceStates(context);
        final ContentObserver contentObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                notifyListeners();
            }
        };
        backgroundExecutor.execute(() ->
                mSecureSettings.registerContentObserver(
                        DEVICE_STATE_ROTATION_LOCK,
                        /* notifyForDescendants= */false,
                        contentObserver,
                        UserHandle.USER_CURRENT
                )
        );
    }

    @Override
    public void registerListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        mSettingListeners.add(settingListener);
    }

    @Override
    public void unregisterListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        if (!mSettingListeners.remove(settingListener)) {
            Log.w(TAG, "Attempting to unregister a listener hadn't been registered");
        }
    }

    @Override
    @Settings.Secure.DeviceStateRotationLockSetting
    public Integer getRotationLockSetting(int deviceState) {
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        final SparseIntArray deviceStateAutoRotateSetting = getRotationLockSetting();
        final Integer autoRotateSettingValue = extractSettingForDevicePosture(devicePosture,
                deviceStateAutoRotateSetting);

        // If the setting is ignored for this posture, check the fallback posture.
        if (autoRotateSettingValue != null
                && autoRotateSettingValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
            final int fallbackPosture = mFallbackPostureMap.get(devicePosture,
                    DEVICE_STATE_ROTATION_LOCK_IGNORED);
            return extractSettingForDevicePosture(fallbackPosture, deviceStateAutoRotateSetting);
        }

        return autoRotateSettingValue;
    }

    @Override
    public SparseIntArray getRotationLockSetting() {
        final String serializedSetting = mSecureSettings.getStringForUser(
                DEVICE_STATE_ROTATION_LOCK,
                UserHandle.USER_CURRENT);
        if (serializedSetting == null || serializedSetting.isEmpty()) return null;
        final String[] deserializedSettings = serializedSetting.split(SEPARATOR_REGEX);
        if (deserializedSettings.length % 2 != 0) {
            Log.e(TAG, "Invalid format in serializedSetting=" + serializedSetting
                    + "\nOdd number of elements in the list");
            return null;
        }
        final SparseIntArray deviceStateAutoRotateSetting = new SparseIntArray();
        for (int i = 0; i < deserializedSettings.length; i += 2) {
            final int key = Integer.parseInt(deserializedSettings[i]);
            final int value = Integer.parseInt(deserializedSettings[i + 1]);
            if (value < 0 || value > 2) {
                Log.e(TAG, "Invalid format in serializedSetting=" + serializedSetting
                        + "\nInvalid value in pair: key=" + deserializedSettings[i] + ", value="
                        + deserializedSettings[i + 1]);
                return null;
            }
            deviceStateAutoRotateSetting.put(key, value);
        }
        return deviceStateAutoRotateSetting;
    }

    @Override
    public Boolean isRotationLocked(int deviceState) {
        final Integer autoRotateValue = getRotationLockSetting(deviceState);
        return autoRotateValue == null ? null
                : autoRotateValue == DEVICE_STATE_ROTATION_LOCK_LOCKED;
    }

    @Override
    public Boolean isRotationLockedForAllStates() {
        final SparseIntArray deviceStateAutoRotateSetting = getRotationLockSetting();
        if (deviceStateAutoRotateSetting == null) return null;
        for (int i = 0; i < deviceStateAutoRotateSetting.size(); i++) {
            if (deviceStateAutoRotateSetting.valueAt(i) != DEVICE_STATE_ROTATION_LOCK_LOCKED) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public List<SettableDeviceState> getSettableDeviceStates() {
        return mSettableDeviceState;
    }

    @Override
    public void updateSetting(int deviceState, boolean rotationLock) {
        RotationPolicy.requestDeviceStateAutoRotateSettingChange(deviceState, !rotationLock);
    }

    @Override
    public void dump(@NonNull PrintWriter writer, String[] args) {
        IndentingPrintWriter indentingWriter = new IndentingPrintWriter(writer, "  ");
        indentingWriter.println("DeviceStateAutoRotateSettingManagerImpl");
        indentingWriter.increaseIndent();
        indentingWriter.println("fallbackPostureMap: " + mFallbackPostureMap);
        indentingWriter.println("settableDeviceState: " + mSettableDeviceState);
        indentingWriter.decreaseIndent();
    }

    @NonNull
    @Override
    public SparseIntArray getDefaultRotationLockSetting() {
        return mDefaultDeviceStateAutoRotateSetting.clone();
    }

    private void notifyListeners() {
        for (DeviceStateAutoRotateSettingListener listener : mSettingListeners) {
            listener.onSettingsChanged();
        }
    }

    /**
     * Loads the {@link R.array#config_perDeviceStateRotationLockDefaults} array and populates the
     * {@link #mFallbackPostureMap}, {@link #mSettableDeviceState}, and
     * {@link #mDefaultDeviceStateAutoRotateSetting}
     * fields.
     */
    private void loadAutoRotateDeviceStates(Context context) {
        final String[] perDeviceStateAutoRotateDefaults =
                context.getResources().getStringArray(
                        R.array.config_perDeviceStateRotationLockDefaults);
        for (String entry : perDeviceStateAutoRotateDefaults) {
            final PostureEntry parsedEntry = parsePostureEntry(entry);

            final int posture = parsedEntry.posture;
            final int autoRotateValue = parsedEntry.autoRotateValue;
            final Integer fallbackPosture = parsedEntry.fallbackPosture;
            final Integer deviceState = mPostureDeviceStateConverter.postureToDeviceState(posture);

            if (deviceState == null) {
                throw new IllegalStateException("No matching device state for posture: " + posture);
            }
            mSettableDeviceState.add(new SettableDeviceState(deviceState,
                    autoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED));

            if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED
                    && fallbackPosture != null) {
                mFallbackPostureMap.put(posture, fallbackPosture);
            } else if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                throw new IllegalStateException(
                        "Auto rotate setting is IGNORED for posture=" + posture
                                + ", but no fallback-posture defined");
            }
            mDefaultDeviceStateAutoRotateSetting.put(posture, autoRotateValue);
        }
    }

    @NonNull
    private PostureEntry parsePostureEntry(String entry) {
        final String[] values = entry.split(SEPARATOR_REGEX);
        if (values.length < 2 || values.length > 3) { // It should contain 2 or 3 values.
            throw new IllegalStateException("Invalid number of values in entry: " + entry);
        }
        try {
            final int posture = Integer.parseInt(values[0]);
            final int autoRotateValue = Integer.parseInt(values[1]);
            final Integer fallbackPosture = (values.length == 3) ? Integer.parseInt(values[2])
                    : null;

            return new PostureEntry(posture, autoRotateValue, fallbackPosture);

        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid number format in '" + entry + "'" + e.getMessage());
        }
    }

    private Integer extractSettingForDevicePosture(
            int devicePosture,
            SparseIntArray deviceStateAutoRotateSetting
    ) {
        return deviceStateAutoRotateSetting == null ? null : deviceStateAutoRotateSetting.get(
                devicePosture,
                DEVICE_STATE_ROTATION_LOCK_IGNORED);
    }

    private record PostureEntry(int posture, int autoRotateValue, Integer fallbackPosture) {
    }
}
