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

package com.android.server.companion.datatransfer.continuity.tasks;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A holder for {@link IRemoteTaskListener}s.
 *
 * <p>This class is responsible for notifying listeners when remote tasks change.
 */
public class RemoteTaskListenerHolder {

    private static final String TAG = RemoteTaskListenerHolder.class.getSimpleName();

    @GuardedBy("this")
    private final RemoteCallbackList<IRemoteTaskListener> mListeners = new RemoteCallbackList<>();

    private List<RemoteTask> mLastBroadcastedTasks = new ArrayList<>();

    /**
     * Adds a listener to the holder and notifies it immediately.
     *
     * @param listener the listener to add
     */
    public void addListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            mListeners.register(Objects.requireNonNull(listener));
            notifyListener(listener);
        }
    }

    /**
     * Removes a listener from the holder.
     *
     * @param listener the listener to remove
     */
    public void removeListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            mListeners.unregister(Objects.requireNonNull(listener));
        }
    }

    /**
     * Notifies all listeners that the remote tasks have changed.
     *
     * @param remoteTasks the list of remote tasks that have changed
     */
    public void notifyListeners(@NonNull List<RemoteTask> remoteTasks) {
        synchronized (this) {
            mLastBroadcastedTasks = remoteTasks;
            mListeners.broadcast(listener -> notifyListener(listener));
        }
    }

    private void notifyListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            try {
                listener.onRemoteTasksChanged(mLastBroadcastedTasks);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to notify listener: " + e.getMessage());
            }
        }
    }
}
