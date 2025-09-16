/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.input;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseArray;
import android.view.InputDevice;

import com.android.internal.annotations.GuardedBy;

import java.util.Map;
import java.util.Objects;

/**
 * A component of {@link InputManagerService} responsible for managing remappings for
 * {@link InputDevice}.
 *
 * <p> This class is source of truth for all per-device remapping.</p>
 *
 * @hide
 */
final class InputDeviceRemapper implements InputManager.InputDeviceListener {

    private static final String TAG = "InputDeviceRemapper";
    private static final int MSG_UPDATE_EXISTING_DEVICES = 1;

    private final Object mLock = new Object();
    private final Context mContext;
    private final NativeInputManagerService mNative;
    private final Handler mHandler;
    private InputManager mInputManager;

    // A SparseArray where the index is the userId. Each entry is per device key remapping.
    // Per device key mappings are stored as a nested map of InputDeviceIdentifier to key remapping.
    // i.e. SparseArray<UserId, Map<InputDeviceIdentifier, Map<fromKeyCode, toKeyCode>>>.
    @GuardedBy("mLock")
    private final SparseArray<Map<InputDeviceIdentifier, Map<Integer, Integer>>> mKeyRemappingData =
            new SparseArray<>();
    @GuardedBy("mLock")
    @UserIdInt
    private int mCurrentUserId = UserHandle.USER_SYSTEM;

    InputDeviceRemapper(Context context, NativeInputManagerService nativeService,
            Looper looper) {
        mContext = context;
        mNative = nativeService;
        mHandler = new Handler(looper, this::handleMessage);
    }

    public void systemRunning() {
        mInputManager = Objects.requireNonNull(
                mContext.getSystemService(InputManager.class));
        mInputManager.registerInputDeviceListener(this, mHandler);

        Message msg = Message.obtain(mHandler, MSG_UPDATE_EXISTING_DEVICES,
                mInputManager.getInputDeviceIds());
        mHandler.sendMessage(msg);
    }

    @MainThread
    public void setCurrentUserId(@UserIdInt int userId) {
        synchronized (mLock) {
            mCurrentUserId = userId;
        }
        Message msg = Message.obtain(mHandler, MSG_UPDATE_EXISTING_DEVICES,
                mInputManager.getInputDeviceIds());
        mHandler.sendMessage(msg);
    }

    public void remapKey(@UserIdInt int userId, @NonNull InputDeviceIdentifier identifier,
            int fromKeyCode, int toKeyCode) {
        Map<Integer, Integer> remapping;
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                mKeyRemappingData.put(userId, new ArrayMap<>());
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> remappingData =
                    mKeyRemappingData.get(userId);
            if (!remappingData.containsKey(identifier)) {
                remappingData.put(identifier, new ArrayMap<>());
            }
            remapping = remappingData.get(identifier);
            remapping.put(fromKeyCode, toKeyCode);
        }
        applyKeyRemapping(identifier, remapping);
    }

    public void removeKeyRemapping(@UserIdInt int userId, @NonNull InputDeviceIdentifier identifier,
            int fromKeyCode) {
        Map<Integer, Integer> remapping;
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> remappingData =
                    mKeyRemappingData.get(userId);
            if (!remappingData.containsKey(identifier)) {
                Slog.d(TAG, "No existing remapping for device = " + identifier);
                return;
            }
            remapping = remappingData.get(identifier);
            remapping.remove(fromKeyCode);
            if (remapping.isEmpty()) {
                remappingData.remove(identifier);
            }
            if (remappingData.isEmpty()) {
                mKeyRemappingData.remove(userId);
            }
        }
        applyKeyRemapping(identifier, remapping);
    }

    public void clearAllKeyRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier identifier) {
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> remappingData =
                    mKeyRemappingData.get(userId);
            if (!remappingData.containsKey(identifier)) {
                Slog.d(TAG, "No existing remapping for device = " + identifier);
                return;
            }
            // Cleanup if remapping map is empty
            remappingData.remove(identifier);
            if (remappingData.isEmpty()) {
                mKeyRemappingData.remove(userId);
            }
        }
        applyKeyRemapping(identifier, /* remapping= */null);
    }

    @NonNull
    public Map<Integer, Integer> getKeyRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier identifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, Map<Integer, Integer>> remappingData =
                    mKeyRemappingData.get(userId);
            if (remappingData == null) {
                return new ArrayMap<>();
            }
            return remappingData.getOrDefault(identifier, new ArrayMap<>());
        }
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_UPDATE_EXISTING_DEVICES:
                for (int deviceId : (int[]) msg.obj) {
                    onInputDeviceAdded(deviceId);
                }
                return true;
        }
        return false;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        onInputDeviceChanged(deviceId);
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        InputDevice device = mInputManager.getInputDevice(deviceId);
        if (device == null) {
            return;
        }
        if (!isPhysicalButtonDevice(device)) {
            return;
        }
        setKeyRemapping(deviceId, getKeyRemapping(mCurrentUserId, device.getIdentifier()));
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        setKeyRemapping(deviceId, null);
    }


    private void applyKeyRemapping(@NonNull InputDeviceIdentifier identifier,
            @Nullable Map<Integer, Integer> remapping) {
        InputDevice device = mInputManager.getInputDeviceByDescriptor(identifier.getDescriptor());
        if (device == null) {
            return;
        }
        if (!isPhysicalButtonDevice(device)) {
            return;
        }
        setKeyRemapping(device.getId(), remapping);
    }

    private void setKeyRemapping(int deviceId, @Nullable Map<Integer, Integer> keyRemapping) {
        int[] fromKeycodeArr;
        int[] toKeycodeArr;
        if (keyRemapping == null) {
            fromKeycodeArr = new int[0];
            toKeycodeArr = new int[0];
        } else {
            fromKeycodeArr = new int[keyRemapping.size()];
            toKeycodeArr = new int[keyRemapping.size()];
            int index = 0;
            for (Map.Entry<Integer, Integer> entry : keyRemapping.entrySet()) {
                fromKeycodeArr[index] = entry.getKey();
                toKeycodeArr[index] = entry.getValue();
                index++;
            }
        }
        mNative.setKeyRemappingForDevice(deviceId, fromKeycodeArr, toKeycodeArr);
    }

    private static boolean isPhysicalButtonDevice(@NonNull InputDevice inputDevice) {
        if (!inputDevice.isPhysicalDevice()) {
            return false;
        }
        return (inputDevice.getSources() & InputDevice.SOURCE_CLASS_BUTTON) != 0;
    }
}
