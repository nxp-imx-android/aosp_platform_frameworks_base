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

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import static com.android.internal.view.RotationPolicy.NATURAL_ROTATION;
import static com.android.internal.view.RotationPolicy.areAllRotationsAllowed;
import static com.android.internal.view.RotationPolicy.useCurrentRotationOnRotationLockChange;
import static com.android.server.wm.DisplayRotation.NO_UPDATE_USER_ROTATION;
import static com.android.server.wm.DisplayRotation.USE_CURRENT_ROTATION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.devicestate.DeviceState;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.PersistedSettingUpdate;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateAccelerometerRotationSetting;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateDeviceState;
import com.android.server.wm.DeviceStateAutoRotateSettingController.Event.UpdateDeviceStateAutoRotateSetting;
import com.android.server.wm.DeviceStateController.DeviceStateEnum;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager;

import java.util.ArrayList;
import java.util.List;

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
    private static final int ACCELEROMETER_ROTATION_OFF = 0;
    private static final int ACCELEROMETER_ROTATION_ON = 1;
    private static final int INVALID_DEVICE_STATE = -1;
    // TODO(b/413598268): Disable debugging after the
    //  com.android.window.flags.enable_device_state_auto_rotate_setting_refactor flag is rolled-out
    private static final boolean DEBUG = true;
    private static final int MSG_UPDATE_STATE = 1;

    private final Handler mHandler;
    private final Handler mEventHandler;
    private final DeviceStateAutoRotateSettingManager mDeviceStateAutoRotateSettingManager;
    private final ContentResolver mContentResolver;
    private final DeviceStateController mDeviceStateController;
    private final List<Event> mPendingEvents = new ArrayList<>();
    private final WindowManagerService mWm;
    private final Context mContext;

    private int mDeviceState = INVALID_DEVICE_STATE;
    private boolean mAccelerometerSetting;
    private SparseIntArray mDeviceStateAutoRotateSetting;

    public DeviceStateAutoRotateSettingController(
            DeviceStateController deviceStateController,
            DeviceStateAutoRotateSettingManager deviceStateAutoRotateSettingManager,
            WindowManagerService wmService) {
        mDeviceStateAutoRotateSettingManager = deviceStateAutoRotateSettingManager;
        mWm = wmService;
        mContext = mWm.mContext;
        mHandler = getHandler();
        mContentResolver = mContext.getContentResolver();
        mDeviceStateController = deviceStateController;
        mDeviceStateAutoRotateSetting = getDeviceStateAutoRotateSetting();
        if (mDeviceStateAutoRotateSetting == null) {
            // Map would be null if string value of DEVICE_STATE_ROTATION_LOCK is corrupted.
            mDeviceStateAutoRotateSetting = getDefaultDeviceStateAutoRotateSetting();
        }
        mAccelerometerSetting = getAccelerometerRotationSetting();
        mEventHandler = new Handler(mHandler.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                final Event event = (Event) msg.obj;
                if (mDeviceState == INVALID_DEVICE_STATE && !(event instanceof UpdateDeviceState)) {
                    mPendingEvents.add(event);
                    Log.w(TAG, "Trying to write into auto-rotate settings, while "
                            + "device-state is unavailable.\n" + "Could not process the event="
                            + event.getClass().getSimpleName() + ".\n"
                            + "This event will be queued and processed later once we receive "
                            + "device-state update.");
                    return;
                }

                handleEvent(event);

                if (!mPendingEvents.isEmpty()) {
                    for (int i = 0; i < mPendingEvents.size(); i++) {
                        handleEvent(mPendingEvents.get(i));
                    }
                    mPendingEvents.clear();
                }
            }
        };

        registerDeviceStateAutoRotateSettingObserver();
        registerAccelerometerRotationSettingObserver();
        registerDeviceStateObserver();
    }

    private void handleEvent(@NonNull Event event) {
        final boolean persistedAccelerometerRotationSetting = getAccelerometerRotationSetting();
        final SparseIntArray persistedDeviceStateAutoRotateSetting =
                getDeviceStateAutoRotateSetting();

        updateInMemoryState(event, persistedAccelerometerRotationSetting,
                persistedDeviceStateAutoRotateSetting);

        writeInMemoryStateIntoPersistedSetting(persistedAccelerometerRotationSetting,
                persistedDeviceStateAutoRotateSetting);
        writeUserRotationSettingIfNeeded(event, persistedAccelerometerRotationSetting);
    }

    /** Request to change {@link DEVICE_STATE_ROTATION_LOCK} persisted setting. */
    public void requestDeviceStateAutoRotateSettingChange(int deviceState, boolean autoRotate) {
        postUpdate(new UpdateDeviceStateAutoRotateSetting(deviceState, autoRotate));
    }

    /**
     * Request to change {@link ACCELEROMETER_ROTATION} persisted setting. If needed, we might also
     * write into {@link USER_ROTATION} with {@param userRotation}.
     */
    public void requestAccelerometerRotationSettingChange(boolean autoRotate, int userRotation) {
        postUpdate(new UpdateAccelerometerRotationSetting(autoRotate, userRotation));
    }

    private void registerDeviceStateAutoRotateSettingObserver() {
        mDeviceStateAutoRotateSettingManager.registerListener(
                () -> postUpdate(PersistedSettingUpdate.INSTANCE));
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

    private void updateInMemoryState(Event event, boolean persistedAccelerometerRotationSetting,
            SparseIntArray persistedDeviceStateAutoRotateSetting) {
        // Compare persisted setting value with in-memory state before making any changes to
        // in-memory state. This is to detect if persisted setting was changed directly, which is
        // not expected.
        // Map would be null if string value of DEVICE_STATE_ROTATION_LOCK is corrupted.
        final boolean isDeviceStateAutoRotateSettingCorrupted =
                persistedDeviceStateAutoRotateSetting == null;
        if (isDeviceStateAutoRotateSettingCorrupted) {
            // If string value of DEVICE_STATE_ROTATION_LOCK is corrupted, rewrite it with default
            // value while also respecting current ACCELEROMETER_ROTATION setting value.
            persistedDeviceStateAutoRotateSetting = getDefaultDeviceStateAutoRotateSetting();
            persistedDeviceStateAutoRotateSetting.put(mDeviceState,
                    mAccelerometerSetting ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                            : DEVICE_STATE_ROTATION_LOCK_LOCKED);
        }

        final boolean wasAccelerometerRotationSettingChanged =
                (persistedAccelerometerRotationSetting != mAccelerometerSetting);
        final boolean wasDevicesStateAutoRotateSettingChanged = !equals(
                mDeviceStateAutoRotateSetting, persistedDeviceStateAutoRotateSetting);

        if (wasAccelerometerRotationSettingChanged || wasDevicesStateAutoRotateSettingChanged) {
            // System apps should only request changes via DeviceStateAutoRotateSettingManager's
            // APIs. Direct updates to the persisted setting will trigger an error.
            StringBuilder errorMessage = new StringBuilder("Persisted setting:\n");
            if (wasAccelerometerRotationSettingChanged) {
                errorMessage.append("ACCELEROMETER_ROTATION setting changed from ").append(
                        mAccelerometerSetting).append(" to ").append(
                        persistedAccelerometerRotationSetting).append(" via Settings API.\n");
            }
            if (wasDevicesStateAutoRotateSettingChanged) {
                errorMessage.append(
                        "DEVICE_STATE_ROTATION_LOCK setting directly changed from ").append(
                        mDeviceStateAutoRotateSetting).append(" to ").append(
                        persistedDeviceStateAutoRotateSetting).append(
                        "\nExpectation is for system-apps to only use defined apis to change "
                                + "auto-rotate persisted settings.\n");
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
            // Clone the persistedDeviceStateAutoRotateSetting to avoid modifying it when updating
            // mDeviceStateAutoRotateSetting in future
            mDeviceStateAutoRotateSetting = persistedDeviceStateAutoRotateSetting.clone();
            mAccelerometerSetting = mDeviceStateAutoRotateSetting.get(mDeviceState)
                    == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
        }
        if (wasAccelerometerRotationSettingChanged) {
            mAccelerometerSetting = persistedAccelerometerRotationSetting;
            mDeviceStateAutoRotateSetting.put(mDeviceState,
                    mAccelerometerSetting ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                            : DEVICE_STATE_ROTATION_LOCK_LOCKED);
        }
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

    private void writeInMemoryStateIntoPersistedSetting(
            boolean persistedAccelerometerRotationSetting,
            SparseIntArray persistedDeviceStateAutoRotateSetting) {
        if (mAccelerometerSetting != persistedAccelerometerRotationSetting) {
            Settings.System.putIntForUser(mContentResolver, ACCELEROMETER_ROTATION,
                    mAccelerometerSetting ? ACCELEROMETER_ROTATION_ON : ACCELEROMETER_ROTATION_OFF,
                    UserHandle.USER_CURRENT);

            if (DEBUG) {
                Slog.d(TAG, "Wrote into persisted setting:\n" + "ACCELEROMETER_ROTATION="
                        + mAccelerometerSetting);
            }
        }

        if (!equals(mDeviceStateAutoRotateSetting, persistedDeviceStateAutoRotateSetting)) {
            mDeviceStateAutoRotateSettingManager.updateSetting(
                    mDeviceStateAutoRotateSetting.clone(),
                    persistedDeviceStateAutoRotateSetting == null
                            ? getDefaultDeviceStateAutoRotateSetting()
                            : persistedDeviceStateAutoRotateSetting.clone());

            if (DEBUG) {
                Slog.d(TAG, "Wrote into persisted setting:\n" + "DEVICE_STATE_ROTATION_LOCK="
                        + mDeviceStateAutoRotateSetting);
            }
        }
    }

    private void writeUserRotationSettingIfNeeded(Event event,
            boolean persistedAccelerometerRotationSetting) {
        if (!(event instanceof UpdateAccelerometerRotationSetting)
                && (mAccelerometerSetting == persistedAccelerometerRotationSetting)) {
            return;
        }
        final int userRotation;
        if (event instanceof UpdateAccelerometerRotationSetting) {
            // If the event is `UpdateAccelerometerRotationSetting`, it means that the
            // userRotation was provided, so we should set it.
            userRotation = ((UpdateAccelerometerRotationSetting) event).mUserRotation;
        } else {
            // If the event is not `UpdateAccelerometerRotationSetting`, it means that the
            // userRotation was not explicitly provided.
            if (mAccelerometerSetting) {
                userRotation = NO_UPDATE_USER_ROTATION;
            } else {
                userRotation = areAllRotationsAllowed(mContext)
                        || useCurrentRotationOnRotationLockChange(mContext)
                        ? USE_CURRENT_ROTATION
                        : NATURAL_ROTATION;
            }
        }
        synchronized (mWm.mRoot.mService.mGlobalLock) {
            mWm.mRoot.getDefaultDisplay().getDisplayRotation().setUserRotationSetting(
                    mAccelerometerSetting ? WindowManagerPolicy.USER_ROTATION_FREE
                            : WindowManagerPolicy.USER_ROTATION_LOCKED, userRotation,
                    "DSAutoRotateCtrl");
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

    @VisibleForTesting
    Handler getHandler() {
        return mWm.mH;
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
         * It also contains the user rotation that should be set, if userRotation is -1 then
         * {@link Settings.System#USER_ROTATION} setting will not be updated.
         */
        static final class UpdateAccelerometerRotationSetting extends Event {
            final boolean mAutoRotate;
            final int mUserRotation;

            /**
             * @param autoRotate   The desired auto-rotate state to write into
             *                     ACCELEROMETER_ROTATION.
             * @param userRotation The desired user rotation to write into USER_ROTATION.
             */
            UpdateAccelerometerRotationSetting(boolean autoRotate, int userRotation) {
                mAutoRotate = autoRotate;
                mUserRotation = userRotation;
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
