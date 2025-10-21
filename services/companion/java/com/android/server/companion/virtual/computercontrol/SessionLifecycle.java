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
import android.annotation.Nullable;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.LifecycleState;
import android.companion.virtual.computercontrol.LifecycleState.Blocked;
import android.companion.virtual.computercontrol.LifecycleStateTracker;
import android.companion.virtualdevice.flags.Flags;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of a computer control session. Thread safe.
 */
final class SessionLifecycle {

    private static final String TAG = "ComputerControlLifecycle";

    @GuardedBy("mLifecycle")
    private final LifecycleStateTracker mLifecycle = new LifecycleStateTracker();
    @GuardedBy("mLifecycle")
    private boolean mIsRemoteCallbackAdded = false;
    @GuardedBy("mLifecycle")
    private final LifecycleConfig mLifecycleConfig = new LifecycleConfig();
    @GuardedBy("mLifecycle")
    private LifecycleState mCurrentState = null;

    /** Configuration args that fully determine the lifecycle state of the session. */
    static final class LifecycleConfig {

        /** When non-null, indicates that the session was closed. */
        @Nullable
        LifecycleState.Closed mClosed = null;

        /** Package name of the blocking activity is running on the display, if any. */
        @Nullable
        String mBlockingActivityPackage = null;

        /** Package name of the app showing a FLAG_SECURE window on the display, if any. */
        @Nullable
        String mSecureWindowPackage = null;

        @NonNull
        private LifecycleState computeState(
                LifecycleState previousState) {
            if (mClosed != null) {
                return mClosed;
            }
            if (Flags.computerControlBlockedState()
                    && (mBlockingActivityPackage != null || mSecureWindowPackage != null)) {
                return mBlockingActivityPackage != null
                        ? new Blocked(
                                ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                                mBlockingActivityPackage)
                        : new Blocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT,
                                mSecureWindowPackage);
            }
            return LifecycleState.ACTIVE;
        }
    }

    SessionLifecycle(ComputerControlSession.LifecycleCallback localCallback) {
        mLifecycle.addCallback(localCallback);
    }

    /**
     * Atomically updates the session lifecycle by modifying the config via the provided update
     * function, and fires all of the callbacks for the new state.
     *
     * @param update A function that modifies the lifecycle config.
     * @return The lifecycle state after the update.
     */
    @NonNull
    LifecycleState updateLifecycleState(Consumer<LifecycleConfig> update) {
        synchronized (mLifecycle) {
            final var previousState = mCurrentState;
            update.accept(mLifecycleConfig);
            mCurrentState = mLifecycleConfig.computeState(previousState);
            if (Objects.equals(mCurrentState, previousState)) {
                return mCurrentState;
            }
            switch (mCurrentState) {
                case LifecycleState.Active ignored ->
                        mLifecycle.onActive();
                case Blocked blocked ->
                        mLifecycle.onBlocked(blocked.reason, blocked.blockingPackage);
                case LifecycleState.Closed closed ->
                        mLifecycle.onClosed(closed.reason);
            }
            Slog.i(TAG, "Updated lifecycle state from " + previousState + " to " + mCurrentState);
            return mCurrentState;
        }
    }

    /**
     * Returns the current lifecycle state to make policy decisions based on the state.
     *
     * NOTE: Use the value returned by {@link #updateLifecycleState(Consumer)} to atomically get the
     *   updated state if there is a possibility for the state to change.
     */
    LifecycleState getCurrentState() {
        synchronized (mLifecycle) {
            return mCurrentState;
        }
    }

    void initializeWithRemoteCallback(IComputerControlLifecycleCallback callback) {
        synchronized (mLifecycle) {
            if (mIsRemoteCallbackAdded) {
                throw new IllegalStateException("Callback already set");
            }
            mIsRemoteCallbackAdded = true;

            // Compute the initial state, if it wasn't already computed.
            updateLifecycleState((config) -> {});

            // Adding the remote callback will immediately notify it of the initial state.
            mLifecycle.addCallback(new ComputerControlSession.LifecycleCallback() {
                @Override
                public void onActive() {
                    try {
                        callback.onActive();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }

                @Override
                public void onBlocked(
                        @ComputerControlSession.SessionBlockReason int reason,
                        @Nullable String blockingPackage) {
                    try {
                        callback.onBlocked(reason, blockingPackage);
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }

                @Override
                public void onClosed(@ComputerControlSession.SessionCloseReason int reason) {
                    try {
                        callback.onClosed(reason);
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }
            });
        }
    }
}
