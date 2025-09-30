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

package android.companion.virtual.computercontrol;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Size;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputConnection;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A session for automated control of applications.
 *
 * <p>A session is associated with a single trusted virtual display, capable of hosting activities,
 * along with the input devices that allow input injection.</p>
 *
 * @hide
 */
public final class ComputerControlSession extends IComputerControlLifecycleCallback.Stub
        implements AutoCloseable {

    /** @hide */
    public static final String ACTION_REQUEST_ACCESS =
            "android.companion.virtual.computercontrol.action.REQUEST_ACCESS";

    /** @hide */
    public static final String EXTRA_AUTOMATING_PACKAGE_NAME =
            "android.companion.virtual.computercontrol.extra.AUTOMATING_PACKAGE_NAME";

    /**
     * Error code indicating that a new session cannot be created because the maximum number of
     * allowed concurrent sessions has been reached.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     */
    public static final int ERROR_SESSION_LIMIT_REACHED = 1;

    /**
     * Error code indicating that a new session cannot be created because the device is currently
     * locked.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     *
     * @see android.app.KeyguardManager#isDeviceLocked()
     */
    public static final int ERROR_DEVICE_LOCKED = 2;

    /**
     * Error code indicating that the user did not approve the creation of a new session.
     */
    public static final int ERROR_PERMISSION_DENIED = 3;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ERROR_", value = {
            ERROR_SESSION_LIMIT_REACHED,
            ERROR_DEVICE_LOCKED,
            ERROR_PERMISSION_DENIED})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionCreationError {
    }

    /**
     * Close reason indicating the session was closed by the caller.
     *
     * @see ComputerControlSession#close()
     */
    public static final int CLOSE_REASON_CALLER_INITIATED = 1;

    /**
     * Close reason indicating the session was closed by the user during an auth flow or to take
     * control of the app under automation, etc.
     */
    public static final int CLOSE_REASON_USER_INITIATED = 2;

    /**
     * Close reason indicating the session timed out.
     */
    public static final int CLOSE_REASON_SESSION_TIMED_OUT = 3;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "CLOSE_REASON_", value = {
            CLOSE_REASON_CALLER_INITIATED,
            CLOSE_REASON_USER_INITIATED,
            CLOSE_REASON_SESSION_TIMED_OUT})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionCloseReason {
    }

    /**
     * Reason indicating that the session was blocked due to secure content being present.
     */
    public static final int BLOCK_REASON_SECURE_CONTENT = 1;

    /**
     * Reason indicating that the session was blocked due to a disallowed activity being launched
     * in the session.
     */
    public static final int BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "BLOCK_REASON_", value = {
            BLOCK_REASON_SECURE_CONTENT,
            BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionBlockReason {
    }

    /**
     * Computer control action that performs back navigation.
     */
    public static final int ACTION_GO_BACK = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ACTION_", value = {
            ACTION_GO_BACK,
    })
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface Action {
    }

    @NonNull
    private final IComputerControlSession mSession;
    @NonNull
    private final Size mDisplaySize;

    private final Object mImageReaderLock = new Object();
    @GuardedBy("mImageReaderLock")
    @Nullable
    private ImageReader mImageReader;

    @GuardedBy("mLifecycle")
    private final SessionLifecycleTracker mLifecycle = new SessionLifecycleTracker();
    @GuardedBy("mLifecycle")
    private LifecycleCallback mRegisteredLifecycleCallback = null;

    // TODO(b/419460558): Added to temporarily link {@link LifecycleCallback#onClosed(int)} with the
    //  existing {@link Callback#onSessionClosed()}. Remove once its migrated.
    @NonNull
    private final Runnable mOnClosedRunnable;

    private final ComputerControlAccessibilityProxy mAccessibilityProxy;

    /** @hide */
    public ComputerControlSession(int displayId, @NonNull IVirtualDisplayCallback displayToken,
            @NonNull IComputerControlSession session,
            @NonNull AccessibilityManager accessibilityManager,
            @NonNull Runnable onClosedRunnable) {
        this(displayId, displayToken, session, accessibilityManager, onClosedRunnable,
                DisplayManagerGlobal.getInstance());
    }

    /** @hide */
    @VisibleForTesting
    public ComputerControlSession(int displayId, @NonNull IVirtualDisplayCallback displayToken,
            @NonNull IComputerControlSession session,
            @NonNull AccessibilityManager accessibilityManager,
            @NonNull Runnable onClosedRunnable,
            @NonNull DisplayManagerGlobal displayManagerGlobal) {
        mSession = Objects.requireNonNull(session);
        mOnClosedRunnable = onClosedRunnable;
        try {
            mSession.setLifecycleCallback(this);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        final Display display = displayManagerGlobal.getRealDisplay(displayId);
        Objects.requireNonNull(display);
        final DisplayInfo displayInfo = new DisplayInfo();
        display.getDisplayInfo(displayInfo);
        mDisplaySize = new Size(displayInfo.logicalWidth, displayInfo.logicalHeight);

        mImageReader = ImageReader.newInstance(displayInfo.logicalWidth,
                displayInfo.logicalHeight,
                PixelFormat.RGBA_8888, /* maxImages= */ 2);
        displayManagerGlobal.setVirtualDisplaySurface(displayToken, mImageReader.getSurface());

        mAccessibilityProxy = new ComputerControlAccessibilityProxy(displayId);
        accessibilityManager.registerDisplayProxy(mAccessibilityProxy);
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the package does not have a launcher activity.
     * @see ComputerControlSessionParams#getTargetPackageNames()
     */
    public void launchApplication(@NonNull String packageName) {
        try {
            mSession.launchApplication(packageName, /* className= */ null);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the component is not a launcher activity.
     * @see ComputerControlSessionParams#getTargetPackageNames()
     */
    public void launchApplication(@NonNull ComponentName component) {
        try {
            mSession.launchApplication(component.getPackageName(), component.getClassName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Hand over full control of the automation session to the user.
     *
     * <p>All of the applications currently automated in the session are moved from the session's
     * display to the user's default display. No further automation is possible on these tasks,
     * although the session remains active and new applications may be launched via
     * {@link #launchApplication(String)}</p>
     */
    public void handOverApplications() {
        try {
            mSession.handOverApplications();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Screenshot the current display content.
     *
     * <p>The behavior is similar to {@link ImageReader#acquireLatestImage}, meaning that any
     * previously acquired images should be released before attempting to acquire new ones.</p>
     *
     * @return A screenshot of the current display content, or {@code null} if no screenshot is
     *   currently available.
     */
    @Nullable
    public Image getScreenshot() {
        synchronized (mImageReaderLock) {
            return mImageReader == null ? null : mImageReader.acquireLatestImage();
        }
    }

    /**
     * Sends a tap event to the computer control session at the given location.
     *
     * <p>The coordinates are in relative display space, e.g. (0.5, 0.5) is the center of the
     * display.</p>
     */
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Tap coordinates must be non-negative");
        }
        try {
            mSession.tap(x, y);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Sends a swipe event to the computer control session for the given coordinates.
     *
     * <p>To avoid misinterpreting the swipe as a fling, the individual touches are throttled, so
     * the entire action will take ~500ms. However, this is done in the background and this method
     * returns immediately. Any ongoing swipe will be canceled if a new swipe is requested.</p>
     *
     * <p>The coordinates are in relative display space, e.g. (0.5, 0.5) is the center of the
     * display.</p>
     */
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int toY) {
        if (fromX < 0 || fromY < 0 || toX < 0 || toY < 0) {
            throw new IllegalArgumentException("Swipe coordinates must be non-negative");
        }
        try {
            mSession.swipe(fromX, fromY, toX, toY);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Sends a long press event to the computer control session for the given coordinates.
     *
     * <p>The coordinates are in relative display space, e.g. (0.5, 0.5) is the center of the
     * display.</p>
     */
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Long press coordinates must be non-negative");
        }
        try {
            mSession.longPress(x, y);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Inserts provided text into the currently active text field on the display associated with
     * the {@link ComputerControlSession}.
     *
     * <p> This method expects a text field to be in focus with an active {@link InputConnection}.
     * It inserts text at the current cursor position in the text field and moves the cursor to
     * the end of inserted text. </p>
     *
     * @param text to be inserted
     * @param replaceExisting whether the current text in the text field needs to be overwritten
     * @param commit whether the text should be submitted after insertion
     */
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        try {
            mSession.insertText(text, replaceExisting, commit);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /** Perform provided action on the trusted virtual display. */
    public void performAction(@Action int actionCode) {
        try {
            mSession.performAction(actionCode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        mAccessibilityProxy.resetStabilityState();
    }

    /** Creates an interactive virtual display, mirroring the trusted one. */
    @Nullable
    public InteractiveMirror createInteractiveMirror() {
        try {
            SurfaceControl mirrorSurface = new SurfaceControl();
            IInteractiveMirror mirror = mSession.createInteractiveMirror(mirrorSurface);
            if (mirror == null) {
                return null;
            }
            return new InteractiveMirror(mirror, mirrorSurface);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Get the size of the session's display. */
    @NonNull
    public Size getDisplaySize() {
        return mDisplaySize;
    }

    /**
     * Sets a {@link StabilityListener} to be notified when the computer control session is
     * potentially stable.
     *
     * @throws IllegalStateException if a listener was previously set.
     */
    public void setStabilityListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull StabilityListener listener) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);
        mAccessibilityProxy.setStabilityListener(executor, listener);
    }

    /**
     * Clears any {@link StabilityListener} that was previously set using
     * {@link #setStabilityListener(Executor, StabilityListener)}.
     *
     * @throws IllegalStateException if a listener was not previously set.
     */
    public void clearStabilityListener() {
        mAccessibilityProxy.clearStabilityListener();
    }

    /**
     * Sets a {@link LifecycleCallback} to be notified about the computer control session lifecycle
     * changes.
     *
     * @throws IllegalStateException if a callback was previously set.
     */
    public void setLifecycleCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull LifecycleCallback callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        synchronized (mLifecycle) {
            if (mRegisteredLifecycleCallback != null) {
                throw new IllegalStateException("Lifecycle callback was already registered!");
            }
            mRegisteredLifecycleCallback = new LifecycleCallback() {
                @Override
                public void onActive() {
                    Binder.withCleanCallingIdentity(() -> executor.execute(callback::onActive));
                }

                @Override
                public void onBlocked(@SessionBlockReason int reason) {
                    Binder.withCleanCallingIdentity(
                            () -> executor.execute(() -> callback.onBlocked(reason)));
                }

                @Override
                public void onClosed(@SessionCloseReason int reason) {
                    Binder.withCleanCallingIdentity(
                            () -> executor.execute(() -> callback.onClosed(reason)));
                }
            };
            mLifecycle.addCallback(mRegisteredLifecycleCallback);
        }
    }

    /**
     * Clears any {@link LifecycleCallback} that was previously set using
     * {@link #setLifecycleCallback(Executor, LifecycleCallback)}.
     *
     * @throws IllegalStateException if a callback was not previously set.
     */
    public void clearLifecycleCallback() {
        synchronized (mLifecycle) {
            if (mRegisteredLifecycleCallback == null) {
                throw new IllegalStateException("Lifecycle callback was never registered!");
            }
            mLifecycle.removeCallback(mRegisteredLifecycleCallback);
            mRegisteredLifecycleCallback = null;
        }
    }

    @Override
    public void onActive() {
        synchronized (mLifecycle) {
            mLifecycle.onActive();
        }
    }

    @Override
    public void onBlocked(@SessionBlockReason int reason) {
        synchronized (mLifecycle) {
            mLifecycle.onBlocked(reason);
        }
    }

    @Override
    public void onClosed(@SessionCloseReason int closeReason) {
        releaseResources();
        synchronized (mLifecycle) {
            mLifecycle.onClosed(closeReason);
        }
        mOnClosedRunnable.run();
    }

    /**
     * Returns all windows on the display associated with the {@link ComputerControlSession}.
     */
    @NonNull
    public List<AccessibilityWindowInfo> getAccessibilityWindows() {
        return mAccessibilityProxy.getWindows();
    }

    @Override
    public void close() {
        try {
            mSession.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void releaseResources() {
        synchronized (mImageReaderLock) {
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        }
    }

    /** Callback for computer control session events. */
    public interface Callback {

        /**
         * Called when the session request needs to approved by the user.
         *
         * <p>Applications should launch the {@link Activity} "encapsulated" in {@code intentSender}
         * {@link IntentSender} object by calling
         * {@link Activity#startIntentSenderForResult(IntentSender, int, Intent, int, int, int)} or
         * {@link Context#startIntentSender(IntentSender, Intent, int, int, int)}
         *
         * @param intentSender an {@link IntentSender} which applications should use to launch
         *   the UI for the user to allow the creation of the session.
         */
        void onSessionPending(@NonNull IntentSender intentSender);

        /** Called when the session has been successfully created. */
        void onSessionCreated(@NonNull ComputerControlSession session);

        /**
         * Called when the session failed to be created.
         *
         * @param errorCode The reason for failure.
         */
        void onSessionCreationFailed(@SessionCreationError int errorCode);

        /**
         * Called when the session has been closed, either via an explicit call to {@link #close()},
         * or due to an automatic closure event, triggered by the framework.
         *
         * @deprecated use {@link LifecycleCallback}
         * @see LifecycleCallback#setLifecycleCallback(Executor, LifecycleCallback)
         */
        @Deprecated
        void onSessionClosed();
    }

    /**
     * Listener to be notified of signals indicating that the computer control session is
     * potentially stable.
     *
     * <p>These signals indicate that the session's display content is currently stable, such as the
     * app under automation being idle and no UI animations being under progress. These are useful
     * for tasks that should only run on a static UI, such as taking screenshots to determine the
     * next step.
     */
    public interface StabilityListener {
        /** Called when the computer control session is considered stable. */
        void onSessionStable();
    }

    /**
     * Callback to be notified about the computer control session lifecycle changes.
     *
     * <p>When the callback is first added, implementers of the callback should not make any
     * assumptions about the starting state of the session. The callback will be notified of the
     * starting state after being added.
     *
     * @see #setLifecycleCallback(Executor, LifecycleCallback)
     */
    public interface LifecycleCallback {

        /**
         * Called when the computer control session enters the active state.
         *
         * <p>When the session is active, the following will apply:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is allowed.
         * </ul>
         */
        void onActive();

        /**
         * Called when the computer control session enters the blocked state.
         *
         * <p>When the session is blocked, the application will generally not be able to interact
         * with or access the content of the session:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are NOT allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is NOT allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is NOT allowed.
         * </ul>
         *
         * <p>However, users can still interact with the contents of the session using the
         * interactive mirror. The application may choose to guide users to take over the session
         * using either the {@link #handOverApplications()} API or the interactive mirror.
         *
         * @param reason the reason that the session initially entered the blocked
         *               state.
         */
        // TODO: b/441475896: Block interactions and screenshots for
        //  BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH. Until then, a dialog indicating the blockage
        //  will show up in the session, and the agent must dismiss it by interacting with the
        //  session to exit the blocked state.
        void onBlocked(@SessionBlockReason int reason);

        /**
         * Called when the computer control session is closed. This marks the end of the session's
         * lifecycle, and no further lifecycle updates will take place.
         */
        void onClosed(@SessionCloseReason int reason);
    }

    /** @hide */
    public static class CallbackProxy extends IComputerControlSessionCallback.Stub {

        private final Context mContext;
        private final Callback mCallback;
        private final Executor mExecutor;
        private ComputerControlSession mSession;

        public CallbackProxy(
                @NonNull Context context, @NonNull Executor executor, @NonNull Callback callback) {
            mContext = context;
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onSessionPending(@NonNull PendingIntent pendingIntent) {
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() ->
                            mCallback.onSessionPending(pendingIntent.getIntentSender())));
        }

        @Override
        public void onSessionCreated(int displayId, IVirtualDisplayCallback displayToken,
                IComputerControlSession session) {
            mSession = new ComputerControlSession(displayId, displayToken, session,
                    mContext.getSystemService(AccessibilityManager.class), this::onSessionClosed);
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() -> mCallback.onSessionCreated(mSession)));
        }

        @Override
        public void onSessionCreationFailed(@SessionCreationError int errorCode) {
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() -> mCallback.onSessionCreationFailed(errorCode)));
        }

        private void onSessionClosed() {
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(mCallback::onSessionClosed));
        }
    }
}