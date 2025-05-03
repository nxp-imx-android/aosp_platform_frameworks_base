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

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.devicestate.DeviceState;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.PersistedSettingUpdate;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateAccelerometerRotationSetting;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateDeviceState;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateDeviceStateAutoRotateSetting;
import com.android.server.wm.DeviceStateController.DeviceStateEnum;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager;

import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Syncs ACCELEROMETER_ROTATION and DEVICE_STATE_ROTATION_LOCK setting to consistent values.
 * <ul>
 * <li>On device state change: Reads value of DEVICE_STATE_ROTATION_LOCK for new device state and
 * writes it into ACCELEROMETER_ROTATION.</li>
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
    private static final String TAG = "DSAutoRotateCtrl";
    private static final String SEPARATOR_REGEX = ":";
    private static final int ACCELEROMETER_ROTATION_OFF = 0;
    private static final int ACCELEROMETER_ROTATION_ON = 1;
    // TODO(b/413598268): Disable debugging after the
    //  com.android.window.flags.enable_device_state_auto_rotate_setting_refactor flag is rolled-out
    private static final boolean DEBUG = true;
    private static final int MSG_UPDATE_STATE = 1;

    private final Handler mHandler;
    private final Handler mEventHandler;
    private final DeviceStateAutoRotateSettingManager mDeviceStateAutoRotateSettingManager;
    private final ContentResolver mContentResolver;
    private final DeviceStateController mDeviceStateController;

    // TODO(b/413639166): Handle device state being missing until we receive first device state
    //  update
    private int mDeviceState = -1;
    private boolean mAccelerometerSetting;
    private SparseIntArray mDeviceStateAutoRotateSetting;

    public DeviceStateAutoRotateSettingController(
            Context context, Looper looper, Handler handler,
            DeviceStateController deviceStateController,
            DeviceStateAutoRotateSettingManager deviceStateAutoRotateSettingManager) {
        mDeviceStateAutoRotateSettingManager = deviceStateAutoRotateSettingManager;
        mHandler = handler;
        mContentResolver = context.getContentResolver();
        mDeviceStateController = deviceStateController;
        mDeviceStateAutoRotateSetting = getDeviceStateAutoRotateSetting();
        if (mDeviceStateAutoRotateSetting == null) {
            // Map would be null if string value of DEVICE_STATE_ROTATION_LOCK is corrupted.
            mDeviceStateAutoRotateSetting = getDefaultDeviceStateAutoRotateSetting();
        }
        mAccelerometerSetting = getAccelerometerRotationSetting();
        mEventHandler = new Handler(looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                final Event event = (Event) msg.obj;
                final boolean inMemoryStateUpdated = updateInMemoryState(event);

                if (inMemoryStateUpdated) {
                    writeInMemoryStateIntoPersistedSetting();
                }
            }
        };

        registerDeviceStateAutoRotateSettingObserver();
        registerAccelerometerRotationSettingObserver();
        registerDeviceStateObserver();
    }

    /** Request to change {@link DEVICE_STATE_ROTATION_LOCK} persisted setting. */
    public void requestDeviceStateAutoRotateSettingChange(int deviceState, boolean autoRotate) {
        postUpdate(new UpdateDeviceStateAutoRotateSetting(deviceState, autoRotate));
    }

    /** Request to change {@link ACCELEROMETER_ROTATION} persisted setting. */
    public void requestAccelerometerRotationSettingChange(boolean autoRotate) {
        postUpdate(new UpdateAccelerometerRotationSetting(autoRotate));
    }

    private void registerDeviceStateAutoRotateSettingObserver() {
        mDeviceStateAutoRotateSettingManager.registerListener(
                () -> postUpdate(
                        PersistedSettingUpdate.INSTANCE));
    }

    private void registerAccelerometerRotationSettingObserver() {
        mContentResolver.registerContentObserver(
                getUriFor(ACCELEROMETER_ROTATION),
                /* notifyForDescendants= */ false,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        postUpdate(PersistedSettingUpdate.INSTANCE);
                    }
                }, UserHandle.USER_CURRENT);
    }

    private void registerDeviceStateObserver() {
        mDeviceStateController.registerDeviceStateCallback(
                (DeviceStateEnum deviceStateEnum, DeviceState deviceState) -> {
                    postUpdate(new UpdateDeviceState(deviceState.getIdentifier()));
                },
                new HandlerExecutor(mHandler));
    }

    private void postUpdate(Event event) {
        Message.obtain(mEventHandler, MSG_UPDATE_STATE, event).sendToTarget();
    }

    private boolean updateInMemoryState(Event event) {
        // Compare persisted setting value with in-memory state before making any changes to
        // in-memory state. This is to detect if persisted setting was changed directly, which is
        // not expected.
        final boolean newAccelerometerRotationSetting = getAccelerometerRotationSetting();
        SparseIntArray newDeviceStateAutoRotateSetting = getDeviceStateAutoRotateSetting();
        // Map would be null if string value of DEVICE_STATE_ROTATION_LOCK is corrupted.
        final boolean isDeviceStateAutoRotateSettingCorrupted =
                newDeviceStateAutoRotateSetting == null;
        if (isDeviceStateAutoRotateSettingCorrupted) {
            // If string value of DEVICE_STATE_ROTATION_LOCK is corrupted, rewrite it with default
            // value while also respecting current ACCELEROMETER_ROTATION setting value.
            newDeviceStateAutoRotateSetting = getDefaultDeviceStateAutoRotateSetting();
            newDeviceStateAutoRotateSetting.put(mDeviceState,
                    mAccelerometerSetting ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                            : DEVICE_STATE_ROTATION_LOCK_LOCKED);
        }

        final boolean wasAccelerometerRotationSettingChanged =
                (newAccelerometerRotationSetting != mAccelerometerSetting);
        final boolean wasDevicesStateAutoRotateSettingChanged = !equals(
                mDeviceStateAutoRotateSetting, newDeviceStateAutoRotateSetting);

        if (wasAccelerometerRotationSettingChanged || wasDevicesStateAutoRotateSettingChanged) {
            // System apps should only request changes via DeviceStateAutoRotateSettingManager's
            // APIs. Direct updates to the persisted setting will trigger an error.
            StringBuilder errorMessage = new StringBuilder("Persisted setting:\n");
            if (wasAccelerometerRotationSettingChanged) {
                errorMessage.append("ACCELEROMETER_ROTATION setting changed from ").append(
                        mAccelerometerSetting).append(" to ").append(
                        newAccelerometerRotationSetting).append(" via Settings API.\n");
            }
            if (wasDevicesStateAutoRotateSettingChanged) {
                errorMessage.append(
                        "DEVICE_STATE_ROTATION_LOCK setting directly changed from ").append(
                        mDeviceStateAutoRotateSetting).append(" to ").append(
                        newDeviceStateAutoRotateSetting).append("\nExpectation is for system-apps "
                        + "to only use defined apis to change auto-rotate persisted settings.\n");
            }
            Slog.e(TAG, errorMessage
                    + "Using Settings API to write auto-rotate persisted setting, could result "
                    + "in inconsistent auto-rotate values.");
        }

        // TODO(b/412714949): Add logging or a mechanism to dump the state whenever changes are made
        //  to relevant settings
        updateInMemoryStateFromEvent(event);

        // At this point, all in-memory properties should be updated, excluding any changes made
        // directly to persisted settings.
        // When ACCELEROMETER_ROTATION and DEVICE_STATE_ROTATION_LOCK persisted settings both change
        // since the last update was processed, there is no way to know the order of their change.
        // Conflicts will arise in determining which change to persist. In that case, we will
        // prioritize ACCELEROMETER_ROTATION because it has a direct impact on the user visible
        // behavior.
        if (wasDevicesStateAutoRotateSettingChanged) {
            // Clone the newDeviceStateAutoRotateSetting to avoid modifying it when updating
            // mDeviceStateAutoRotateSetting in future
            mDeviceStateAutoRotateSetting = newDeviceStateAutoRotateSetting.clone();
            mAccelerometerSetting = mDeviceStateAutoRotateSetting.get(mDeviceState)
                    == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
        }
        if (wasAccelerometerRotationSettingChanged) {
            mAccelerometerSetting = newAccelerometerRotationSetting;
            mDeviceStateAutoRotateSetting.put(mDeviceState,
                    mAccelerometerSetting ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                            : DEVICE_STATE_ROTATION_LOCK_LOCKED);
        }

        // Return true if the in-memory state was updated and write needs to be made in persisted
        // settings.
        return (mAccelerometerSetting != newAccelerometerRotationSetting
                || !equals(mDeviceStateAutoRotateSetting, newDeviceStateAutoRotateSetting)
                || isDeviceStateAutoRotateSettingCorrupted);
    }

    private void updateInMemoryStateFromEvent(Event event) {
        switch (event) {
            case UpdateAccelerometerRotationSetting updateAccelerometerRotationSetting -> {
                mAccelerometerSetting = updateAccelerometerRotationSetting.mAutoRotate;
                mDeviceStateAutoRotateSetting.put(mDeviceState,
                        mAccelerometerSetting ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                                : DEVICE_STATE_ROTATION_LOCK_LOCKED);
            }
            case UpdateDeviceStateAutoRotateSetting updateDeviceStateAutoRotateSetting -> {
                mDeviceStateAutoRotateSetting.put(
                        updateDeviceStateAutoRotateSetting.mDeviceState,
                        updateDeviceStateAutoRotateSetting.mAutoRotate
                                ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                                : DEVICE_STATE_ROTATION_LOCK_LOCKED);
                mAccelerometerSetting = mDeviceStateAutoRotateSetting.get(mDeviceState)
                        == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
            }
            case UpdateDeviceState updateDeviceState -> {
                mDeviceState = updateDeviceState.mDeviceState;
                mAccelerometerSetting = mDeviceStateAutoRotateSetting.get(mDeviceState)
                        == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
            }
            default -> {
            }
        }
    }

    private boolean getAccelerometerRotationSetting() {
        return Settings.System.getIntForUser(mContentResolver, ACCELEROMETER_ROTATION,
                /* def= */ -1, UserHandle.USER_CURRENT) == ACCELEROMETER_ROTATION_ON;
    }

    @Nullable
    private SparseIntArray getDeviceStateAutoRotateSetting() {
        return mDeviceStateAutoRotateSettingManager.getRotationLockSetting();
    }

    @NonNull
    private SparseIntArray getDefaultDeviceStateAutoRotateSetting() {
        return mDeviceStateAutoRotateSettingManager.getDefaultRotationLockSetting();
    }

    private void writeInMemoryStateIntoPersistedSetting() {
        Settings.System.putIntForUser(mContentResolver, ACCELEROMETER_ROTATION,
                mAccelerometerSetting ? ACCELEROMETER_ROTATION_ON : ACCELEROMETER_ROTATION_OFF,
                UserHandle.USER_CURRENT);

        final String serializedDeviceStateAutoRotateSetting =
                convertIntArrayToSerializedSetting(mDeviceStateAutoRotateSetting);
        Settings.Secure.putStringForUser(mContentResolver, DEVICE_STATE_ROTATION_LOCK,
                serializedDeviceStateAutoRotateSetting, UserHandle.USER_CURRENT);
        if (DEBUG) {
            Slog.d(TAG, "Wrote into persisted setting:\n" + "ACCELEROMETER_ROTATION="
                    + mAccelerometerSetting + "\nDEVICE_STATE_ROTATION_LOCK="
                    + serializedDeviceStateAutoRotateSetting);
        }
    }

    private static String convertIntArrayToSerializedSetting(
            SparseIntArray intArray) {
        return IntStream.range(0, intArray.size())
                .mapToObj(i -> intArray.keyAt(i) + SEPARATOR_REGEX + intArray.valueAt(i))
                .collect(Collectors.joining(SEPARATOR_REGEX));
    }

    private static boolean equals(SparseIntArray a, SparseIntArray b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            if (b.keyAt(i) != a.keyAt(i) || b.valueAt(i) != a.valueAt(i)) {
                return false;
            }
        }
        return true;
    }

    static sealed class Event {
        private Event() {
        }

        /**
         * Event sent when there is a request to update the current auto-rotate setting.
         * This occurs when actions like `freezeRotation` or `thawRotation` are triggered.
         */
        static final class UpdateAccelerometerRotationSetting extends Event {
            final boolean mAutoRotate;

            /**
             * @param autoRotate The desired auto-rotate state to write into ACCELEROMETER_ROTATION.
             */
            UpdateAccelerometerRotationSetting(boolean autoRotate) {
                mAutoRotate = autoRotate;
            }
        }

        /**
         * Event sent when there is a request to update the device's auto-rotate
         * setting(DEVICE_STATE_ROTATION_LOCK) for a specific device state.
         */
        static final class UpdateDeviceStateAutoRotateSetting extends Event {
            final int mDeviceState;
            final boolean mAutoRotate;

            /**
             * @param deviceState The device state the change is intended for.
             * @param autoRotate  The desired auto-rotate state for this device state.
             */
            UpdateDeviceStateAutoRotateSetting(int deviceState, boolean autoRotate) {
                mDeviceState = deviceState;
                mAutoRotate = autoRotate;
            }
        }

        /**
         * Event sent when the device state changes.
         */
        static final class UpdateDeviceState extends Event {
            final int mDeviceState;

            /**
             * @param deviceState New device state.
             */
            UpdateDeviceState(int deviceState) {
                mDeviceState = deviceState;
            }
        }

        /**
         * Event sent when there is a change in either of the two persisted settings:
         * <ul>
         * <li> Current auto-rotate setting</li>
         * <li> Device state auto-rotate setting</li>
         * </ul>
         */
        static final class PersistedSettingUpdate extends Event {
            static final PersistedSettingUpdate INSTANCE = new PersistedSettingUpdate();

            private PersistedSettingUpdate() {
            }
        }
    }
}
