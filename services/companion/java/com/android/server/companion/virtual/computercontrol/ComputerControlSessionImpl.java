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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.companion.virtual.computercontrol.LifecycleState;
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
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArraySet;
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
import java.util.function.Consumer;
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

    private final Consumer<ComputerControlSessionImpl> mOnClosedListener;
    private final VirtualDevice mVirtualDevice;
    private final VirtualDisplay mVirtualDisplay;
    private final int mVirtualDisplayId;
    private final int mVirtualDeviceId;
    private final int mMainDisplayId;
    private final VirtualTouchscreen mVirtualTouchscreen;
    private final VirtualDpad mVirtualDpad;
    @Nullable
    private final VirtualKeyboard mVirtualKeyboard;
    private final ComputerControlAudioCapture mAudioCapture;
    private final ComputerControlAudioInjector mAudioInjector;
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final WindowManagerInternal mWindowManagerInternal;
    private final InputMethodManagerInternal mInputMethodManagerInternal;
    private final UserManagerInternal mUserManagerInternal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final InputManagerInternal mInputManagerInternal;
    private final DisplayManagerGlobal mDisplayManagerGlobal;
    private final ViewConfiguration mViewConfiguration;
    private final long mGlobalSessionTimeoutDurationMs;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;

    // Keeps track of the current lifecycle state. Thread safe.
    private final SessionLifecycle mLifecycle = new SessionLifecycle();

    @GuardedBy("mAllowlistedPackages")
    private final Set<String> mAllowlistedPackages = new ArraySet<>();

    // Handle state transitions for the session lifecycle.
    // NOTE: Do not make lifecycle transitions from these callbacks.
    private final ComputerControlSession.LifecycleCallback mStateTransitions =
            new ComputerControlSession.LifecycleCallback() {
                @Override
                public void onActive() {
                    // TODO: b/441475896 - Lock activity policy; Unblock input and display surface.
                }

                @Override
                public void onBlocked(@ComputerControlSession.SessionCloseReason int reason) {
                    cancelOngoingKeyGestures();
                    cancelOngoingTouchGestures();
                    // In the short term, we don't do anything special when entering the blocked
                    // state. The state exists to notify the client through the callback.
                    // TODO: b/441475896 - Block input and display surface; Unlock activity policy.
                }

                @Override
                public void onClosed(@ComputerControlSession.SessionCloseReason int reason) {
                    releaseResources();
                }
            };

    private final Object mNotificationLock = new Object();
    @GuardedBy("mNotificationLock")
    private NotificationInfo mNotificationInfo = null;

    private ScheduledFuture<?> mSwipeFuture;
    private ScheduledFuture<?> mInsertTextFuture;
    private ScheduledFuture<?> mCloseSessionFuture;

    ComputerControlSessionImpl(Context context, IBinder appToken,
            ComputerControlSessionParams params, AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Set<UserHandle> allowedUsers, Consumer<ComputerControlSessionImpl> onClosedListener) {
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
            Set<UserHandle> allowedUsers, Consumer<ComputerControlSessionImpl> onClosedListener) {
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
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mDisplayManagerGlobal = displayManagerGlobal;

        // TODO(b/440005498): Consider using the display from the app's context instead.
        mMainDisplayId = mUserManagerInternal.getMainDisplayAssignedToUser(
                mOwnerUser.getIdentifier());

        // This assumes that {@link ComputerControlSessionParams#getTargetPackageNames()}
        // never contains any packageNames that the session owner should never be able to
        // launch. This is validated in {@link ComputerControlSessionProcessor} prior to
        // creating the session.
        mAllowlistedPackages.addAll(mParams.getTargetPackageNames());
        mAllowlistedPackages.add(CUSTOM_BLOCKED_APP_PACKAGE);

        final VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(mParams.getName())
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS, DEVICE_POLICY_CUSTOM)
                .setAllowedUsers(allowedUsers)
                .build();

        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;

        final DisplayInfo mainDisplayInfo = mDisplayManagerGlobal.getDisplayInfo(mMainDisplayId);
        final int displayWidth = mainDisplayInfo.logicalWidth;
        final int displayHeight = mainDisplayInfo.logicalHeight;
        final VirtualDisplayConfig virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                mParams.getName() + "-display", displayWidth, displayHeight,
                mainDisplayInfo.logicalDensityDpi)
                .setFlags(displayFlags)
                .build();

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams);
            mVirtualDeviceId = mVirtualDevice.getDeviceId();
            mVirtualDevice.addActivityListener(mScheduler, new ComputerControlActivityListener());

            applyActivityPolicy();

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplay = Binder.withCleanCallingIdentity(() -> {
                VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                        virtualDisplayConfig, null, null);
                mWindowManagerInternal.setAnimationsDisabledForDisplay(
                        virtualDisplay.getDisplay().getDisplayId(), true);
                return virtualDisplay;
            });
            mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();

            if (Flags.computerControlShowTouches()) {
                mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
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
            mVirtualDpad = mVirtualDevice.createVirtualDpad(virtualDpadConfig);

            if (!android.companion.virtualdevice.flags.Flags.computerControlTyping()) {
                final String keyboardName = inputDeviceNamePrefix + "-kbrd";
                final VirtualKeyboardConfig virtualKeyboardConfig =
                        new VirtualKeyboardConfig.Builder()
                                .setAssociatedDisplayId(mVirtualDisplayId)
                                .setInputDeviceName(keyboardName)
                                .setVendorId(VENDOR_ID)
                                .setProductId(PRODUCT_ID_KEYBOARD)
                                .build();
                mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(virtualKeyboardConfig);
            } else {
                mVirtualKeyboard = null;
            }

            final String touchscreenName = inputDeviceNamePrefix + "-tscr";
            final VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(displayWidth, displayHeight)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_TOUCHSCREEN)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(virtualTouchscreenConfig);

            // Take control of the audio streams
            VirtualAudioDevice virtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                    mVirtualDisplay, null, null);
            mAudioInjector = new ComputerControlAudioInjector(virtualAudioDevice);
            mAudioInjector.startAudioInjection();
            mAudioCapture = new ComputerControlAudioCapture(virtualAudioDevice);
            mAudioCapture.startAudioCapture();

            mAppToken.linkToDeath(this, 0);
            startSessionCloseGlobalTimeout();

            mLifecycle.initializeLifecycle(mStateTransitions);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    int getDeviceId() {
        return mVirtualDeviceId;
    }

    IVirtualDisplayCallback getVirtualDisplayToken() {
        return mVirtualDisplay.getToken();
    }

    String getName() {
        return mParams.getName();
    }

    String getOwnerPackageName() {
        return mOwnerPackageName;
    }

    NotificationInfo getNotificationInfo() {
        synchronized (mNotificationLock) {
            return mNotificationInfo;
        }
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
            if (!(mLifecycle.getCurrentState() instanceof LifecycleState.Active)) {
                Slog.e(TAG, "Cannot launch application: Agent interaction is not available");
                return;
            }
            synchronized (mAllowlistedPackages) {
                mAllowlistedPackages.add(packageName);
            }
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
        final var mirror = new InteractiveMirrorImpl(mirrorSurface, mTransactionSupplier,
                mDisplayManagerGlobal.getDisplayInfo(mVirtualDisplayId), mInputManagerInternal);
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
        mLifecycle.setRemoteCallback(callback);
    }

    @Override
    public void attachNotificationInfo(int notificationId, String notificationTag) {
        synchronized (mNotificationLock) {
            if (mNotificationInfo != null) {
                throw new IllegalStateException("Notification info already set");
            }
            mNotificationInfo = new NotificationInfo(notificationId, notificationTag);
        }
    }

    @Override
    public void close() throws RemoteException {
        close(CLOSE_REASON_CALLER_INITIATED);
    }

    void close(@ComputerControlSession.SessionCloseReason int closeReason) {
        mLifecycle.updateLifecycleState((config) -> {
            if (config.mClosed != null) {
                return;
            }
            config.mClosed = new LifecycleState.Closed(closeReason);
        });
    }

    @Override
    public void binderDied() {
        try {
            close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void releaseResources() {
        cancelOngoingKeyGestures();
        cancelOngoingTouchGestures();
        cancelPendingCloseSession();
        mAudioInjector.stopAudioInjection();
        mAudioCapture.stopAudioCapture();
        mVirtualDevice.close(); // closes also the VirtualAudioDevice
        mAppToken.unlinkToDeath(this, 0);
        mOnClosedListener.accept(this);
    }

    private void applyActivityPolicy() throws RemoteException {
        List<String> exemptedPackageNames = new ArrayList<>();
        if (Flags.computerControlActivityPolicyStrict()) {
            mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

            exemptedPackageNames.addAll(mAllowlistedPackages);
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

    private void performSwipeStep(int fromX, int fromY, int toX, int toY, int step, int stepCount) {
        final double fraction = ((double) step) / stepCount;
        // This makes the movement distance smaller towards the end.
        final double easedFraction = Math.sin(fraction * Math.PI / 2);
        final int currentX = (int) (fromX + (toX - fromX) * easedFraction);
        final int currentY = (int) (fromY + (toY - fromY) * easedFraction);
        final int nextStep = step + 1;

        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(currentX, currentY, VirtualTouchEvent.ACTION_MOVE));

        if (nextStep > stepCount) {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(toX, toY, VirtualTouchEvent.ACTION_UP));
            mSwipeFuture = null;
            return;
        }

        mSwipeFuture = mScheduler.schedule(
                () -> performSwipeStep(fromX, fromY, toX, toY, nextStep, stepCount),
                TOUCH_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void performKeyStep(List<VirtualKeyEvent> keysToSend, int currStep) {
        if (mVirtualKeyboard == null) {
            return;
        }
        final int nextStep = currStep + 1;
        mVirtualKeyboard.sendKeyEvent(keysToSend.get(currStep));
        if (nextStep >= keysToSend.size()) {
            mInsertTextFuture = null;
            return;
        }

        mInsertTextFuture = mScheduler.schedule(
                () -> performKeyStep(keysToSend, nextStep), KEY_EVENT_DELAY_MS,
                TimeUnit.MILLISECONDS);
    }

    private void startSessionCloseGlobalTimeout() {
        mCloseSessionFuture = mScheduler.schedule(() -> close(CLOSE_REASON_SESSION_TIMED_OUT),
                mGlobalSessionTimeoutDurationMs, TimeUnit.MILLISECONDS);
    }

    private void cancelOngoingKeyGestures() {
        if (mInsertTextFuture != null) {
            mInsertTextFuture.cancel(false);
            mInsertTextFuture = null;
        }
    }

    private void cancelOngoingTouchGestures() {
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

    private static VirtualTouchEvent createTouchEvent(int x, int y,
            @VirtualTouchEvent.Action int action) {
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

    private static VirtualKeyEvent createKeyEvent(int keyCode, @VirtualKeyEvent.Action int action) {
        return new VirtualKeyEvent.Builder()
                .setAction(action)
                .setKeyCode(keyCode)
                .build();
    }

    private class ComputerControlActivityListener implements VirtualDeviceManager.ActivityListener {
        @Override
        public void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity) {
            Slog.v(TAG, "Top activity changed to " + topActivity);
            synchronized (mAllowlistedPackages) {
                mLifecycle.updateLifecycleState((config) -> {
                    if (config.mBlockedActivityVisible && mAllowlistedPackages.contains(
                            topActivity.getPackageName())) {
                        // In the short term, stay in the blocked state as long as content from the
                        // custom blocked dialog package is visible.
                        // TODO: b/441475896 - Remove this condition when we stop showing the
                        //  custom blocked dialog activity.
                        if (topActivity.getPackageName().equals(CUSTOM_BLOCKED_APP_PACKAGE)) {
                            return;
                        }
                        // The top activity is now no longer a blocked activity so unblock the
                        // session.
                        // TODO: b/441475896 - Do not rely only on the top activity, but took a
                        //  all running activities on the display.
                        config.mBlockedActivityVisible = false;
                    }
                });
            }
        }

        @Override
        public void onDisplayEmpty(int displayId) {
            Slog.v(TAG, "Display empty");
            mLifecycle.updateLifecycleState((config) -> {
                config.mBlockedActivityVisible = false;
                // We cannot currently assume all secure windows are gone when the display is empty.
                // Therefore mSecureWindowVisible cannot be update here for now.
                // TODO: b/449765707 - Investigate and update mSecureWindowVisible.
            });
        }

        @Override
        @SuppressLint("AndroidFrameworkRequiresPermission")
        public void onActivityLaunchBlocked(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user, IntentSender intentSender) {
            Slog.v(TAG, "Blocked activity launch for " + componentName + " on session "
                    + mParams.getName());
            final var changedState = mLifecycle.updateLifecycleState(
                    (config) -> config.mBlockedActivityVisible = true);
            if (Flags.computerControlBlockedState()
                    && !(changedState instanceof LifecycleState.Blocked)) {
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
        public void onSecureWindowShown(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user) {
            Slog.v(TAG, "Secure window shown for " + componentName);
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowVisible = true);
        }

        @Override
        public void onSecureWindowHidden(int displayId) {
            Slog.v(TAG, "Secure window hidden");
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowVisible = false);
        }
    }

    static final class NotificationInfo {
        private final int mNotificationId;
        private final String mNotificationTag;

        NotificationInfo(int notificationId, String notificationTag) {
            this.mNotificationId = notificationId;
            this.mNotificationTag = notificationTag;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NotificationInfo that = (NotificationInfo) o;
            return mNotificationId == that.mNotificationId
                    && Objects.equals(mNotificationTag, that.mNotificationTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mNotificationId, mNotificationTag);
        }
    }
}
