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

import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.RemoteException;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
public final class ComputerControlSession implements AutoCloseable {

    /** @hide */
    public static final String ACTION_REQUEST_ACCESS =
            "android.companion.virtual.computercontrol.action.REQUEST_ACCESS";

    /**
     * Error code indicating that a new session cannot be created because the maximum number of
     * allowed concurrent sessions has been reached.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     */
    public static final int ERROR_SESSION_LIMIT_REACHED = 1;

    /**
     * Error code indicating that a new session cannot be created because the lock screen (also
     * known as Keyguard) is showing.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     *
     * @see android.app.KeyguardManager#isKeyguardLocked()
     */
    public static final int ERROR_KEYGUARD_LOCKED = 2;

    /**
     * Error code indicating that the user did not approve the creation of a new session.
     */
    public static final int ERROR_PERMISSION_DENIED = 3;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ERROR_", value = {
            ERROR_SESSION_LIMIT_REACHED,
            ERROR_KEYGUARD_LOCKED,
            ERROR_PERMISSION_DENIED})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionCreationError {
    }

    @NonNull
    private final IComputerControlSession mSession;
    // TODO(b/439774796): Make this non-nullable.
    @Nullable
    private final ImageReader mImageReader;

    /** @hide */
    public ComputerControlSession(int displayId, @NonNull IVirtualDisplayCallback displayToken,
            @NonNull IComputerControlSession session) {
        this(displayId, displayToken, session, DisplayManagerGlobal.getInstance());
    }

    /** @hide */
    @VisibleForTesting
    public ComputerControlSession(int displayId, @NonNull IVirtualDisplayCallback displayToken,
            @NonNull IComputerControlSession session,
            @NonNull DisplayManagerGlobal displayManagerGlobal) {
        mSession = Objects.requireNonNull(session);

        // TODO(b/439774796): Require a valid display id.
        if (displayId != Display.INVALID_DISPLAY) {
            final Display display = displayManagerGlobal.getRealDisplay(displayId);
            Objects.requireNonNull(display);
            final DisplayInfo displayInfo = new DisplayInfo();
            display.getDisplayInfo(displayInfo);

            mImageReader = ImageReader.newInstance(displayInfo.logicalWidth,
                    displayInfo.logicalHeight,
                    PixelFormat.RGBA_8888, /* maxImages= */ 2);
            displayManagerGlobal.setVirtualDisplaySurface(displayToken, mImageReader.getSurface());
        } else {
            mImageReader = null;
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
        return mImageReader == null ? null : mImageReader.acquireLatestImage();
    }

    /**
     * Sends a tap event to the computer control session at the given location.
     *
     * <p>The coordinates are in relative display space, e.g. (0.5, 0.5) is the center of the
     * display.</p>
     */
    public void tap(@FloatRange(from = 0.0, to = 1.0) float x,
            @FloatRange(from = 0.0, to = 1.0) float y) {
        if (x < 0 || x > 1 || y < 0 || y > 1) {
            throw new IllegalArgumentException("Tap coordinates must be in range [0, 1]");
        }
        try {
            mSession.tap(x, y);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
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
            @FloatRange(from = 0.0, to = 1.0) float fromX,
            @FloatRange(from = 0.0, to = 1.0) float fromY,
            @FloatRange(from = 0.0, to = 1.0) float toX,
            @FloatRange(from = 0.0, to = 1.0) float toY) {
        if (fromX < 0 || fromX > 1 || fromY < 0 || fromY > 1
                || toX < 0 || toX > 1 || toY < 0 || toY > 1) {
            throw new IllegalArgumentException("Swipe coordinates must be in range [0, 1]");
        }
        try {
            mSession.swipe(fromX, fromY, toX, toY);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Returns the ID of the single trusted virtual display for this session. */
    public int getVirtualDisplayId() {
        try {
            return mSession.getVirtualDisplayId();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Injects a key event into the trusted virtual display. */
    public void sendKeyEvent(@NonNull VirtualKeyEvent event) {
        try {
            mSession.sendKeyEvent(Objects.requireNonNull(event));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Injects a touch event into the trusted virtual display. */
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) {
        try {
            mSession.sendTouchEvent(Objects.requireNonNull(event));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Creates an interactive virtual display, mirroring the trusted one. */
    @Nullable
    public InteractiveMirrorDisplay createInteractiveMirrorDisplay(
            @IntRange(from = 1) int width, @IntRange(from = 1) int height,
            @NonNull Surface surface) {
        Objects.requireNonNull(surface);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        try {
            IInteractiveMirrorDisplay display =
                    mSession.createInteractiveMirrorDisplay(width, height, surface);
            if (display == null) {
                return null;
            }
            return new InteractiveMirrorDisplay(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void close() {
        try {
            mSession.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
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
         */
        void onSessionClosed();
    }

    /** @hide */
    public static class CallbackProxy extends IComputerControlSessionCallback.Stub {

        private final Callback mCallback;
        private final Executor mExecutor;

        public CallbackProxy(@NonNull Executor executor, @NonNull Callback callback) {
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
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() -> mCallback.onSessionCreated(
                            new ComputerControlSession(displayId, displayToken, session))));
        }

        @Override
        public void onSessionCreationFailed(@SessionCreationError int errorCode) {
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() -> mCallback.onSessionCreationFailed(errorCode)));
        }

        @Override
        public void onSessionClosed() {
            Binder.withCleanCallingIdentity(() ->
                    mExecutor.execute(() -> mCallback.onSessionClosed()));
        }
    }
}
