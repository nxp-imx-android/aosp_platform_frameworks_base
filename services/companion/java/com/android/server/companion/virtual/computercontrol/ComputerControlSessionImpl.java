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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;

import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
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
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.WindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A computer control session that encapsulates a {@link IVirtualDevice}. The device is created and
 * managed by the system, but it is still owned by the caller.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub
        implements IBinder.DeathRecipient {

    // Throttle swipe events to avoid misinterpreting them as a fling. Each swipe will
    // consist of a DOWN event, 10 MOVE events spread over 500ms, and an UP event.
    @VisibleForTesting
    static final int SWIPE_STEPS = 10;
    @VisibleForTesting
    static final long SWIPE_EVENT_DELAY_MS = 50L;

    private final IBinder mAppToken;
    private final ComputerControlSessionParams mParams;
    private final OnClosedListener mOnClosedListener;
    private final IVirtualDevice mVirtualDevice;
    private final int mVirtualDisplayId;
    private final IVirtualDisplayCallback mVirtualDisplayToken;
    private final IVirtualInputDevice mVirtualTouchscreen;
    private final IVirtualInputDevice mVirtualDpad;
    private final IVirtualInputDevice mVirtualKeyboard;
    private final AtomicInteger mMirrorDisplayCounter = new AtomicInteger(0);
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final Injector mInjector;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private ScheduledFuture<?> mSwipeFuture;

    ComputerControlSessionImpl(IBinder appToken, ComputerControlSessionParams params,
            AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            OnClosedListener onClosedListener, Injector injector) {
        mAppToken = appToken;
        mParams = params;
        mOnClosedListener = onClosedListener;
        mInjector = injector;

        final VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(mParams.getName())
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .build();
        final String permissionControllerPackage = mInjector.getPermissionControllerPackageName();
        final ActivityPolicyExemption permissionController =
                new ActivityPolicyExemption.Builder()
                        .setPackageName(permissionControllerPackage)
                        .build();

        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;
        if (mParams.isDisplayAlwaysUnlocked()) {
            displayFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        }

        // This is used as a death detection token to release the display upon app death. We're in
        // the system process, so this won't happen, but this is OK because we already do death
        // detection in the virtual device based on the app token and closing it will also release
        // the display.
        // The same applies to the input devices. We can't reuse the app token there because it's
        // used as a map key for the virtual input devices.
        mVirtualDisplayToken = new DisplayManagerGlobal.VirtualDisplayCallback(null, null);

        // If the client didn't provide a surface, use the default display dimensions and enable
        // the screenshot API.
        // TODO(b/439774796): Do not allow client-provided surface and dimensions.
        final VirtualDisplayConfig virtualDisplayConfig;
        if (params.getDisplaySurface() == null) {
            displayFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
            final DisplayInfo defaultDisplayInfo =
                    mInjector.getDisplayInfo(Display.DEFAULT_DISPLAY);
            mDisplayWidth = defaultDisplayInfo.logicalWidth;
            mDisplayHeight = defaultDisplayInfo.logicalHeight;
            virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                    mParams.getName() + "-display", mDisplayWidth, mDisplayHeight,
                    defaultDisplayInfo.logicalDensityDpi)
                    .setFlags(displayFlags)
                    .build();
        } else {
            mDisplayWidth = mParams.getDisplayWidthPx();
            mDisplayHeight = mParams.getDisplayHeightPx();
            virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                    mParams.getName() + "-display", mDisplayWidth, mDisplayHeight,
                    mParams.getDisplayDpi())
                    .setSurface(mParams.getDisplaySurface())
                    .setFlags(displayFlags)
                    .build();
        }

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams, new ComputerControlActivityListener());
            mVirtualDevice.addActivityPolicyExemption(permissionController);

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplayId = Binder.withCleanCallingIdentity(() -> {
                int displayId = mVirtualDevice.createVirtualDisplay(virtualDisplayConfig,
                        mVirtualDisplayToken);
                mInjector.disableAnimationsForDisplay(displayId);
                return displayId;
            });

            mVirtualDevice.setDisplayImePolicy(
                    mVirtualDisplayId, WindowManager.DISPLAY_IME_POLICY_HIDE);

            final String dpadName = mParams.getName() + "-dpad";
            final VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(
                    virtualDpadConfig, new Binder(dpadName));

            final String keyboardName = mParams.getName()  + "-keyboard";
            final VirtualKeyboardConfig virtualKeyboardConfig =
                    new VirtualKeyboardConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(keyboardName)
                            .build();
            mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(
                    virtualKeyboardConfig, new Binder(keyboardName));

            final String touchscreenName = mParams.getName() + "-touchscreen";
            final VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(mDisplayWidth, mDisplayHeight)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(
                    virtualTouchscreenConfig, new Binder(touchscreenName));

            mAppToken.linkToDeath(this, 0);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    IVirtualDisplayCallback getVirtualDisplayToken() {
        return mVirtualDisplayToken;
    }

    @Override
    public void tap(@FloatRange(from = 0.0, to = 1.0) float x,
            @FloatRange(from = 0.0, to = 1.0) float y) throws RemoteException {
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_UP));
    }

    @Override
    public void swipe(
            @FloatRange(from = 0.0, to = 1.0) float fromX,
            @FloatRange(from = 0.0, to = 1.0) float fromY,
            @FloatRange(from = 0.0, to = 1.0) float toX,
            @FloatRange(from = 0.0, to = 1.0) float toY) throws RemoteException {
        if (mSwipeFuture != null) {
            mSwipeFuture.cancel(false);
        }
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(fromX, fromY, VirtualTouchEvent.ACTION_DOWN));
        performSwipeStep(fromX, fromY, toX, toY, /* step= */ 0);
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
    @Nullable
    public IInteractiveMirrorDisplay createInteractiveMirrorDisplay(
            int width, int height, @NonNull Surface surface) throws RemoteException {
        Objects.requireNonNull(surface);
        DisplayInfo displayInfo = mInjector.getDisplayInfo(mVirtualDisplayId);
        if (displayInfo == null) {
            // The display we're trying to mirror is gone; likely the session is already closed.
            return null;
        }
        String name =
                mParams.getName() + "-display-mirror-" + mMirrorDisplayCounter.getAndIncrement();
        VirtualDisplayConfig virtualDisplayConfig =
                new VirtualDisplayConfig.Builder(name, width, height, displayInfo.logicalDensityDpi)
                        .setSurface(surface)
                        .setDisplayIdToMirror(mVirtualDisplayId)
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .build();
        return new InteractiveMirrorDisplayImpl(virtualDisplayConfig, mVirtualDevice);
    }

    @Override
    public void close() throws RemoteException {
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
        mVirtualDevice.close();
        mAppToken.unlinkToDeath(this, 0);
        mOnClosedListener.onClosed(asBinder());
    }

    @Override
    public void binderDied() {
        try {
            close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    VirtualTouchEvent createTouchEvent(float x, float y, @VirtualTouchEvent.Action int action) {
        return new VirtualTouchEvent.Builder()
                .setX(x * mDisplayWidth)
                .setY(y * mDisplayHeight)
                .setAction(action)
                .setPointerId(4)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setPressure(255)
                .setMajorAxisSize(1)
                .build();
    }

    private void performSwipeStep(float fromX, float fromY, float toX, float toY, int step) {
        final float fraction = ((float) step) / SWIPE_STEPS;
        // This makes the movement distance smaller towards the end.
        final float easedFraction = (float) Math.sin(fraction * Math.PI / 2);
        final float currentX = fromX + (toX - fromX) * easedFraction;
        final float currentY = fromY + (toY - fromY) * easedFraction;
        final int nextStep = step + 1;

        try {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(currentX, currentY, VirtualTouchEvent.ACTION_MOVE));

            if (nextStep > SWIPE_STEPS) {
                mVirtualTouchscreen.sendTouchEvent(
                        createTouchEvent(toX, toY, VirtualTouchEvent.ACTION_UP));
                mSwipeFuture = null;
                return;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mSwipeFuture = mScheduler.schedule(
                () -> performSwipeStep(fromX, fromY, toX, toY, nextStep),
                SWIPE_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
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

    /** Interface for listening for closing of sessions. */
    interface OnClosedListener {
        void onClosed(IBinder token);
    }

    @VisibleForTesting
    public static class Injector {
        private final PackageManager mPackageManager;
        private final WindowManagerInternal mWindowManagerInternal;

        Injector(PackageManager packageManager) {
            mPackageManager = packageManager;
            mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        }

        public String getPermissionControllerPackageName() {
            return mPackageManager.getPermissionControllerPackageName();
        }

        public DisplayInfo getDisplayInfo(int displayId) {
            final Display display = DisplayManagerGlobal.getInstance().getRealDisplay(displayId);
            if (display == null) {
                return null;
            }
            final DisplayInfo displayInfo = new DisplayInfo();
            display.getDisplayInfo(displayInfo);
            return displayInfo;
        }

        public void disableAnimationsForDisplay(int displayId) {
            mWindowManagerInternal.setAnimationsDisabledForDisplay(displayId, /* disabled= */ true);
        }
    }
}
