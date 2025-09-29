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

import static android.companion.virtual.computercontrol.SessionLifecycleTrackerState.ACTIVE;

import android.annotation.NonNull;
import android.companion.virtual.computercontrol.SessionLifecycleTrackerState.Active;
import android.companion.virtual.computercontrol.SessionLifecycleTrackerState.Closed;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the lifecycle state transitions for a ComputerControlSession. It is used to notify
 * lifecycle callbacks.
 *
 * <p>Callbacks are fired in the order they are added. All callbacks are fired from the calling
 * thread.
 *
 * NOTE: This class is NOT thread safe.
 *
 * @hide
 */
public final class SessionLifecycleTracker implements ComputerControlSession.LifecycleCallback {

    private static final SessionLifecycleTrackerState INITIAL_STATE = ACTIVE;

    private final List<ComputerControlSession.LifecycleCallback> mCallbacks = new ArrayList<>();

    private SessionLifecycleTrackerState mState = INITIAL_STATE;

    /**
     * Adds a lifecycle callback that should be notified for state changes. When a new callback is
     * added, it is assumed to be in an "active" state.
     */
    public void addCallback(@NonNull ComputerControlSession.LifecycleCallback callback) {
        if (mCallbacks.contains(callback)) {
            throw new IllegalStateException("Callback already registered");
        }
        mCallbacks.add(callback);
        notifyCallback(callback);
    }

    /**
     * Removes a lifecycle callback.
     */
    public void removeCallback(@NonNull ComputerControlSession.LifecycleCallback callback) {
        if (!mCallbacks.remove(callback)) {
            throw new IllegalStateException("Callback not registered");
        }
    }

    private void notifyAllCallbacks() {
        for (ComputerControlSession.LifecycleCallback callback : new ArrayList<>(mCallbacks)) {
            notifyCallback(callback);
        }
    }

    private void notifyCallback(ComputerControlSession.LifecycleCallback callback) {
        switch (mState) {
            case Active ignored -> {
            }
            case Closed closed -> {
                callback.onClosed(closed.reason);
            }
        }
    }

    @Override
    public void onClosed(@ComputerControlSession.SessionCloseReason int reason) {
        if (mState instanceof Closed) {
            return;
        }
        mState = new Closed(reason);
        notifyAllCallbacks();
    }
}
