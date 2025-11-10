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
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.server.input.data.InputDataStore;
import com.android.server.input.data.InputDeviceRemappingData;

import java.util.ArrayList;
import java.util.List;
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
    private static final int MSG_PERSIST_REMAPPINGS = 2;
    private static final int MSG_LOAD_ALL_USER_REMAPPINGS = 3;

    private final Object mLock = new Object();
    private final Context mContext;
    private final NativeInputManagerService mNative;
    private final Handler mHandler;
    private final Handler mIoHandler;
    private final InputDataStore mInputDataStore;
    private InputManager mInputManager;

    /**
     * A SparseArray where the index is the userId. Each entry is a map from InputDeviceIdentifier
     * to the remapping data.
     * i.e. [UserId, [InputDeviceIdentifier, InputDeviceRemappingData]].
     */
    @GuardedBy("mLock")
    private final SparseArray<Map<InputDeviceIdentifier, InputDeviceRemappingData>> mRemappingData =
            new SparseArray<>();

    @GuardedBy("mLock")
    @UserIdInt
    private int mCurrentUserId = UserHandle.USER_SYSTEM;

    InputDeviceRemapper(Context context, NativeInputManagerService nativeService,
            Looper looper, Looper ioLooper, InputDataStore inputDataStore) {
        mContext = context;
        mNative = nativeService;
        mHandler = new Handler(looper, this::handleMessage);
        mIoHandler = new Handler(ioLooper, this::handleIoMessage);
        mInputDataStore = inputDataStore;
    }

    public void systemRunning() {
        mInputManager = Objects.requireNonNull(
                mContext.getSystemService(InputManager.class));
        mInputManager.registerInputDeviceListener(this, mHandler);

        mIoHandler.sendEmptyMessage(MSG_LOAD_ALL_USER_REMAPPINGS);
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
        synchronized (mLock) {
            InputDeviceRemappingData data = getOrCreateRemappingDataLocked(userId,
                    deviceIdentifier);
            data.buttonRemappingMap().put(fromKeyCode, toKeyCode);
            findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, data.buttonRemappingMap());
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    public void removeKeyRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier, int fromKeyCode) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null) {
                Slog.d(TAG, "No existing remapping for device = " + deviceIdentifier);
                return;
            }
            if (data.buttonRemappingMap() == null
                    || data.buttonRemappingMap().remove(fromKeyCode) == null) {
                Slog.d(TAG, "No existing key remapping for device = " + deviceIdentifier
                        + " for fromKeyCode = " + fromKeyCode);
                return;
            }
            findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, data.buttonRemappingMap());
            cleanupRemappingDataLocked(userId, deviceIdentifier);
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    public void clearAllKeyRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                Slog.d(TAG, "No existing remapping for userId = " + userId);
                return;
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null || data.buttonRemappingMap() == null) {
                Slog.d(TAG, "No existing remapping for device = " + deviceIdentifier);
                return;
            }
            data.buttonRemappingMap().clear();
            findButtonDeviceAndApplyKeyRemapping(deviceIdentifier, /* deviceRemappings= */null);
            cleanupRemappingDataLocked(userId, deviceIdentifier);
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    @NonNull
    public Map<Integer, Integer> getKeyRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                return Map.of();
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null || data.buttonRemappingMap() == null) {
                return Map.of();
            }
            return data.buttonRemappingMap();
        }
    }

    public void remapAxis(@UserIdInt int userId, @NonNull InputDeviceIdentifier deviceIdentifier,
            @MotionEvent.Axis int fromAxis, @MotionEvent.Axis int toAxis) {
        if (fromAxis == toAxis) {
            removeAxisRemapping(userId, deviceIdentifier, fromAxis);
            return;
        }
        synchronized (mLock) {
            InputDeviceRemappingData data = getOrCreateRemappingDataLocked(userId,
                    deviceIdentifier);
            data.axisRemappingMap().put(fromAxis, toAxis);
            findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, data.axisRemappingMap());
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    public void removeAxisRemapping(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier, @MotionEvent.Axis int fromAxis) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                Slog.d(TAG, "No existing axis remapping for userId = " + userId);
                return;
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier);
            }
            if (data.axisRemappingMap() == null
                    || data.axisRemappingMap().remove(fromAxis) == null) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier
                        + " for axis = " + fromAxis);
                return;
            }
            findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, data.axisRemappingMap());
            cleanupRemappingDataLocked(userId, deviceIdentifier);
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    public void clearAllAxisRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                Slog.d(TAG, "No existing axis remapping for userId = " + userId);
                return;
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null || data.axisRemappingMap() == null) {
                Slog.d(TAG, "No existing axis remapping for device = " + deviceIdentifier);
                return;
            }
            data.axisRemappingMap().clear();
            findJoystickDeviceAndApplyAxisRemapping(deviceIdentifier, /* deviceRemappings= */null);
            cleanupRemappingDataLocked(userId, deviceIdentifier);
        }
        mIoHandler.obtainMessage(MSG_PERSIST_REMAPPINGS, userId, 0).sendToTarget();
    }

    @NonNull
    public Map<Integer, Integer> getAxisRemappings(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier deviceIdentifier) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                return Map.of();
            }
            InputDeviceRemappingData data = userRemappings.get(deviceIdentifier);
            if (data == null || data.axisRemappingMap() == null) {
                return Map.of();
            }
            return data.axisRemappingMap();
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

    private boolean handleIoMessage(Message msg) {
        switch (msg.what) {
            case MSG_PERSIST_REMAPPINGS:
                persistRemappings(msg.arg1);
                return true;
            case MSG_LOAD_ALL_USER_REMAPPINGS:
                loadAllUserRemappings();
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

    private void persistRemappings(int userId) {
        synchronized (mLock) {
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                    mRemappingData.get(userId);
            if (userRemappings == null) {
                return;
            }
            synchronized (mInputDataStore) {
                mInputDataStore.saveData(userId, new ArrayList<>(userRemappings.values()),
                        InputDeviceRemappingData.class);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void loadAllUserRemappings() {
        UserManager userManager = Objects.requireNonNull(
                mContext.getSystemService(UserManager.class));
        for (UserHandle userHandle : userManager.getUserHandles(true /* excludeDying */)) {
            loadRemappings(userHandle.getIdentifier());
        }
        // Apply remappings to any already-connected devices
        Message msg = Message.obtain(mHandler, MSG_UPDATE_EXISTING_DEVICES,
                mInputManager.getInputDeviceIds());
        mHandler.sendMessage(msg);
    }

    private void loadRemappings(int userId) {
        List<InputDeviceRemappingData> dataList;
        synchronized (mInputDataStore) {
            dataList = mInputDataStore.loadData(userId, InputDeviceRemappingData.class);
        }
        synchronized (mLock) {
            mRemappingData.remove(userId);
            if (dataList.isEmpty()) {
                return;
            }
            Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings = new ArrayMap<>();
            for (InputDeviceRemappingData data : dataList) {
                userRemappings.put(data.deviceIdentifier(), data);
            }
            mRemappingData.put(userId, userRemappings);
        }
    }

    @GuardedBy("mLock")
    private InputDeviceRemappingData getOrCreateRemappingDataLocked(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier identifier) {
        Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                mRemappingData.get(userId);
        if (userRemappings == null) {
            userRemappings = new ArrayMap<>();
            mRemappingData.put(userId, userRemappings);
        }
        InputDeviceRemappingData data = userRemappings.get(identifier);
        if (data == null) {
            data = new InputDeviceRemappingData(identifier, new ArrayMap<>(), new ArrayMap<>());
            userRemappings.put(identifier, data);
        }
        return data;
    }

    @GuardedBy("mLock")
    private void cleanupRemappingDataLocked(@UserIdInt int userId,
            @NonNull InputDeviceIdentifier identifier) {
        Map<InputDeviceIdentifier, InputDeviceRemappingData> userRemappings =
                mRemappingData.get(userId);
        if (userRemappings == null) {
            return;
        }
        InputDeviceRemappingData data = userRemappings.get(identifier);
        if (data == null) {
            return;
        }

        boolean hasButtonRemaps = data.buttonRemappingMap() != null
                && !data.buttonRemappingMap().isEmpty();
        boolean hasAxisRemaps = data.axisRemappingMap() != null
                && !data.axisRemappingMap().isEmpty();

        if (!hasButtonRemaps && !hasAxisRemaps) {
            userRemappings.remove(identifier);
            if (userRemappings.isEmpty()) {
                mRemappingData.remove(userId);
            }
        }
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
            ipw.println("Remapping Data:");
            ipw.increaseIndent();
            if (mRemappingData.size() == 0) {
                ipw.println("<none>");
            } else {
                for (int i = 0; i < mRemappingData.size(); i++) {
                    int userId = mRemappingData.keyAt(i);
                    ipw.println("User " + userId + ":");
                    ipw.increaseIndent();
                    Map<InputDeviceIdentifier, InputDeviceRemappingData> deviceMap =
                            mRemappingData.valueAt(i);
                    for (InputDeviceRemappingData data : deviceMap.values()) {
                        ipw.println("Device: " + data.deviceIdentifier());
                        ipw.increaseIndent();
                        Map<Integer, Integer> buttonRemaps = data.buttonRemappingMap();
                        if (buttonRemaps != null && !buttonRemaps.isEmpty()) {
                            ipw.println("Button Remappings:");
                            ipw.increaseIndent();
                            for (Map.Entry<Integer, Integer> remapEntry :
                                    buttonRemaps.entrySet()) {
                                ipw.println(KeyEvent.keyCodeToString(remapEntry.getKey()) + " -> "
                                        + KeyEvent.keyCodeToString(remapEntry.getValue()));
                            }
                            ipw.decreaseIndent();
                        }
                        Map<Integer, Integer> axisRemaps = data.axisRemappingMap();
                        if (axisRemaps != null && !axisRemaps.isEmpty()) {
                            ipw.println("Axis Remappings:");
                            ipw.increaseIndent();
                            for (Map.Entry<Integer, Integer> remapEntry :
                                    axisRemaps.entrySet()) {
                                ipw.println(MotionEvent.axisToString(remapEntry.getKey()) + " -> "
                                        + MotionEvent.axisToString(remapEntry.getValue()));
                            }
                            ipw.decreaseIndent();
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
