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

package com.android.extensions.computercontrol;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityOptions;
import android.companion.virtual.computercontrol.InteractiveMirrorDisplay;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.os.Bundle;
import android.view.Surface;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.extensions.computercontrol.input.KeyEvent;
import com.android.extensions.computercontrol.input.TouchEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Computer control sessions are used to allow a computer of some sort to control applications on
 * the Android device.
 *
 * <p>Applications can be launched in the computer control session via
 * {@link #startActivity(Context, Intent)}, which can then be controlled with input events injected
 * via {@link #sendTouchEvent(TouchEvent)} and {@link #sendKeyEvent(KeyEvent)}.</p>
 *
 * <p>When the session is no longer used it should be closed by calling {@link #close()} to clean up
 * resources and reduce the system-wide impact computer control sessions can have.</p>
 */
public final class ComputerControlSession implements AutoCloseable {
    private final android.companion.virtual.computercontrol.ComputerControlSession mSession;
    private final Params mParams;
    private final int mVirtualDisplayId;
    private final AccessibilityManager mAccessibilityManager;
    private final ComputerControlAccessibilityProxy mAccessibilityProxy;
    private TouchListener mTouchListener = null;
    private final AtomicBoolean mIsValid = new AtomicBoolean(true);

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    ComputerControlSession(
            @NonNull android.companion.virtual.computercontrol.ComputerControlSession session,
            @NonNull Params params, @NonNull AccessibilityManager accessibilityManager) {
        mSession = Objects.requireNonNull(session);
        mParams = Objects.requireNonNull(params);
        mVirtualDisplayId = session.getVirtualDisplayId();
        mAccessibilityManager = Objects.requireNonNull(accessibilityManager);
        mAccessibilityProxy = new ComputerControlAccessibilityProxy(mVirtualDisplayId);
        mAccessibilityManager.registerDisplayProxy(mAccessibilityProxy);
    }

    /**
     * Returns the {@link Params} for this session.
     */
    @NonNull
    public Params getParams() {
        return mParams;
    }

    /**
     * Launches an Activity in the computer control session. This is used to start an application in
     * the {@link VirtualDisplay} of the ComputerControl environment.
     */
    public void startActivity(Context context, Intent intent) {
        Bundle activityOptionsBundle =
                ActivityOptions.makeBasic().setLaunchDisplayId(mVirtualDisplayId).toBundle();
        context.startActivity(intent, activityOptionsBundle);
        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Injects a {@link TouchEvent} into the computer control session.
     */
    public void sendTouchEvent(TouchEvent touchEvent) {
        VirtualTouchEvent virtualTouchEvent =
                new VirtualTouchEvent.Builder()
                        .setX(touchEvent.getX())
                        .setY(touchEvent.getY())
                        .setPressure(touchEvent.getPressure())
                        .setToolType(touchEvent.getToolType())
                        .setAction(touchEvent.getAction())
                        .setPointerId(touchEvent.getPointerId())
                        .setEventTimeNanos(touchEvent.getEventTimeNanos())
                        .setMajorAxisSize(touchEvent.getMajorAxisSize())
                        .build();
        mSession.sendTouchEvent(virtualTouchEvent);

        mAccessibilityProxy.resetStabilityState();

        if (mTouchListener != null) {
            mTouchListener.onTouchEvent(touchEvent);
        }
    }

    /**
     * Injects a {@link KeyEvent} into the computer control session.
     */
    public void sendKeyEvent(KeyEvent keyEvent) {
        VirtualKeyEvent virtualKeyEvent = new VirtualKeyEvent.Builder()
                                                  .setKeyCode(keyEvent.getKeyCode())
                                                  .setAction(keyEvent.getAction())
                                                  .setEventTimeNanos(keyEvent.getEventTimeNanos())
                                                  .build();
        mSession.sendKeyEvent(virtualKeyEvent);

        mAccessibilityProxy.resetStabilityState();
    }

    /**
     * Returns all windows on the display associated with the {@link ComputerControlSession}.
     */
    @NonNull
    public List<AccessibilityWindowInfo> getAccessibilityWindows() {
        return mAccessibilityProxy.getWindows();
    }

    /**
     * Returns an {@link InteractiveMirror} which mirrors this {@link ComputerControlSession}.
     */
    public InteractiveMirror createInteractiveMirror(
            int width, int height, @NonNull Surface surface) {
        Objects.requireNonNull(surface);

        InteractiveMirrorDisplay interactiveMirrorDisplay =
                mSession.createInteractiveMirrorDisplay(width, height, surface);
        if (interactiveMirrorDisplay == null) {
            return null;
        }
        return new InteractiveMirror(interactiveMirrorDisplay);
    }

    /** Add a callback to be notified when the computer control session is potentially stable. */
    public void addStabilityHintCallback(long timeoutMs, @NonNull StabilityHintCallback callback) {
        mAccessibilityProxy.registerStabilityHintCallback(timeoutMs, callback);
    }

    /** Remove a stability hint callback that was previously added. */
    public void removeStabilityHintCallback(@NonNull StabilityHintCallback callback) {
        mAccessibilityProxy.removeStabilityHintCallback(callback);
    }

    /**
     * Callback used to inform the session owner that the computer control session has potentially
     * reached a stable state. This can be used as a hint to determine when an action that was
     * performed, such as app launch, has completed or finished animating.
     */
    public interface StabilityHintCallback {
        /**
         * Called when the computer control session has potentially reached a stable state.
         *
         * @param timedOut True if the system was unable to determine the stability of the session
         *                 within the timeout.
         */
        void onStabilityHint(boolean timedOut);
    }

    /**
     * Closes the ComputerControl session and release resources. The session can no longer be used
     * after calling this method.
     */
    @Override
    public void close() {
        synchronized (mIsValid) {
            if (!mIsValid.get()) {
                return;
            }
            mAccessibilityManager.unregisterDisplayProxy(mAccessibilityProxy);
            mSession.close();
            mIsValid.set(false);
        }
    }

    /**
     * Sets a {@link TouchListener} for this session.
     */
    public void setTouchListener(@Nullable TouchListener listener) {
        mTouchListener = listener;
    }

    /**
     * Used for listening to any {@link TouchEvent} injected via this session.
     */
    public interface TouchListener {
        /**
         * Indicates that the given {@link TouchEvent} has been injected.
         */
        void onTouchEvent(@NonNull TouchEvent event);
    }

    public static class Params {
        @NonNull private final Context mContext;
        @NonNull private final String mName;
        private final int mDisplayWidthPx;
        private final int mDisplayHeightPx;
        private final int mDisplayDpi;
        @NonNull private final Surface mDisplaySurface;
        private final boolean mIsDisplayAlwaysUnlocked;

        private Params(@NonNull Context context, @NonNull String name, int displayWidthPx,
                int displayHeightPx, int displayDpi, @NonNull Surface displaySurface,
                boolean isDisplayAlwaysUnlocked) {
            mContext = context;
            mName = name;
            mDisplayWidthPx = displayWidthPx;
            mDisplayHeightPx = displayHeightPx;
            mDisplayDpi = displayDpi;
            mDisplaySurface = displaySurface;
            mIsDisplayAlwaysUnlocked = isDisplayAlwaysUnlocked;
        }

        /**
         * Returns the context used to construct the ComputerControlSession.
         */
        @NonNull
        public Context getContext() {
            return mContext;
        }

        /**
         * Returns the name of the computer control session
         *
         * @see Builder#setName(String)
         */
        @NonNull
        public String getName() {
            return mName;
        }

        /**
         * Returns the display width to use for the computer control session.
         *
         * @see Builder#setDisplayWidthPx(int)
         */
        public int getDisplayWidthPx() {
            return mDisplayWidthPx;
        }

        /**
         * Returns the display height to use for the computer control session.
         *
         * @see Builder#setDisplayHeightPx(int)
         */
        public int getDisplayHeightPx() {
            return mDisplayHeightPx;
        }

        /**
         * Returns the display DPI to use for the computer control session.
         *
         * @see Builder#setDisplayDpi(int) (int)
         */
        public int getDisplayDpi() {
            return mDisplayDpi;
        }

        /**
         * Returns the {@link Surface} to attach to the computer control session.
         *
         * @see Builder#setDisplaySurface(Surface)
         */
        @NonNull
        public Surface getDisplaySurface() {
            return mDisplaySurface;
        }

        /**
         * Returns if the Virtual Display created should always remain unlocked
         *
         * @see Builder#setDisplayAlwaysUnlocked(boolean)
         */
        public boolean isDisplayAlwaysUnlocked() {
            return mIsDisplayAlwaysUnlocked;
        }

        /**
         * Builder for {@link ComputerControlSession}.
         */
        public static class Builder {
            @NonNull private final Context mContext;
            @Nullable private String mName;
            private int mDisplayWidthPx;
            private int mDisplayHeightPx;
            private int mDisplayDpi;
            @Nullable private Surface mDisplaySurface;

            // By default, the displays created are always unlocked
            private boolean mIsDisplayAlwaysUnlocked = true;

            /**
             * Create a new Builder.
             */
            public Builder(@NonNull Context context) {
                mContext = context;
            }

            /**
             * Set the name of the session. Only used internally and not shown to users.
             */
            @NonNull
            public Builder setName(@Nullable String name) {
                mName = name;
                return this;
            }

            /**
             * Set the width of the display used for the session.
             */
            @NonNull
            public Builder setDisplayWidthPx(int displayWidthPx) {
                if (displayWidthPx <= 0) {
                    throw new IllegalArgumentException("Invalid display width");
                }
                mDisplayWidthPx = displayWidthPx;
                return this;
            }

            /**
             * Set the height of the display used for the session.
             */
            @NonNull
            public Builder setDisplayHeightPx(int displayHeightPx) {
                if (displayHeightPx <= 0) {
                    throw new IllegalArgumentException("Invalid display height");
                }
                mDisplayHeightPx = displayHeightPx;
                return this;
            }

            /**
             * Set the DPI of the display used for the session.
             */
            @NonNull
            public Builder setDisplayDpi(int dpi) {
                if (dpi <= 0) {
                    throw new IllegalArgumentException("Invalid display DPI");
                }
                mDisplayDpi = dpi;
                return this;
            }

            /**
             * Set the {@link Surface} of the display used for the session.
             */
            @NonNull
            public Builder setDisplaySurface(@NonNull Surface displaySurface) {
                if (displaySurface == null) {
                    throw new NullPointerException("Missing display surface");
                }
                mDisplaySurface = displaySurface;
                return this;
            }

            /**
             * Set the created display to always remain unlocked
             */
            @NonNull
            public Builder setDisplayAlwaysUnlocked(boolean isDisplayAlwaysUnlocked) {
                mIsDisplayAlwaysUnlocked = isDisplayAlwaysUnlocked;
                return this;
            }

            /**
             * Build a computer control session. Will throw when required values are not set.
             */
            @NonNull
            public Params build() {
                if (mContext == null) {
                    throw new NullPointerException("Missing Context");
                }
                if (mName == null) {
                    mName = "ComputerControl-" + mContext.getPackageName();
                }
                if (mDisplayWidthPx <= 0) {
                    throw new IllegalArgumentException("Invalid display width");
                }
                if (mDisplayHeightPx <= 0) {
                    throw new IllegalArgumentException("Invalid display height");
                }
                if (mDisplayDpi <= 0) {
                    throw new IllegalArgumentException("Invalid display DPI");
                }
                if (mDisplaySurface == null) {
                    throw new NullPointerException("Missing display surface");
                }

                return new Params(mContext, mName, mDisplayWidthPx, mDisplayHeightPx, mDisplayDpi,
                        mDisplaySurface, mIsDisplayAlwaysUnlocked);
            }
        }
    }

    /** Callback for computer control session events. */
    public interface Callback {
        /** Called when the session has been successfully created. */
        void onSessionCreated(@NonNull ComputerControlSession session);

        /** Called when the session failed to be created. */
        void onSessionCreationFailed(int errorCode);

        /** Called when the session has been closed. */
        void onSessionClosed();
    }

    @VisibleForTesting
    static class ComputerControlAccessibilityProxy extends AccessibilityDisplayProxy {
        @Nullable
        @GuardedBy("this")
        private StabilityHintCallbackTracker mStabilityHintCallbackTracker;

        ComputerControlAccessibilityProxy(int displayId) {
            super(displayId, Executors.newSingleThreadExecutor(), getAccessibilityServiceInfos());
        }

        @Override
        public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
            synchronized (this) {
                if (mStabilityHintCallbackTracker == null) {
                    return;
                }
                mStabilityHintCallbackTracker.onAccessibilityEvent();
            }
        }

        @VisibleForTesting
        static List<AccessibilityServiceInfo> getAccessibilityServiceInfos() {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            return List.of(info);
        }

        /**
         * Called whenever something significant happens in the ComputerControl session (new input
         * events, new apps launched, etc.).
         */
        public void resetStabilityState() {
            synchronized (this) {
                if (mStabilityHintCallbackTracker != null) {
                    mStabilityHintCallbackTracker.resetStabilityState();
                }
            }
        }

        /**
         * Register a new {@link StabilityHintCallback}
         */
        public void registerStabilityHintCallback(
                long timeoutMs, @NonNull StabilityHintCallback callback) {
            synchronized (this) {
                if (mStabilityHintCallbackTracker != null) {
                    throw new IllegalStateException(
                            "A stability hint callback is already added; Only a single callback is "
                            + "supported.");
                }
                mStabilityHintCallbackTracker =
                        new StabilityHintCallbackTracker(callback, timeoutMs);
            }
        }

        /**
         * Unregister a {@link StabilityHintCallback}
         */
        public void removeStabilityHintCallback(@NonNull StabilityHintCallback callback) {
            synchronized (this) {
                if (mStabilityHintCallbackTracker == null
                        || mStabilityHintCallbackTracker.getCallback() != callback) {
                    throw new IllegalArgumentException("The callback was not previously added.");
                }
                mStabilityHintCallbackTracker.close();
                mStabilityHintCallbackTracker = null;
            }
        }
    }
}
