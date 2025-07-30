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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import java.util.Objects;

/**
 * A computer control session that encapsulates a {@link IVirtualDevice}. The device is created and
 * managed by the system, but it is still owned by the caller.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub {

    private final IBinder mAppToken;
    private final IVirtualDevice mVirtualDevice;
    private final int mVirtualDisplayId;
    private final IVirtualInputDevice mVirtualTouchscreen;
    private final IVirtualInputDevice mVirtualDpad;
    private final IVirtualInputDevice mVirtualKeyboard;

    ComputerControlSessionImpl(IBinder appToken, ComputerControlSessionParams params,
            AttributionSource attributionSource, PackageManager packageManager,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory) {
        mAppToken = appToken;
        VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(params.name)
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_RECENTS,
                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                .build();
        String permissionControllerPackage = packageManager.getPermissionControllerPackageName();
        ActivityPolicyExemption permissionController =
                new ActivityPolicyExemption.Builder()
                        .setPackageName(permissionControllerPackage)
                        .build();

        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;
        if (params.isDisplayAlwaysUnlocked) {
            displayFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        }

        // This is used as a death detection token to release the display upon app death. We're in
        // the system process, so this won't happen, but this is OK because we already do death
        // detection in the virtual device based on the app token and closing it will also release
        // the display.
        // The same applies to the input devices. We can't reuse the app token there because it's
        // used as a map key for the virtual input devices.
        IVirtualDisplayCallback virtualDisplayCallback =
                new DisplayManagerGlobal.VirtualDisplayCallback(null, null);

        VirtualDisplayConfig virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                params.name + "-display", params.displayWidthPx, params.displayHeightPx,
                params.displayDpi)
                .setSurface(params.displaySurface)
                .setFlags(displayFlags)
                .build();

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams, new ComputerControlActivityListener());
            mVirtualDevice.addActivityPolicyExemption(permissionController);

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplayId = Binder.withCleanCallingIdentity(() ->
                    mVirtualDevice.createVirtualDisplay(
                            virtualDisplayConfig, virtualDisplayCallback));

            String dpadName = params.name + "-dpad";
            VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(
                    virtualDpadConfig, new Binder(dpadName));

            String keyboardName = params.name  + "-keyboard";
            VirtualKeyboardConfig virtualKeyboardConfig =
                    new VirtualKeyboardConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(keyboardName)
                            .build();
            mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(
                    virtualKeyboardConfig, new Binder(keyboardName));

            String touchscreenName = params.name + "-touchscreen";
            VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(
                            params.displayWidthPx, params.displayHeightPx)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(
                    virtualTouchscreenConfig, new Binder(touchscreenName));

        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    @Override
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) throws RemoteException {
        mVirtualTouchscreen.sendTouchEvent(Objects.requireNonNull(event));
    }

    @Override
    public void sendKeyEvent(@NonNull VirtualKeyEvent event) throws RemoteException {
        if (VirtualDpad.isKeyCodeSupported(Objects.requireNonNull(event).getKeyCode())) {
            mVirtualDpad.sendKeyEvent(event);
        } else {
            mVirtualKeyboard.sendKeyEvent(event);
        }
    }

    @Override
    public int createMirrorDisplay(
            @NonNull VirtualDisplayConfig config, @NonNull IVirtualDisplayCallback callback)
            throws RemoteException {
        Objects.requireNonNull(config);
        Objects.requireNonNull(callback);
        // The config in the app process is only used for the name and the surface, so we can
        // replace it here to ensure it only has the allowed properties and we don't leak
        // capabilities by creating the display with a clean identity (which we need for the mirror
        // display creation permission).
        VirtualDisplayConfig internalConfig = new VirtualDisplayConfig.Builder(
                config.getName(), config.getWidth(), config.getHeight(), config.getDensityDpi())
                .setSurface(config.getSurface())
                .setDisplayIdToMirror(mVirtualDisplayId)
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                .build();
        return Binder.withCleanCallingIdentity(() ->
                mVirtualDevice.createVirtualDisplay(internalConfig, callback));

    }

    @Override
    public IVirtualInputDevice createMirrorDisplayTouchscreen(
            @NonNull VirtualTouchscreenConfig config) throws RemoteException {
        return mVirtualDevice.createVirtualTouchscreen(
                config, new Binder(config.getInputDeviceName()));
    }

    @Override
    public void close() throws RemoteException {
        mVirtualDevice.close();
    }

    private static class ComputerControlActivityListener
            extends IVirtualDeviceActivityListener.Stub {
        @Override
        public void onTopActivityChanged(int displayId, ComponentName topActivity,
                @UserIdInt int userId) {}

        @Override
        public void onDisplayEmpty(int displayId) {}

        @Override
        public void onActivityLaunchBlocked(int displayId, ComponentName componentName,
                UserHandle user, IntentSender intentSender) {}

        @Override
        public void onSecureWindowShown(int displayId, ComponentName componentName,
                UserHandle user) {}

        @Override
        public void onSecureWindowHidden(int displayId) {}
    }
}
