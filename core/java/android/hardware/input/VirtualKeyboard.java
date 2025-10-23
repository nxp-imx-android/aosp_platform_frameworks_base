/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.input;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

import java.io.Closeable;
import java.util.Objects;

/**
 * A virtual keyboard representing a key input mechanism on a remote device, such as a built-in
 * keyboard on a laptop, a software keyboard on a tablet, or a keypad on a TV remote control.
 *
 * <p>This registers an InputDevice that is interpreted like a physically-connected device and
 * dispatches received events to it.</p>
 *
 * @hide
 */
@SystemApi
public class VirtualKeyboard implements Closeable {

    private static final String TAG = "VirtualKeyboard";
    private static final int UNSUPPORTED_KEY_CODE = KeyEvent.KEYCODE_DPAD_CENTER;

    // This class is expected to be initiated with either a {@link IVirtualKeyboard} or
    // {@link IVirtualInputDevice}. Only one should be non-null at a time and is an error
    // if both are null.
    @Nullable
    private IVirtualKeyboard mVirtualKeyboard;
    @Nullable
    private IVirtualInputDevice mVirtualInputDevice;

    private final VirtualKeyboardConfig mConfig;

    /**
     * Constructor if creating a virtual keyboard with a {@link IVirtualKeyboard}.
     * @hide
     */
    public VirtualKeyboard(VirtualKeyboardConfig config, IVirtualKeyboard virtualKeyboard) {
        mConfig = config;
        mVirtualKeyboard = Objects.requireNonNull(virtualKeyboard);
    }

    /**
     * Constructor if creating a virtual keyboard with a {@link IVirtualInputDevice}.
     * @hide
     */
    public VirtualKeyboard(VirtualKeyboardConfig config, IVirtualInputDevice virtualDevice) {
        mConfig = config;
        mVirtualInputDevice = Objects.requireNonNull(virtualDevice);
    }

    /**
     * Sends a key event to the system.
     *
     * @param event the event to send
     */
    public void sendKeyEvent(@NonNull VirtualKeyEvent event) {
        try {
            // TODO(b/447298290): Move keycode validity checks to the service.
            if (UNSUPPORTED_KEY_CODE == event.getKeyCode()) {
                throw new IllegalArgumentException("Unsupported key code " + event.getKeyCode()
                        + " sent to a VirtualKeyboard input device.");
            }

            if (mVirtualKeyboard == null && mVirtualInputDevice == null) {
                throw new IllegalStateException("VirtualKeyboard is not properly initialized.");
            }

            if (mVirtualKeyboard != null && !mVirtualKeyboard.sendKeyEvent(event)) {
                Log.w(TAG, "Failed to send key event to virtual keyboard "
                        + mConfig.getInputDeviceName());
            } else if (mVirtualInputDevice != null && !mVirtualInputDevice.sendKeyEvent(event)) {
                Log.w(TAG, "Failed to send key event to virtual input device keyboard "
                        + mConfig.getInputDeviceName());
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the ID of the underlying input device.
     *
     * @return The input device id of this device.
     * @see InputDevice#getId()
     * @hide
     */
    @FlaggedApi(com.android.hardware.input.Flags.FLAG_CREATE_VIRTUAL_KEYBOARD_API)
    @SystemApi
    public int getInputDeviceId() {
        try {
            if (mVirtualKeyboard != null) {
                return mVirtualKeyboard.getInputDeviceId();
            } else if (mVirtualInputDevice != null) {
                return mVirtualInputDevice.getInputDeviceId();
            } else {
                throw new IllegalStateException("VirtualKeyboard is not properly initialized.");
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void close() {
        try {
            if (mVirtualKeyboard != null) {
                mVirtualKeyboard.close();
            } else if (mVirtualInputDevice != null) {
                mVirtualInputDevice.close();
            } else {
                throw new IllegalStateException("VirtualKeyboard is not properly initialized.");
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public String toString() {
        return mConfig.toString();
    }

    /**
     * @return The id of the {@link android.view.InputDevice} corresponding to this keyboard.
     * @hide
     */
    // TODO(b/423975806): Remove once the system api is unflagged
    @SuppressLint("UnflaggedApi") // @TestApi without associated feature.
    @TestApi
    public int getInputDeviceIdForTest() {
        try {
            if (mVirtualInputDevice != null) {
                return mVirtualInputDevice.getInputDeviceId();
            } else if (mVirtualKeyboard != null) {
                return getInputDeviceId();
            } else {
                throw new IllegalStateException("VirtualKeyboard is not properly initialized.");
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
