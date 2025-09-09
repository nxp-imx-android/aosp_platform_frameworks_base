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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityOptions;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;
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

    private static final String TAG = "ComputerControlSession";

    // Input device names are limited to 80 bytes, so keep the prefix shorter than that.
    private static final int MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH = 70;

    // Throttle swipe events to avoid misinterpreting them as a fling. Each swipe will
    // consist of a DOWN event, 10 MOVE events spread over 500ms, and an UP event.
    @VisibleForTesting
    static final int SWIPE_STEPS = 10;
    // Delay between consecutive touch events sent during a swipe or a long press gesture.
    @VisibleForTesting
    static final long TOUCH_EVENT_DELAY_MS = 50L;
    // Multiplier for the long press timeout to ensure it's registered as a long press,
    // as some applications might have slightly different thresholds.
    @VisibleForTesting
    static final float LONG_PRESS_TIMEOUT_MULTIPLIER = 1.5f;
    @VisibleForTesting
    static final long KEY_EVENT_DELAY_MS = 10L;

    private final IBinder mAppToken;
    private final ComputerControlSessionParams mParams;
    private final String mOwnerPackageName;
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
    private ScheduledFuture<?> mInsertTextFuture;

    ComputerControlSessionImpl(IBinder appToken, ComputerControlSessionParams params,
            AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            OnClosedListener onClosedListener, Injector injector) {
        mAppToken = appToken;
        mParams = params;
        mOwnerPackageName = attributionSource.getPackageName();
        mOnClosedListener = onClosedListener;
        mInjector = injector;

        final VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(mParams.getName())
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

            applyActivityPolicy();

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplayId = Binder.withCleanCallingIdentity(() -> {
                int displayId = mVirtualDevice.createVirtualDisplay(virtualDisplayConfig,
                        mVirtualDisplayToken);
                mInjector.disableAnimationsForDisplay(displayId);
                return displayId;
            });

            mVirtualDevice.setDisplayImePolicy(
                    mVirtualDisplayId, WindowManager.DISPLAY_IME_POLICY_HIDE);

            final String inputDeviceNamePrefix =
                    createInputDeviceNamePrefix(attributionSource.getPackageName());

            final String dpadName = inputDeviceNamePrefix + "-dpad";
            final VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(
                    virtualDpadConfig, new Binder(dpadName));

            final String keyboardName = inputDeviceNamePrefix + "-kbrd";
            final VirtualKeyboardConfig virtualKeyboardConfig =
                    new VirtualKeyboardConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(keyboardName)
                            .build();
            mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(
                    virtualKeyboardConfig, new Binder(keyboardName));

            final String touchscreenName = inputDeviceNamePrefix + "-tscr";
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

    /**
     * This assumes that {@link ComputerControlSessionParams#getTargetPackageNames()} never contains
     * any packageNames that the session owner should never be able to launch. This is validated in
     * {@link ComputerControlSessionProcessor} prior to creating the session.
     */
    private void applyActivityPolicy() throws RemoteException {
        List<String> exemptedPackageNames = new ArrayList<>();
        if (Flags.computerControlActivityPolicyStrict()) {
            mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

            exemptedPackageNames.addAll(mParams.getTargetPackageNames());
        } else {
            // TODO(b/439774796): Remove once v0 API is removed and the flag is rolled out.
            // This legacy policy allows all apps other than PermissionController to be automated.
            String permissionControllerPackage = mInjector.getPermissionControllerPackageName();
            exemptedPackageNames.add(permissionControllerPackage);
        }
        for (int i = 0; i < exemptedPackageNames.size(); i++) {
            String exemptedPackageName = exemptedPackageNames.get(i);
            mVirtualDevice.addActivityPolicyExemption(
                    new ActivityPolicyExemption.Builder()
                            .setPackageName(exemptedPackageName)
                            .build());
        }
    }

    @Override
    public int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    IVirtualDisplayCallback getVirtualDisplayToken() {
        return mVirtualDisplayToken;
    }

    String getName() {
        return mParams.getName();
    }

    String getOwnerPackageName() {
        return mOwnerPackageName;
    }

    public void launchApplication(@NonNull String packageName) {
        if (!mParams.getTargetPackageNames().contains(Objects.requireNonNull(packageName))) {
            throw new IllegalArgumentException(
                    "Package " + packageName + " is not allowed to be launched in this session.");
        }
        final UserHandle user = UserHandle.of(UserHandle.getUserId(Binder.getCallingUid()));
        Binder.withCleanCallingIdentity(() -> mInjector.launchApplicationOnDisplayAsUser(
                packageName, mVirtualDisplayId, user));
    }

    @Override
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_UP));
    }

    @Override
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int  toY) throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(fromX, fromY, VirtualTouchEvent.ACTION_DOWN));
        performSwipeStep(fromX, fromY, toX, toY, /* step= */ 0, SWIPE_STEPS);
    }

    @Override
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y)
            throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        int longPressStepCount =
                (int) Math.ceil(
                        (double) mInjector.getLongPressTimeoutMillis() / TOUCH_EVENT_DELAY_MS);
        performSwipeStep(x, y, x, y, /* step= */ 0, longPressStepCount);
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
    public void performAction(@ComputerControlSession.Action int actionCode)
            throws RemoteException {
        if (actionCode == ComputerControlSession.ACTION_GO_BACK) {
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN));
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP));
        } else {
            Slog.e(TAG, "Invalid action code for performAction: " + actionCode);
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

    @SuppressLint("WrongConstant")
    @Override
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        cancelOngoingKeyGestures();
        if (android.companion.virtualdevice.flags.Flags.computerControlTyping()) {
            // TODO(b/422134565): Implement Input connection based typing
        } else {
            KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            KeyEvent[] events = kcm.getEvents(text.toCharArray());

            if (events == null) {
                Slog.e(TAG, "Couldn't generate key events from the provided text");
                return;
            }
            List<VirtualKeyEvent> keysToSend = new ArrayList<>();
            if (replaceExisting) {
                keysToSend.add(
                        createKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_UP));
                keysToSend.add(
                        createKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, VirtualKeyEvent.ACTION_UP));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_DEL, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_DEL, VirtualKeyEvent.ACTION_UP));
            }

            for (KeyEvent event : events) {
                keysToSend.add(createKeyEvent(event.getKeyCode(), event.getAction()));
            }

            if (commit) {
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_ENTER, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_ENTER, VirtualKeyEvent.ACTION_UP));
            }
            performKeyStep(keysToSend, 0);
        }
    }

    @Override
    public void close() throws RemoteException {
        mVirtualDevice.close();
        mAppToken.unlinkToDeath(this, 0);
        mOnClosedListener.onClosed(this);
    }

    @Override
    public void binderDied() {
        try {
            close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    VirtualTouchEvent createTouchEvent(int x, int y, @VirtualTouchEvent.Action int action) {
        return new VirtualTouchEvent.Builder()
                .setX(x)
                .setY(y)
                .setAction(action)
                .setPointerId(4)
                .setToolType(
                        action == VirtualTouchEvent.ACTION_CANCEL
                                ? VirtualTouchEvent.TOOL_TYPE_PALM
                                : VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setPressure(255)
                .setMajorAxisSize(1)
                .build();
    }

    VirtualKeyEvent createKeyEvent(int keyCode, @VirtualKeyEvent.Action int action) {
        return new VirtualKeyEvent.Builder()
                .setAction(action)
                .setKeyCode(keyCode)
                .build();
    }

    private void performSwipeStep(int fromX, int fromY, int toX, int toY, int step, int stepCount) {
        final double fraction = ((double) step) / stepCount;
        // This makes the movement distance smaller towards the end.
        final double easedFraction = Math.sin(fraction * Math.PI / 2);
        final int currentX = (int) (fromX + (toX - fromX) * easedFraction);
        final int currentY = (int) (fromY + (toY - fromY) * easedFraction);
        final int nextStep = step + 1;

        try {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(currentX, currentY, VirtualTouchEvent.ACTION_MOVE));

            if (nextStep > stepCount) {
                mVirtualTouchscreen.sendTouchEvent(
                        createTouchEvent(toX, toY, VirtualTouchEvent.ACTION_UP));
                mSwipeFuture = null;
                return;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mSwipeFuture = mScheduler.schedule(
                () -> performSwipeStep(fromX, fromY, toX, toY, nextStep, stepCount),
                TOUCH_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void performKeyStep(List<VirtualKeyEvent> keysToSend, int currStep) {
        final int nextStep = currStep + 1;
        try {
            mVirtualKeyboard.sendKeyEvent(keysToSend.get(currStep));
            if (nextStep >= keysToSend.size()) {
                mInsertTextFuture = null;
                return;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mInsertTextFuture = mScheduler.schedule(
                () -> performKeyStep(keysToSend, nextStep), KEY_EVENT_DELAY_MS,
                TimeUnit.MILLISECONDS);
    }

    private void cancelOngoingKeyGestures() {
        if (mInsertTextFuture != null) {
            mInsertTextFuture.cancel(false);
            mInsertTextFuture = null;
        }
    }

    private void cancelOngoingTouchGestures() throws RemoteException {
        if (mSwipeFuture != null && mSwipeFuture.cancel(false)) {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(0, 0, VirtualTouchEvent.ACTION_CANCEL));
        }
    }

    private String createInputDeviceNamePrefix(String packageName) {
        final String prefix = packageName + ":" + mParams.getName();
        return (prefix.length() > MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                ? prefix.substring(prefix.length() - MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                : prefix;
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
        void onClosed(ComputerControlSessionImpl session);
    }

    @VisibleForTesting
    public static class Injector {
        private final Context mContext;
        private final PackageManager mPackageManager;
        private final WindowManagerInternal mWindowManagerInternal;

        Injector(Context context) {
            mContext = context;
            mPackageManager = mContext.getPackageManager();
            mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        }

        public String getPermissionControllerPackageName() {
            return mPackageManager.getPermissionControllerPackageName();
        }

        public void launchApplicationOnDisplayAsUser(String packageName, int displayId,
                UserHandle user) {
            Intent intent = mPackageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new IllegalArgumentException(
                        "Package " + packageName + " does not have a launcher activity.");
            }
            mContext.startActivityAsUser(intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(), user);
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

        public long getLongPressTimeoutMillis() {
            return (long) (ViewConfiguration.getLongPressTimeout() * LONG_PRESS_TIMEOUT_MULTIPLIER);
        }
    }
}
