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

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.annotations.GuardedBy;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * {@link AccessibilityDisplayProxy} for gathering A11y signals for apps running on a
 * {@link ComputerControlSession}, and inferring when the session can be considered stable.
 */
final class ComputerControlAccessibilityProxy extends AccessibilityDisplayProxy {
    @Nullable
    @GuardedBy("this")
    private StabilitySignalTracker mStabilitySignalTracker;

    ComputerControlAccessibilityProxy(int displayId) {
        super(displayId, Executors.newSingleThreadExecutor(), getAccessibilityServiceInfos());
    }

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        synchronized (this) {
            if (mStabilitySignalTracker == null) {
                return;
            }
            mStabilitySignalTracker.onAccessibilityEvent();
        }
    }

    /**
     * Called whenever something significant happens in the ComputerControl session (new input
     * events, new apps launched, etc.).
     */
    void resetStabilityState() {
        synchronized (this) {
            if (mStabilitySignalTracker != null) {
                mStabilitySignalTracker.resetStabilityState();
            }
        }
    }

    /**
     * Sets a {@link ComputerControlSession.StabilityListener} to be invoked on a given
     * {@link Executor} whenever a session is considered stable.
     */
    void setStabilityListener(@CallbackExecutor @NonNull Executor executor,
            @NonNull ComputerControlSession.StabilityListener listener) {
        synchronized (this) {
            if (mStabilitySignalTracker != null) {
                throw new IllegalStateException("A stability listener is already set.");
            }
            mStabilitySignalTracker =
                    new StabilitySignalTracker(
                            () -> executor.execute(listener::onSessionStable));
        }
    }

    /**
     * Clears any {@link ComputerControlSession.StabilityListener} that was set previously.
     */
    void clearStabilityListener() {
        synchronized (this) {
            if (mStabilitySignalTracker == null) {
                throw new IllegalStateException("A stability listener is not set.");
            }
            mStabilitySignalTracker.close();
            mStabilitySignalTracker = null;
        }
    }

    private static List<AccessibilityServiceInfo> getAccessibilityServiceInfos() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        return List.of(info);
    }

    private static final class StabilitySignalTracker implements AutoCloseable,
            EventIdleTracker.Callback {
        private static final long STABILITY_TIMER_MS = 500L;

        private final HandlerThread mHandlerThread = new HandlerThread("StabilityTracker");

        private final ComputerControlSession.StabilityListener mStabilityListener;
        private final EventIdleTracker mEventIdleTracker;

        StabilitySignalTracker(ComputerControlSession.StabilityListener listener) {
            mStabilityListener = listener;

            mHandlerThread.start();
            mEventIdleTracker = new EventIdleTracker(
                    mHandlerThread.getThreadHandler(), STABILITY_TIMER_MS);
        }

        void onAccessibilityEvent() {
            mEventIdleTracker.onEvent();
        }

        void resetStabilityState() {
            mEventIdleTracker.reset();
            mEventIdleTracker.registerOneShotIdleCallback(this);
        }

        @Override
        public void close() {
            mEventIdleTracker.reset();
            mHandlerThread.quitSafely();
        }

        @Override
        public void onEventIdle() {
            if (mStabilityListener != null) {
                mStabilityListener.onSessionStable();
            }
        }
    }

    private static final class EventIdleTracker {
        private final Handler mHandler;
        private final long mEventIdleTimeoutMs;

        private Callback mPendingCallback;

        private final Runnable mCallbackExecutor = new Runnable() {
            @Override
            public void run() {
                if (mPendingCallback == null) {
                    return;
                }
                Callback pendingCallback = mPendingCallback;
                reset();
                pendingCallback.onEventIdle();
            }
        };

        EventIdleTracker(@NonNull Handler handler, long eventIdleTimeoutMs) {
            mHandler = handler;
            mEventIdleTimeoutMs = eventIdleTimeoutMs;
        }

        /** Called when an event is received, which resets the idle timer. */
        void onEvent() {
            if (mPendingCallback == null) {
                return;
            }

            mHandler.removeCallbacks(mCallbackExecutor);
            long idleTime = SystemClock.uptimeMillis() + mEventIdleTimeoutMs;
            mHandler.postAtTime(mCallbackExecutor, idleTime);
        }

        void registerOneShotIdleCallback(@NonNull Callback callback) {
            if (mPendingCallback != null) {
                throw new IllegalStateException("There is already a pending callback!");
            }

            mPendingCallback = callback;
            long now = SystemClock.uptimeMillis();
            mHandler.postAtTime(mCallbackExecutor, now + mEventIdleTimeoutMs);
        }

        void reset() {
            mPendingCallback = null;
            mHandler.removeCallbacks(mCallbackExecutor);
        }

        interface Callback {
            void onEventIdle();
        }
    }
}
