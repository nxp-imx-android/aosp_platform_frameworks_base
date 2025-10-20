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
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

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

    /**
     * A SparseArray where the index is the userId. Each entry is per device key remapping.
     * Per device key mappings are stored as a nested map of InputDeviceIdentifier to key remapping.
     * i.e. [UserId, [InputDeviceIdentifier, [fromKeyCode, toKeyCode]]].
     */
    @GuardedBy("mLock")
    private final SparseArray<Map<InputDeviceIdentifier, Map<Integer, Integer>>> mKeyRemappingData =
            new SparseArray<>();
    /**
     * A SparseArray where the index is the userId. Each entry is per device axis remapping.
     * Per device axis mappings are stored as a nested map of InputDeviceIdentifier to axis
     * remapping.
     * i.e. [UserId, [InputDeviceIdentifier, [fromAxis, toAxis]]].
     */
    @GuardedBy("mLock")
    private final SparseArray<Map<InputDeviceIdentifier, Map<Integer, Integer>>>
            mAxisRemappingData = new SparseArray<>();
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

    public void remapKey(@UserIdInt int userId, @NonNull InputDeviceIdentifier deviceIdentifier,
            int fromKeyCode, int toKeyCode) {
        if (fromKeyCode == toKeyCode) {
            removeKeyRemapping(userId, deviceIdentifier, fromKeyCode);
            return;
        }
        Map<Integer, Integer> deviceRemappings;
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                mKeyRemappingData.put(userId, new ArrayMap<>());
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mKeyRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                userRemappings.put(deviceIdentifier, new ArrayMap<>());
            }
            deviceRemappings = userRemappings.get(deviceIdentifier);
            deviceRemappings.put(fromKeyCode, toKeyCode);
        }
        findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, deviceRemappings);
    }

    public void removeKeyRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier, int fromKeyCode) {
        Map<Integer, Integer> deviceRemappings;
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mKeyRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                Slog.d(TAG, "No existing remapping for device = " + deviceIdentifier);
                return;
            }
            deviceRemappings = userRemappings.get(deviceIdentifier);
            if (deviceRemappings.remove(fromKeyCode) == null) {
                Slog.d(TAG, "No existing key remapping for device = " + deviceIdentifier
                        + " for fromKeyCode = " + fromKeyCode);
                return;
            }
            if (deviceRemappings.isEmpty()) {
                userRemappings.remove(deviceIdentifier);
            }
            if (userRemappings.isEmpty()) {
                mKeyRemappingData.remove(userId);
            }
        }
        findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, deviceRemappings);
    }

    public void clearAllKeyRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            if (!mKeyRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mKeyRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                Slog.d(TAG, "No existing remapping for device = " + deviceIdentifier);
                return;
            }
            // Cleanup if remapping map is empty
            userRemappings.remove(deviceIdentifier);
            if (userRemappings.isEmpty()) {
                mKeyRemappingData.remove(userId);
            }
        }
        findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, /* deviceRemappings= */null);
    }

    @NonNull
    public Map<Integer, Integer> getKeyRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mKeyRemappingData.get(userId);
            if (userRemappings == null) {
                return new ArrayMap<>();
            }
            return userRemappings.getOrDefault(deviceIdentifier, new ArrayMap<>());
        }
    }

    public void remapAxis(@UserIdInt int userId, @NonNull InputDeviceIdentifier deviceIdentifier,
            @MotionEvent.Axis int fromAxis, @MotionEvent.Axis int toAxis) {
        if (fromAxis == toAxis) {
            removeAxisRemapping(userId, deviceIdentifier, fromAxis);
            return;
        }
        Map<Integer, Integer> deviceRemappings;
        synchronized (mLock) {
            if (!mAxisRemappingData.contains(userId)) {
                mAxisRemappingData.put(userId, new ArrayMap<>());
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mAxisRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                userRemappings.put(deviceIdentifier, new ArrayMap<>());
            }
            deviceRemappings = userRemappings.get(deviceIdentifier);
            deviceRemappings.put(fromAxis, toAxis);
        }
        findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, deviceRemappings);
    }

    public void removeAxisRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier, @MotionEvent.Axis int fromAxis) {
        Map<Integer, Integer> deviceRemappings;
        synchronized (mLock) {
            if (!mAxisRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing axis remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mAxisRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier);
                return;
            }
            deviceRemappings = userRemappings.get(deviceIdentifier);
            if (deviceRemappings.remove(fromAxis) == null) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier
                        + " for axis = " + fromAxis);
                return;
            }
            if (deviceRemappings.isEmpty()) {
                userRemappings.remove(deviceIdentifier);
            }
            if (userRemappings.isEmpty()) {
                mAxisRemappingData.remove(userId);
            }
        }
        findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, deviceRemappings);
    }

    public void clearAllAxisRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            if (!mAxisRemappingData.contains(userId)) {
                Slog.d(TAG, "No existing axis remapping for userId = " + userId);
                return;
            }
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mAxisRemappingData.get(userId);
            if (!userRemappings.containsKey(deviceIdentifier)) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier);
                return;
            }
            // Cleanup if remapping map is empty
            userRemappings.remove(deviceIdentifier);
            if (userRemappings.isEmpty()) {
                mAxisRemappingData.remove(userId);
            }
        }
        findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, /* deviceRemappings= */null);
    }

    @NonNull
    public Map<Integer, Integer> getAxisRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, Map<Integer, Integer>> userRemappings =
                    mAxisRemappingData.get(userId);
            if (userRemappings == null) {
                return new ArrayMap<>();
            }
            return userRemappings.getOrDefault(deviceIdentifier, new ArrayMap<>());
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
        if (isPhysicalButtonDevice(device)) {
            setKeyRemapping(deviceId, getKeyRemappings(mCurrentUserId, device.getIdentifier()));
        }
        if (isPhysicalJoystickDevice(device)) {
            setAxisRemapping(deviceId, getAxisRemappings(mCurrentUserId, device.getIdentifier()));
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        setKeyRemapping(deviceId, null);
        setAxisRemapping(deviceId, null);
    }


    private void findButtonDeviceAndApplyKeyRemapping(
            @NonNull InputDeviceIdentifier deviceIdentifier,
            @Nullable Map<Integer, Integer> deviceRemappings) {
        InputDevice device = getDeviceByIdentifier(deviceIdentifier);
        if (device == null) {
            return;
        }
        if (!isPhysicalButtonDevice(device)) {
            return;
        }
        setKeyRemapping(device.getId(), deviceRemappings);
    }

    private void findJoystickDeviceAndApplyAxisRemapping(
            @NonNull InputDeviceIdentifier deviceIdentifier,
            @Nullable Map<Integer, Integer> deviceRemappings) {
        InputDevice device = getDeviceByIdentifier(deviceIdentifier);
        if (device == null) {
            return;
        }
        if (!isPhysicalJoystickDevice(device)) {
            return;
        }
        setAxisRemapping(device.getId(), deviceRemappings);
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

    private void setAxisRemapping(int deviceId, @Nullable Map<Integer, Integer> deviceRemappings) {
        int[] fromAxisArr;
        int[] toAxisArr;
        if (deviceRemappings == null) {
            fromAxisArr = new int[0];
            toAxisArr = new int[0];
        } else {
            fromAxisArr = new int[deviceRemappings.size()];
            toAxisArr = new int[deviceRemappings.size()];
            int index = 0;
            for (Map.Entry<Integer, Integer> entry : deviceRemappings.entrySet()) {
                fromAxisArr[index] = entry.getKey();
                toAxisArr[index] = entry.getValue();
                index++;
            }
        }
        mNative.setAxisRemappingForDevice(deviceId, fromAxisArr, toAxisArr);
    }

    @Nullable
    private InputDevice getDeviceByIdentifier(@NonNull InputDeviceIdentifier identifier) {
        return mInputManager.getInputDeviceByDescriptor(identifier.getDescriptor());
    }

    private static boolean isPhysicalButtonDevice(@NonNull InputDevice inputDevice) {
        if (!inputDevice.isPhysicalDevice()) {
            return false;
        }
        return (inputDevice.getSources() & InputDevice.SOURCE_CLASS_BUTTON) != 0;
    }

    private static boolean isPhysicalJoystickDevice(@NonNull InputDevice inputDevice) {
        if (!inputDevice.isPhysicalDevice()) {
            return false;
        }
        return (inputDevice.getSources() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0;
    }

    public void dump(IndentingPrintWriter ipw) {
        ipw.println("Input Device Remapper State:");
        ipw.increaseIndent();
        synchronized (mLock) {
            ipw.println("Current user ID: " + mCurrentUserId);
            ipw.println("Key Remapping Data:");
            ipw.increaseIndent();
            if (mKeyRemappingData.size() == 0) {
                ipw.println("<none>");
            } else {
                for (int i = 0; i < mKeyRemappingData.size(); i++) {
                    int userId = mKeyRemappingData.keyAt(i);
                    ipw.println("User " + userId + ":");
                    ipw.increaseIndent();
                    Map<InputDeviceIdentifier, Map<Integer, Integer>> deviceMap =
                            mKeyRemappingData.valueAt(i);
                    for (Map.Entry<InputDeviceIdentifier, Map<Integer, Integer>> entry :
                            deviceMap.entrySet()) {
                        ipw.println("Device: " + entry.getKey());
                        ipw.increaseIndent();
                        for (Map.Entry<Integer, Integer> remapEntry :
                                entry.getValue().entrySet()) {
                            ipw.println(KeyEvent.keyCodeToString(remapEntry.getKey()) + " -> "
                                    + KeyEvent.keyCodeToString(remapEntry.getValue()));
                        }
                        ipw.decreaseIndent();
                    }
                    ipw.decreaseIndent();
                }
            }
            ipw.decreaseIndent();

            ipw.println("Axis Remapping Data:");
            ipw.increaseIndent();
            if (mAxisRemappingData.size() == 0) {
                ipw.println("<none>");
            } else {
                for (int i = 0; i < mAxisRemappingData.size(); i++) {
                    int userId = mAxisRemappingData.keyAt(i);
                    ipw.println("User " + userId + ":");
                    ipw.increaseIndent();
                    Map<InputDeviceIdentifier, Map<Integer, Integer>> deviceMap =
                            mAxisRemappingData.valueAt(i);
                    for (Map.Entry<InputDeviceIdentifier, Map<Integer, Integer>> entry :
                            deviceMap.entrySet()) {
                        ipw.println("Device: " + entry.getKey());
                        ipw.increaseIndent();
                        for (Map.Entry<Integer, Integer> remapEntry : entry.getValue().entrySet()) {
                            ipw.println(MotionEvent.axisToString(remapEntry.getKey()) + " -> "
                                    + MotionEvent.axisToString(remapEntry.getValue()));
                        }
                        ipw.decreaseIndent();
                    }
                    ipw.decreaseIndent();
                }
            }
        }
        ipw.decreaseIndent();
        ipw.decreaseIndent();
    }
}
