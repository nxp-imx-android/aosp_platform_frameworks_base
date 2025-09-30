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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;

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
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
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
import android.view.DisplayInfo;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.internal.inputmethod.InputConnectionCommandHeader;
import com.android.server.LocalServices;
import com.android.server.input.InputManagerInternal;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A computer control session that encapsulates a {@link IVirtualDevice}. The device is created and
 * managed by the system, but it is still owned by the caller.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub
        implements IBinder.DeathRecipient {

    private static final String TAG = "ComputerControlSession";

    private static final long DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS =
            TimeUnit.MILLISECONDS.convert(360, TimeUnit.MINUTES);

    private static final String CUSTOM_BLOCKED_APP_PACKAGE = "com.android.virtualdevicemanager";

    private static final ComponentName CUSTOM_BLOCKED_APP_ACTIVITY = new ComponentName(
            CUSTOM_BLOCKED_APP_PACKAGE,
            CUSTOM_BLOCKED_APP_PACKAGE + ".NotifyComputerControlBlockedActivity");

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

    // Vendor and Product IDs for Computer Control virtual input devices.
    // These values are likely unique within the VIRTUAL bus type, but they are not
    // guaranteed to be globally unique forever.
    // TODO: b/443001754 - Remove setVendorId and setProductId in all input devices below,
    //   in favor of reporting dedicated Computer Control metrics.
    private static final int VENDOR_ID = 0x0000;
    @VisibleForTesting
    static final int PRODUCT_ID_DPAD = 0xCC01;
    @VisibleForTesting
    static final int PRODUCT_ID_KEYBOARD = 0xCC02;
    @VisibleForTesting
    static final int PRODUCT_ID_TOUCHSCREEN = 0xCC03;

    private final Context mContext;
    private final IBinder mAppToken;
    private final ComputerControlSessionParams mParams;

    private final UserHandle mOwnerUser;
    private final Context mOwnerContext;
    private final String mOwnerPackageName;

    private final OnClosedListener mOnClosedListener;
    private final IVirtualDevice mVirtualDevice;
    private final int mVirtualDisplayId;
    private final int mVirtualDeviceId;
    private final int mMainDisplayId;
    private final IVirtualDisplayCallback mVirtualDisplayToken;
    private final IVirtualInputDevice mVirtualTouchscreen;
    private final IVirtualInputDevice mVirtualDpad;
    private final IVirtualInputDevice mVirtualKeyboard;
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final WindowManagerInternal mWindowManagerInternal;
    private final InputMethodManagerInternal mInputMethodManagerInternal;
    private final UserManagerInternal mUserManagerInternal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final ViewConfiguration mViewConfiguration;
    private final long mGlobalSessionTimeoutDurationMs;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;

    private ScheduledFuture<?> mSwipeFuture;
    private ScheduledFuture<?> mInsertTextFuture;
    private ScheduledFuture<?> mCloseSessionFuture;

    private final Object mLifecycleCallbackLock = new Object();
    @GuardedBy("mLifecycleCallbackLock")
    private IComputerControlLifecycleCallback mLifecycleCallback;

    ComputerControlSessionImpl(Context context, IBinder appToken,
            ComputerControlSessionParams params, AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Set<UserHandle> allowedUsers, OnClosedListener onClosedListener) {
        this(context, DisplayManagerGlobal.getInstance(), ViewConfiguration.get(context),
                DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS, SurfaceControl.Transaction::new,
                appToken, params, attributionSource, virtualDeviceFactory, allowedUsers,
                onClosedListener);
    }

    @VisibleForTesting
    ComputerControlSessionImpl(Context context, DisplayManagerGlobal displayManagerGlobal,
            ViewConfiguration viewConfiguration, long globalSessionTimeoutDurationMs,
            Supplier<SurfaceControl.Transaction> transactionSupplier, IBinder appToken,
            ComputerControlSessionParams params, AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Set<UserHandle> allowedUsers, OnClosedListener onClosedListener) {
        mContext = context;
        mViewConfiguration = viewConfiguration;
        mGlobalSessionTimeoutDurationMs = globalSessionTimeoutDurationMs;
        mTransactionSupplier = transactionSupplier;
        mAppToken = appToken;
        mParams = params;

        mOwnerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        mOwnerContext = context.createContextAsUser(mOwnerUser, /* flags = */ 0);
        mOwnerPackageName = attributionSource.getPackageName();

        mOnClosedListener = onClosedListener;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mInputMethodManagerInternal = LocalServices.getService(
                InputMethodManagerInternal.class);
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mActivityTaskManagerInternal = LocalServices.getService(
                ActivityTaskManagerInternal.class);

        // TODO(b/440005498): Consider using the display from the app's context instead.
        mMainDisplayId = mUserManagerInternal.getMainDisplayAssignedToUser(
                mOwnerUser.getIdentifier());

        final VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(mParams.getName())
                .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS, DEVICE_POLICY_CUSTOM)
                .setAllowedUsers(allowedUsers)
                .build();

        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;

        // This is used as a death detection token to release the display upon app death. We're in
        // the system process, so this won't happen, but this is OK because we already do death
        // detection in the virtual device based on the app token and closing it will also release
        // the display.
        // The same applies to the input devices. We can't reuse the app token there because it's
        // used as a map key for the virtual input devices.
        mVirtualDisplayToken = new DisplayManagerGlobal.VirtualDisplayCallback(null, null);

        final DisplayInfo mainDisplayInfo = displayManagerGlobal.getDisplayInfo(mMainDisplayId);
        final int displayWidth = mainDisplayInfo.logicalWidth;
        final int displayHeight = mainDisplayInfo.logicalHeight;
        final VirtualDisplayConfig virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                mParams.getName() + "-display", displayWidth, displayHeight,
                mainDisplayInfo.logicalDensityDpi)
                .setFlags(displayFlags)
                .build();

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams, new ComputerControlActivityListener());
            mVirtualDeviceId = mVirtualDevice.getDeviceId();

            applyActivityPolicy();

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplayId = Binder.withCleanCallingIdentity(() -> {
                int displayId = mVirtualDevice.createVirtualDisplay(virtualDisplayConfig,
                        mVirtualDisplayToken);
                mWindowManagerInternal.setAnimationsDisabledForDisplay(displayId, true);
                return displayId;
            });

            if (Flags.computerControlShowTouches()) {
                InputManagerInternal inputManagerInternal = LocalServices.getService(
                        InputManagerInternal.class);
                inputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                        true /* enabled */);
            }

            mVirtualDevice.setDisplayImePolicy(
                    mVirtualDisplayId, WindowManager.DISPLAY_IME_POLICY_HIDE);

            final String inputDeviceNamePrefix =
                    createInputDeviceNamePrefix(attributionSource.getPackageName());

            final String dpadName = inputDeviceNamePrefix + "-dpad";
            final VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_DPAD)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(
                    virtualDpadConfig, new Binder(dpadName));

            final String keyboardName = inputDeviceNamePrefix + "-kbrd";
            final VirtualKeyboardConfig virtualKeyboardConfig =
                    new VirtualKeyboardConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(keyboardName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_KEYBOARD)
                            .build();
            mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(
                    virtualKeyboardConfig, new Binder(keyboardName));

            final String touchscreenName = inputDeviceNamePrefix + "-tscr";
            final VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(displayWidth, displayHeight)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_TOUCHSCREEN)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(
                    virtualTouchscreenConfig, new Binder(touchscreenName));

            mAppToken.linkToDeath(this, 0);
            startSessionCloseGlobalTimeout();
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
            exemptedPackageNames.add(CUSTOM_BLOCKED_APP_PACKAGE);
        } else {
            // This legacy policy allows all apps other than PermissionController to be automated.
            String permissionControllerPackage =
                    mContext.getPackageManager().getPermissionControllerPackageName();
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

    public int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    int getDeviceId() {
        return mVirtualDeviceId;
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

    @Override
    @SuppressLint("AndroidFrameworkRequiresPermission")
    public void launchApplication(@NonNull String packageName, @Nullable String className)
            throws RemoteException {
        final Intent intent = getLaunchIntent(packageName, className);
        if (intent == null) {
            throw new IllegalArgumentException(
                    "Could not find launcher activity for " + packageName + "/" + className);
        }
        if (Flags.computerControlActivityPolicyStrict()) {
            // TODO(b/444600407): Remove this once the consent model is per-target app. While the
            // consent is general, the caller can extend the list of target packages dynamically.
            mVirtualDevice.addActivityPolicyExemption(
                    new ActivityPolicyExemption.Builder().setPackageName(packageName).build());
        }
        Binder.withCleanCallingIdentity(() ->
                mContext.startActivityAsUser(intent,
                        ActivityOptions.makeBasic()
                                .setLaunchDisplayId(mVirtualDisplayId).toBundle(), mOwnerUser));
    }

    @Override
    public void handOverApplications() {
        Binder.withCleanCallingIdentity(
                () -> moveAllTasks(mVirtualDisplayId, mMainDisplayId));
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
                        (double) mViewConfiguration.getLongPressTimeoutMillis() *
                                LONG_PRESS_TIMEOUT_MULTIPLIER / TOUCH_EVENT_DELAY_MS);
        performSwipeStep(x, y, x, y, /* step= */ 0, longPressStepCount);
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
    public IInteractiveMirror createInteractiveMirror(SurfaceControl outMirrorSurface)
            throws RemoteException {
        final var mirrorSurface =
                mWindowManagerInternal.createMirrorForDisplayContent(mVirtualDisplayId);
        if (mirrorSurface == null) {
            return null;
        }
        outMirrorSurface.copyFrom(mirrorSurface,
                "ComputerControlSessionImpl#createInteractiveMirrorDisplay");
        final var mirror = new InteractiveMirrorImpl(mirrorSurface, mTransactionSupplier);
        mirror.setInteractive(InteractiveMirror.DEFAULT_INTERACTIVE);
        return mirror;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        cancelOngoingKeyGestures();
        if (android.companion.virtualdevice.flags.Flags.computerControlTyping()) {
            IRemoteComputerControlInputConnection ic = getInputConnection(mVirtualDisplayId);
            if (ic == null) {
                Slog.e(TAG, "Unable to insert text: No input connection found!");
                return;
            }
            // TODO(b/422134565): Implement client invoker logic to pass the correct session id when
            //  "client text view" invalidates input while view remains focused.
            //  Currently, if we set text using A11y nodes or the application sets text into the
            //  text field outside of input connection (while text view is focused), CC session will
            //  no longer be able to insert text until the text view restarts the input connection.
            try {
                if (replaceExisting) {
                    ic.replaceText(new InputConnectionCommandHeader(0), 0 /* start */,
                            Integer.MAX_VALUE /* end */, text, 1 /* newCursorPosition */);
                } else {
                    ic.commitText(new InputConnectionCommandHeader(0), text,
                            1 /* newCursorPosition */);
                }
                // TODO(b/422134565): Use right editor action to commit text instead key enter
                if (commit) {
                    ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to insert text through InputConnection", e);
            }
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
    public void setLifecycleCallback(IComputerControlLifecycleCallback callback) {
        synchronized (mLifecycleCallbackLock) {
            mLifecycleCallback = callback;
        }
    }

    @Override
    public void close() throws RemoteException {
        close(CLOSE_REASON_CALLER_INITIATED);
    }

    void close(@ComputerControlSession.SessionCloseReason int closeReason)
            throws RemoteException {
        releaseResources();
        synchronized (mLifecycleCallbackLock) {
            if (mLifecycleCallback != null) {
                try {
                    mLifecycleCallback.onClosed(closeReason);
                    mLifecycleCallback = null;
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to send LifeCycleCallback#onClosed");
                }
            }
        }
    }

    private void releaseResources() throws RemoteException {
        cancelOngoingKeyGestures();
        cancelOngoingTouchGestures();
        cancelPendingCloseSession();
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

    private void startSessionCloseGlobalTimeout() {
        mCloseSessionFuture = mScheduler.schedule(() -> {
            try {
                close(CLOSE_REASON_SESSION_TIMED_OUT);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        },
        mGlobalSessionTimeoutDurationMs, TimeUnit.MILLISECONDS);
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

    private void cancelPendingCloseSession() {
        if (mCloseSessionFuture != null) {
            mCloseSessionFuture.cancel(false);
            mCloseSessionFuture = null;
        }
    }

    private String createInputDeviceNamePrefix(String packageName) {
        final String prefix = packageName + ":" + mParams.getName();
        return (prefix.length() > MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                ? prefix.substring(prefix.length() - MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                : prefix;
    }

    private class ComputerControlActivityListener extends IVirtualDeviceActivityListener.Stub {
        @Override
        public void onTopActivityChanged(int displayId, ComponentName topActivity,
                @UserIdInt int userId) {}

        @Override
        public void onDisplayEmpty(int displayId) {}

        @Override
        @SuppressLint("AndroidFrameworkRequiresPermission")
        public void onActivityLaunchBlocked(int displayId, ComponentName componentName,
                UserHandle user, IntentSender intentSender) {
            Slog.d(TAG, "Blocked activity launch for " + componentName + " on session "
                    + mParams.getName());
            if (Objects.equals(CUSTOM_BLOCKED_APP_ACTIVITY, componentName)) {
                return;
            }
            Intent intent = new Intent()
                    .setComponent(CUSTOM_BLOCKED_APP_ACTIVITY)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, componentName);
            mContext.startActivityAsUser(
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
                    UserHandle.SYSTEM);
        }

        @Override
        public void onSecureWindowShown(int displayId, ComponentName componentName,
                UserHandle user) {}

        @Override
        public void onSecureWindowHidden(int displayId) {}
    }

    /** Interface for listening for closing of sessions. */
    interface OnClosedListener {
        void onClosed(@NonNull ComputerControlSessionImpl session);
    }

    private Intent getLaunchIntent(String packageName, String className) {
        if (className == null) {
            return mOwnerContext.getPackageManager().getLaunchIntentForPackage(packageName);
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(packageName, className);
        final List<ResolveInfo> resolveInfos = mOwnerContext.getPackageManager()
                .queryIntentActivities(intent, ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        if (resolveInfos.isEmpty()) {
            return null;
        }
        return intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private IRemoteComputerControlInputConnection getInputConnection(int displayId) {
        // getUserAssignedToDisplay returns the main userId, if we want to support cross
        // profile CC interactions and typing on CC display, we need to find the right user
        // profile here for the CC input connection
        return mInputMethodManagerInternal.getComputerControlInputConnection(
                mUserManagerInternal.getUserAssignedToDisplay(displayId), displayId);
    }

    private void moveAllTasks(int fromDisplayId, int toDisplayId) {
        mActivityTaskManagerInternal.moveAllTasks(fromDisplayId, toDisplayId);
    }
}
