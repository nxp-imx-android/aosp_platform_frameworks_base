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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_EMPTY;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_USER_INITIATED;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtual.computercontrol.LifecycleState;
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
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.internal.inputmethod.InputConnectionCommandHeader;
import com.android.server.FgThread;
import com.android.server.LocalServices;
import com.android.server.appinteraction.AppInteractionService;
import com.android.server.input.InputManagerInternal;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A computer control session that encapsulates a {@link android.companion.virtual.IVirtualDevice}.
 * The device is created and managed by the system, but it is still owned by the caller.
 *
 * NOTE: Lock ordering precedence: The hierarchy of locks defined in this file is determined by the
 * order in which the locks are declared. If two locks need to be acquired at once, the lock
 * declared earlier in the file needs to be acquired first.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub
        implements IBinder.DeathRecipient, AppOpsManager.OnOpChangedListener {

    private static final String TAG = "ComputerControlSession";
    private static final int TRACE_COOKIE_SESSION = 0;
    private static final int TRACE_COOKIE_WINDOW_DRAW = 1;

    private static final long DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS =
            TimeUnit.MILLISECONDS.convert(360, TimeUnit.MINUTES);

    // Timeout for waiting for all windows on the display to be drawn before taking a screenshot.
    private static final int WINDOW_DRAW_TIMEOUT_MS = 1000;

    // Input device names are limited to 80 bytes, so keep the prefix shorter than that.
    private static final int MAX_INPUT_DEVICE_NAME_PREFIX_BYTES = 70;

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
    static final long KEY_EVENT_DELAY_MS = 50L;
    // The session will be closed whenever the display remains empty for this timeout period.
    // This timeout is used to avoid closing the session immediately upon the display being empty
    // to allow for transient cases of emptiness, like when an Activity is launched in a new task
    // while the current task is finished.
    @VisibleForTesting
    static final long CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS = 100L;

    // Vendor and Product IDs for Computer Control virtual input devices.
    // These values are likely unique within the VIRTUAL bus type, but they are not
    // guaranteed to be globally unique forever.
    // TODO: b/443001754 - Remove setVendorId and setProductId in all input devices below,
    //   in favor of reporting dedicated Computer Control metrics.
    private static final int VENDOR_ID = 0x0000;
    @VisibleForTesting
    static final int PRODUCT_ID_DPAD = 0xCC01;
    @VisibleForTesting
    static final int PRODUCT_ID_TOUCHSCREEN = 0xCC03;

    private final String mTraceTrack = "ComputerControlSessionImpl#"
            + System.identityHashCode(this);

    private final IBinder mAppToken;
    private final ComputerControlSessionParams mParams;
    private final IApplicationThread mAppThread;
    private final String mAttributionTag;

    private final UserHandle mOwnerUser;
    private final String mOwnerPackageName;
    private final Context mOwnerContext;

    private final Consumer<ComputerControlSessionImpl> mOnClosedListener;
    private final VirtualDevice mVirtualDevice;
    // The VirtualDisplay is owned by the system and its token must not be leaked to the client.
    private final VirtualDisplay mVirtualDisplay;
    private final int mVirtualDisplayId;
    private final int mVirtualDeviceId;
    private final int mMainDisplayId;
    private final VirtualTouchscreen mVirtualTouchscreen;
    private final VirtualDpad mVirtualDpad;
    private final ComputerControlAudioCapture mAudioCapture;
    private final ComputerControlAudioInjector mAudioInjector;

    @Override
    protected void dump(@androidx.annotation.NonNull FileDescriptor fd,
            @androidx.annotation.NonNull PrintWriter fout,
            @androidx.annotation.Nullable String[] args) {
        String indent = "        ";
        fout.print(indent);

        fout.print("ComupterControlSession {");
        fout.print(" mDeviceId=" + mVirtualDeviceId);
        fout.print(" mName=" + mParams.getName());
        fout.print(" mTargetExtensionVersion=" + mParams.getTargetExtensionVersion());
        fout.print(" mOwnerPackageName=" + mOwnerPackageName);
        fout.print(" mTargetPackageNames=" + mParams.getTargetPackageNames());
        fout.print(" mAppInteractionAttribution=" + mParams.getAppInteractionAttribution());
        fout.print("}");
        fout.print("\n");
    }

    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();
    /** Executor for the shared FgThread. */
    private final Executor mFgThreadExecutor;

    private final PackageManager mOwnerPackageManager;
    private final AppOpsManager mAppOpsManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private final InputMethodManagerInternal mInputMethodManagerInternal;
    private final UserManagerInternal mUserManagerInternal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final InputManagerInternal mInputManagerInternal;
    private final DisplayManagerGlobal mDisplayManagerGlobal;
    private final ViewConfiguration mViewConfiguration;
    private final long mGlobalSessionTimeoutDurationMs;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private final ComputerControlAllowlistController mAllowlistController;
    private final ComputerControlStatsController mStatsController;
    @Nullable private final AppInteractionService mAppInteractionService;

    @GuardedBy("mAllowlistedPackages")
    private final Set<String> mAllowlistedPackages = new ArraySet<>();

    // Handle state transitions for the session lifecycle.
    // NOTE: Do not make lifecycle transitions from these callbacks.
    private final ComputerControlSession.LifecycleCallback mStateTransitions =
            new ComputerControlSession.LifecycleCallback() {
                @Override
                public void onActive() {
                    mStatsController.onSessionActive();
                }

                @Override
                public void onBlocked(@ComputerControlSession.SessionBlockReason int reason,
                        @Nullable String blockingPackage) {
                    cancelOngoingInteractions();
                    mStatsController.onSessionBlocked(reason);
                }

                @Override
                public void onClosed(@ComputerControlSession.SessionCloseReason int reason) {
                    releaseResources();
                    mStatsController.onSessionClosed(reason);
                    Trace.asyncTraceForTrackEnd(mTraceTrack, TRACE_COOKIE_SESSION);
                }
            };

    // Keeps track of the current lifecycle state. Thread safe.
    private final SessionLifecycle mLifecycle = new SessionLifecycle(mStateTransitions);

    private final Object mNotificationLock = new Object();
    @GuardedBy("mNotificationLock")
    @Nullable
    private NotificationInfo mNotificationInfo = null;
    private final Object mPreviewIntentLock = new Object();
    @GuardedBy("mPreviewIntentLock")
    @Nullable
    private PendingIntent mPreviewIntent = null;

    @GuardedBy("mInteractiveMirrors")
    // A list of active interactive mirrors. The presence of mirrors indicates foreground
    // automation, which enables touch visualization.
    private final List<InteractiveMirrorImpl> mInteractiveMirrors = new ArrayList<>();

    @Nullable
    private ScheduledFuture<?> mSwipeFuture;
    @Nullable
    private ScheduledFuture<?> mInsertTextFuture;
    @Nullable
    private ScheduledFuture<?> mCloseSessionFuture;
    @Nullable
    private ScheduledFuture<?> mDisplayEmptyScheduledAction;
    @Nullable
    private Surface mClientSurface;

    // Whether this is a session only intended for testing ComputerControl functionality.
    private final boolean mIsTestSession;

    private final Object mWindowDrawLock = new Object();
    // Whether a window draw as a result of a screenshot request is in progress.
    @GuardedBy("mWindowDrawLock")
    private boolean mIsWaitingForWindowDraw = false;

    ComputerControlSessionImpl(Context context,
            ComputerControlAllowlistController allowlistController, IBinder appToken,
            ComputerControlSessionParams params, IApplicationThread appThread,
            AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Consumer<ComputerControlSessionImpl> onClosedListener) {
        this(context, DisplayManagerGlobal.getInstance(), allowlistController,
                ViewConfiguration.get(context), DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS,
                SurfaceControl.Transaction::new, appToken, params, appThread, attributionSource,
                virtualDeviceFactory, onClosedListener, FgThread.getExecutor());
    }

    @VisibleForTesting
    ComputerControlSessionImpl(Context context, DisplayManagerGlobal displayManagerGlobal,
            ComputerControlAllowlistController allowlistController,
            ViewConfiguration viewConfiguration, long globalSessionTimeoutDurationMs,
            Supplier<SurfaceControl.Transaction> transactionSupplier, IBinder appToken,
            ComputerControlSessionParams params, IApplicationThread appThread,
            AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Consumer<ComputerControlSessionImpl> onClosedListener, Executor fgThreadExecutor) {
        Trace.asyncTraceForTrackBegin(mTraceTrack, "Session", TRACE_COOKIE_SESSION);
        mFgThreadExecutor = fgThreadExecutor;
        mViewConfiguration = viewConfiguration;
        mGlobalSessionTimeoutDurationMs = globalSessionTimeoutDurationMs;
        mTransactionSupplier = transactionSupplier;
        mAppToken = appToken;
        mParams = params;
        mAllowlistController = allowlistController;
        mPreviewIntent = params.getPreviewIntent();
        mAppThread = appThread;
        mAttributionTag = attributionSource.getAttributionTag();

        mOwnerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        mOwnerContext = context.createContextAsUser(mOwnerUser, /* flags = */ 0);
        mOwnerPackageName = attributionSource.getPackageName();
        mOwnerPackageManager = mOwnerContext.getPackageManager();

        mIsTestSession = mAllowlistController.isTestAgent(attributionSource.getUid(),
                mOwnerPackageName, mOwnerPackageManager);

        mOnClosedListener = onClosedListener;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mInputMethodManagerInternal = LocalServices.getService(
                InputMethodManagerInternal.class);
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mActivityTaskManagerInternal = LocalServices.getService(
                ActivityTaskManagerInternal.class);
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mDisplayManagerGlobal = displayManagerGlobal;
        mStatsController = new ComputerControlStatsController(
                context.getPackageManager(), attributionSource, params);
        if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            mAppInteractionService = LocalServices.getService(AppInteractionService.class);
        } else {
            mAppInteractionService = null;
        }

        // TODO(b/469400179): Consider using the display from the app's context instead.
        mMainDisplayId = mUserManagerInternal.getMainDisplayAssignedToUser(
                mOwnerUser.getIdentifier());

        // This assumes that {@link ComputerControlSessionParams#getTargetPackageNames()}
        // never contains any packageNames that the session owner should never be able to
        // launch. This is validated in {@link ComputerControlSessionProcessor} prior to
        // creating the session.
        mAllowlistedPackages.addAll(mParams.getTargetPackageNames());

        final VirtualDeviceParams.Builder virtualDeviceParamsBuilder =
                new VirtualDeviceParams.Builder()
                    .setName(mParams.getName())
                    .setLocalDeviceOnly(true)
                    .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                    .setDevicePolicy(POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS,
                            DEVICE_POLICY_CUSTOM)
                    .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM);
        final VirtualDeviceParams virtualDeviceParams = virtualDeviceParamsBuilder.build();

        final VirtualDisplayConfig virtualDisplayConfig = createSessionDisplayConfig(
                mParams.getName() + "-display",
                mDisplayManagerGlobal.getDisplayInfo(mMainDisplayId));
        final int displayWidth = virtualDisplayConfig.getWidth();
        final int displayHeight = virtualDisplayConfig.getHeight();

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams);
            mVirtualDeviceId = mVirtualDevice.getDeviceId();
            mVirtualDevice.addActivityListener(mScheduler, new ComputerControlActivityListener());

            // Create the display with a clean identity so it can be trusted. The virtual display's
            // token must not be leaked to the client.
            mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                    virtualDisplayConfig, null, null);
            mWindowManagerInternal.setAnimationsDisabledForDisplay(
                    mVirtualDisplay.getDisplay().getDisplayId(), true);
            mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
            mWindowManagerInternal.enablePowerOptimizations(mVirtualDisplayId, /* enable = */ true);
            mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                    mVirtualDisplayId, /* enable = */true);

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
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mAppOpsManager = mOwnerContext.getSystemService(AppOpsManager.class);
        mAppOpsManager.startWatchingMode(AppOpsManager.OP_COMPUTER_CONTROL, mOwnerPackageName,
                this);
    }

    /**
     * Create the session's display to have the same size and density as that of the main display
     * when it is in its natural orientation.
     */
    private static VirtualDisplayConfig createSessionDisplayConfig(String name,
            DisplayInfo mainDisplayInfo) {
        final int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;

        final int displayWidth;
        final int displayHeight;
        if (mainDisplayInfo.rotation == Surface.ROTATION_90
                || mainDisplayInfo.rotation == Surface.ROTATION_270) {
            displayWidth = mainDisplayInfo.logicalHeight;
            displayHeight = mainDisplayInfo.logicalWidth;
        } else {
            displayWidth = mainDisplayInfo.logicalWidth;
            displayHeight = mainDisplayInfo.logicalHeight;
        }

        return new VirtualDisplayConfig.Builder(
                name, displayWidth, displayHeight,
                mainDisplayInfo.logicalDensityDpi)
                .setFlags(displayFlags)
                .setIgnoreActivitySizeRestrictions(true)
                .build();
    }

    int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    int getDeviceId() {
        return mVirtualDeviceId;
    }

    String getName() {
        return mParams.getName();
    }

    String getOwnerPackageName() {
        return mOwnerPackageName;
    }

    @Nullable
    NotificationInfo getNotificationInfo() {
        synchronized (mNotificationLock) {
            return mNotificationInfo;
        }
    }

    PackageManager getPackageManager() {
        return mOwnerPackageManager;
    }

    KeyguardManager getKeyguardManager() {
        return mOwnerContext.getSystemService(KeyguardManager.class);
    }

    boolean isTestSession() {
        return mIsTestSession;
    }

    @Override
    public void initialize(IComputerControlLifecycleCallback callback, Surface clientSurface) {
        if (mClientSurface != null) {
            throw new IllegalStateException("Client surface is already initialized");
        }
        mClientSurface = clientSurface;
        mVirtualDisplay.setSurface(mClientSurface);

        mLifecycle.initializeWithRemoteCallback(callback);
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
        if (!mAllowlistController.isPackageAutomatable(packageName, this)) {
            throw new IllegalArgumentException(
                    "Trying to launch " + packageName + " which is not allowlisted");
        }

        // TODO(b/444600407): Remove this once the consent model is per-target app. While the
        // consent is general, the caller can extend the list of target packages dynamically.
        if (!(mLifecycle.getCurrentState() instanceof LifecycleState.Active)) {
            Slog.e(TAG, "Cannot launch application: Agent interaction is not available");
            return;
        }
        cancelOngoingInteractions();
        synchronized (mAllowlistedPackages) {
            mAllowlistedPackages.add(packageName);
        }
        // If we block input and screenshots in the blocked state, we simply allow all
        // activities to launch. We detect blocked state automatically when an activity
        // launch request comes in for a package that's not allowed to launch.
        final Bundle options =
                ActivityOptions.makeBasic().setLaunchDisplayId(mVirtualDisplayId).toBundle();
        Binder.withCleanCallingIdentity(() -> mActivityTaskManagerInternal.startActivityAsUser(
                mAppThread, mOwnerPackageName, mAttributionTag, intent, null,
                Intent.FLAG_ACTIVITY_NEW_TASK, options, mOwnerUser.getIdentifier()));
        mStatsController.onApplicationLaunched(packageName);
    }

    @Override
    public void handOverApplications() {
        Binder.withCleanCallingIdentity(
                () -> moveAllTasks(mVirtualDisplayId, mMainDisplayId));
        close(CLOSE_REASON_SESSION_EMPTY);
    }

    @Override
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) throws RemoteException {
        if (shouldDisallowInteractions("tap")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_UP));
        mStatsController.onTap();
    }

    @Override
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int  toY) throws RemoteException {
        if (shouldDisallowInteractions("swipe")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(fromX, fromY, VirtualTouchEvent.ACTION_DOWN));
        performSwipeStep(fromX, fromY, toX, toY, /* step= */ 0, SWIPE_STEPS);
        mStatsController.onSwipe();
    }

    @Override
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y)
            throws RemoteException {
        if (shouldDisallowInteractions("longPress")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        int longPressStepCount =
                (int) Math.ceil(
                        (double) mViewConfiguration.getLongPressTimeoutMillis() *
                                LONG_PRESS_TIMEOUT_MULTIPLIER / TOUCH_EVENT_DELAY_MS);
        performSwipeStep(x, y, x, y, /* step= */ 0, longPressStepCount);
        mStatsController.onLongPress();
    }

    @Override
    public void performAction(@ComputerControlSession.Action int actionCode)
            throws RemoteException {
        if (shouldDisallowInteractions("performAction")) {
            return;
        }
        cancelOngoingInteractions();
        if (actionCode == ComputerControlSession.ACTION_GO_BACK) {
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN));
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP));
        } else {
            Slog.e(TAG, "Invalid action code for performAction: " + actionCode);
            return;
        }
        mStatsController.onPerformAction(actionCode);
    }

    @Override
    @Nullable
    public IInteractiveMirror createInteractiveMirror(SurfaceControl outMirrorSurface) {
        final var mirror = createInteractiveMirrorImpl();
        if (mirror == null) {
            return null;
        }
        final boolean foregroundMirroringStarted;
        synchronized (mInteractiveMirrors) {
            foregroundMirroringStarted = mInteractiveMirrors.isEmpty();
            if (foregroundMirroringStarted) {
                // Automation is no longer running in the background. Show touches.
                mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                        true /* enabled */);
                // Automation is happening in the foreground, so enable rendering.
                mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                        mVirtualDisplayId, /* enable = */false);
                mWindowManagerInternal.requestHardwareRendererOutputEnabled(mVirtualDisplayId,
                        0 /* timeoutMs */, (success) -> {
                        }, mScheduler);
            }
            mInteractiveMirrors.add(mirror);
        }
        outMirrorSurface.copyFrom(mirror.getMirrorLeash(),
                "ComputerControlSessionImpl#createInteractiveMirrorDisplay");
        if (foregroundMirroringStarted) {
            mStatsController.onMirroringStarted();
        }
        mStatsController.onMirrorViewCreated();
        return mirror;
    }

    @Override
    public void onOpChanged(String op, String packageName) {}

    @Override
    public void onOpChanged(@NonNull String op, @NonNull String packageName, int userId) {
        if (!AppOpsManager.OPSTR_COMPUTER_CONTROL.equals(op)
                || !Objects.equals(packageName, mOwnerPackageName)
                || userId != mOwnerUser.getIdentifier()) {
            return;
        }

        try {
            final int uid = mOwnerPackageManager.getPackageUidAsUser(packageName, userId);
            final int mode = mAppOpsManager.checkOpNoThrow(op, uid, packageName);
            Slog.i(TAG, "onOpChanged: Found new mode " + mode + " for package " + packageName
                    + " for user id " + userId);
            if (mode == AppOpsManager.MODE_IGNORED) {
                close(CLOSE_REASON_USER_INITIATED);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "onOpChanged: Failed to get uid for package " + packageName
                    + " for user id " + userId);
        }
    }

    @Nullable
    private InteractiveMirrorImpl createInteractiveMirrorImpl() {
        final var mirror =
                mWindowManagerInternal.createMirrorForDisplayContent(mVirtualDisplayId);
        if (mirror == null) {
            Slog.w(TAG, "Failed to create DisplayMirror from WM for display: " + mVirtualDisplayId);
            return null;
        }
        return new InteractiveMirrorImpl(mirror, mTransactionSupplier,
                mDisplayManagerGlobal.getDisplayInfo(mVirtualDisplayId), mInputManagerInternal,
                mStatsController::onMirrorViewInteractive, this::removeInteractiveMirror);
    }

    private void removeInteractiveMirror(InteractiveMirrorImpl interactiveMirror) {
        final boolean foregroundMirroringStopped;
        synchronized (mInteractiveMirrors) {
            if (!mInteractiveMirrors.remove(interactiveMirror)) {
                return;
            }
            foregroundMirroringStopped = mInteractiveMirrors.isEmpty();
            if (foregroundMirroringStopped) {
                // Automation is fully running in the background. No need to show touches.
                mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                        false /* enabled */);
                // Disable rendering during background automation, where windows will only draw
                // when the client requests a screenshot.
                mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                        mVirtualDisplayId, /* enable = */true);
                synchronized (mWindowDrawLock) {
                    if (!mIsWaitingForWindowDraw) {
                        mWindowManagerInternal.requestHardwareRendererOutputDisabled(
                                mVirtualDisplayId);
                    }
                }
            }
        }
        try (var transaction = mTransactionSupplier.get()) {
            interactiveMirror.closeWithTransaction(transaction);
            transaction.apply();
        }
        if (foregroundMirroringStopped) {
            mStatsController.onMirroringStopped();
        }
    }

    private void removeAllInteractiveMirrorsOnSessionClose() {
        synchronized (mInteractiveMirrors) {
            if (mInteractiveMirrors.isEmpty()) {
                return;
            }
            try (var transaction = mTransactionSupplier.get()) {
                for (int i = 0; i < mInteractiveMirrors.size(); i++) {
                    mInteractiveMirrors.get(i).closeWithTransaction(transaction);
                }
                transaction.apply();
            }
            mInteractiveMirrors.clear();

            // Automation is fully running in the background. No need to show touches.
            mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                    false /* enabled */);
        }
    }

    @SuppressLint("WrongConstant")
    @Override
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        if (shouldDisallowInteractions("insertText")) {
            return;
        }
        cancelOngoingInteractions();

        InputMethodManagerInternal.ComputerControlInputConnectionData data = getInputConnectionData(
                mVirtualDisplayId);
        if (data == null) {
            Slog.e(TAG, "Unable to insert text: No input connection data found!");
            return;
        }
        final IRemoteComputerControlInputConnection ic = data.inputConnection();
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
            if (commit) {
                // Use the saved editor info of the current client on CC display to perform the
                // default editor action (if any). Otherwise fallback to pressing enter key.
                // Introduced a delay for performing editor action/pressing enter key to let the
                // text be committed to text field first. Some apps might be processing input
                // connection actions differently causing race conditions between "insertion of
                // text" and "committing the text" actions. Introducing a small delay (50 ms),
                // would ensure things happen in order.
                final EditorInfo editorInfo = data.editorInfo();
                mInsertTextFuture = mScheduler.schedule(() -> {
                    try {
                        if (!performDefaultEditorAction(editorInfo, ic)) {
                            Slog.w(TAG,
                                    "Unable to perform editor action to commit text: defaulting "
                                            + "to pressing enter key");
                            ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                            ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to commit text through InputConnection", e);
                    }
                }, KEY_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to insert text through InputConnection", e);
            return;
        }
        mStatsController.onInsertText();
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
    public void setPreviewIntent(@Nullable PendingIntent previewIntent) {
        synchronized (mPreviewIntentLock) {
            mPreviewIntent = previewIntent;
        }
    }

    @Override
    public boolean requestScreenshot() {
        if (mLifecycle.getCurrentState() instanceof LifecycleState.Closed) {
            Slog.e(TAG, "Cannot request screenshot: Session is closed");
            return false;
        }
        synchronized (mWindowDrawLock) {
            if (mIsWaitingForWindowDraw) {
                Slog.w(TAG, "Cannot request screenshot: Window draw is already in progress");
                return false;
            }
            mIsWaitingForWindowDraw = mWindowManagerInternal.requestHardwareRendererOutputEnabled(
                    mVirtualDisplayId, WINDOW_DRAW_TIMEOUT_MS, this::onWindowsDrawnCallback,
                    mScheduler);
            if (mIsWaitingForWindowDraw) {
                Trace.asyncTraceForTrackBegin(mTraceTrack, "isWaitingForWindowDraw",
                        TRACE_COOKIE_WINDOW_DRAW);
            }
            return mIsWaitingForWindowDraw;
        }
    }

    private void onWindowsDrawnCallback(boolean success) {
        if (!success) {
            Slog.w(TAG, "Timed out waiting for windows to be drawn!");
        }
        synchronized (mInteractiveMirrors) {
            if (mInteractiveMirrors.isEmpty()) {
                mWindowManagerInternal.requestHardwareRendererOutputDisabled(
                        mVirtualDisplayId);
            }
            synchronized (mWindowDrawLock) {
                mIsWaitingForWindowDraw = false;
                Trace.asyncTraceForTrackEnd(mTraceTrack, TRACE_COOKIE_WINDOW_DRAW);
            }
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
        cancelOngoingInteractions();
        cancelPendingCloseSession();
        mAudioInjector.stopAudioInjection();
        mAudioCapture.stopAudioCapture();
        mVirtualDevice.close(); // closes also the VirtualAudioDevice
        mAppToken.unlinkToDeath(this, 0);
        removeAllInteractiveMirrorsOnSessionClose();
        mOnClosedListener.accept(this);
        mAppOpsManager.stopWatchingMode(this);
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

    private void startSessionCloseGlobalTimeout() {
        mCloseSessionFuture = mScheduler.schedule(() -> close(CLOSE_REASON_SESSION_TIMED_OUT),
                mGlobalSessionTimeoutDurationMs, TimeUnit.MILLISECONDS);
    }

    private boolean performDefaultEditorAction(@Nullable EditorInfo editorInfo,
            @NonNull IRemoteComputerControlInputConnection ic) throws RemoteException {
        // Check if currently active input connection on CC display has a valid editor action
        // provided by the client view
        if (editorInfo != null && editorInfo.imeOptions != EditorInfo.IME_ACTION_UNSPECIFIED
                && (editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION)
                != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(new InputConnectionCommandHeader(0),
                    editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION);
            return true;
        }
        return false;
    }

    private void cancelOngoingInteractions() {
        if (mInsertTextFuture != null) {
            mInsertTextFuture.cancel(false);
            mInsertTextFuture = null;
        }
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

        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_INPUT_DEVICE_NAME_PREFIX_BYTES) {
            return prefix;
        }

        int startIndex = bytes.length - MAX_INPUT_DEVICE_NAME_PREFIX_BYTES;
        while (startIndex < bytes.length) {
            byte currentByte = bytes[startIndex];
            // Check if the byte is a continuation byte (0x80 <= byte <= 0xBF)
            // In Java, bytes are signed, so the range check is: (currentByte & 0xC0) == 0x80
            if ((currentByte & 0xC0) == 0x80) {
                // This is a continuation byte, so we must advance the start index
                startIndex++;
            } else {
                // This is a start byte (or an ASCII byte), which is a safe cut-off point.
                break;
            }
        }
        byte[] truncatedBytes = Arrays.copyOfRange(bytes, startIndex, bytes.length);
        return new String(truncatedBytes, StandardCharsets.UTF_8);
    }

    private boolean isActivityLaunchAllowed(@NonNull ComponentName componentName,
            @UserIdInt int userId) {
        synchronized (mAllowlistedPackages) {
            if (!mAllowlistedPackages.contains(componentName.getPackageName())) {
                return false;
            }
        }

        // TODO: b/451568055 - Support cross-user sessions.
        return userId == UserHandle.USER_SYSTEM || userId == mOwnerUser.getIdentifier();
    }

    @Nullable
    private Intent getLaunchIntent(@NonNull String packageName, @Nullable String className) {
        if (className == null) {
            return mOwnerPackageManager.getLaunchIntentForPackage(packageName);
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(packageName, className);
        final List<ResolveInfo> resolveInfos = mOwnerPackageManager.queryIntentActivities(
                intent, ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        if (resolveInfos.isEmpty()) {
            return null;
        }
        return intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private InputMethodManagerInternal.ComputerControlInputConnectionData getInputConnectionData(
            int displayId) {
        // getUserAssignedToDisplay returns the main userId, if we want to support cross
        // profile CC interactions and typing on CC display, we need to find the right user
        // profile here for the CC input connection
        return mInputMethodManagerInternal.getComputerControlInputConnectionData(
                mUserManagerInternal.getUserAssignedToDisplay(displayId), displayId);
    }

    private void moveAllTasks(int fromDisplayId, int toDisplayId) {
        mActivityTaskManagerInternal.moveAllTasks(fromDisplayId, toDisplayId);
    }

    private boolean shouldDisallowInteractions(String callSite) {
        // TODO: b/452428736 - Find a long term solution for blocking agent interactions.
        if (!(mLifecycle.getCurrentState() instanceof LifecycleState.Active)) {
            Slog.w(TAG, "Computer control interaction blocked since session is not active: "
                    + callSite);
            return true;
        }
        return false;
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

    private void cancelDisplayEmptyScheduledAction() {
        final var action = mDisplayEmptyScheduledAction;
        if (action != null) {
            action.cancel(false);
        }
    }

    private class ComputerControlActivityListener implements VirtualDeviceManager.ActivityListener {
        @Override
        public void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity) {}

        @Override
        public void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity,
                @UserIdInt int userId) {
            Slog.v(TAG, "Top activity changed to " + topActivity + " for user " + userId);
            cancelDisplayEmptyScheduledAction();

            // If we have a new top activity which is allowed, then attempt a transition to the
            // active state.
            if (isActivityLaunchAllowed(topActivity, userId)) {
                mLifecycle.updateLifecycleState((config) -> {
                    config.mBlockingActivityPackage = null;
                });
            }
        }

        @Override
        public void onDisplayEmpty(int displayId) {
            Slog.v(TAG, "Display empty");
            mLifecycle.updateLifecycleState((config) -> {
                config.mBlockingActivityPackage = null;
                config.mSecureWindowPackage = null;
            });
            cancelDisplayEmptyScheduledAction();
            // Close the session if the display remains empty after the timeout.
            mDisplayEmptyScheduledAction = mScheduler.schedule(
                    () -> close(CLOSE_REASON_SESSION_EMPTY),
                    CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        @SuppressLint("AndroidFrameworkRequiresPermission")
        public void onActivityLaunchBlocked(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user, IntentSender intentSender) {
            Slog.w(TAG, "Unexpectedly blocked activity launch for " + componentName
                    + " on session " + mParams.getName());
        }

        @Override
        public void onSecureWindowShown(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user) {
            Slog.v(TAG, "Secure window shown for " + componentName);
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage =
                    Objects.requireNonNull(componentName.getPackageName()));
        }

        @Override
        public void onSecureWindowHidden(int displayId) {
            Slog.v(TAG, "Secure window hidden");
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage = null);
        }

        @Override
        public void onActivityLaunchRequested(int displayId, @NonNull ComponentName componentName,
                @UserIdInt int userId) {
            Slog.v(TAG, "Activity launch requested for " + componentName + " for user "
                    + userId);
            // If we have an activity launch request which is not allowed, then transition to
            // blocked state.
            if (!isActivityLaunchAllowed(componentName, userId)) {
                mLifecycle.updateLifecycleState(
                        (config) -> config.mBlockingActivityPackage =
                                Objects.requireNonNull(componentName.getPackageName()));
                return;
            }
            if (mAppInteractionService != null) {
                long now = System.currentTimeMillis();
                mFgThreadExecutor.execute(
                        () -> {
                            mAppInteractionService.noteAppInteraction(
                                    mOwnerPackageName,
                                    componentName.getPackageName(),
                                    mParams.getAppInteractionAttribution(),
                                    now,
                                    userId);
                        });
            }
        }
    }

    static final class NotificationInfo {
        private final int mNotificationId;
        @Nullable
        private final String mNotificationTag;

        NotificationInfo(int notificationId, @Nullable String notificationTag) {
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
