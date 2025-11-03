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
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public class RemoteTaskStore {

    private static final String TAG = RemoteTaskStore.class.getSimpleName();

    /** A task associated with a remote device. */
    public record Task(int associationId, @NonNull RemoteTaskInfo taskInfo) {}

    private record TaskIdentifier(int associationId, int taskId) {}

    @GuardedBy("mTasks")
    private final Map<TaskIdentifier, RemoteTaskInfo> mTasks = new HashMap<>();

    @GuardedBy("mListeners")
    private final Set<Listener> mListeners = new HashSet<>();

    /** Listener for changes to remote device information. */
    public interface Listener {
        /**
         * Called when the remote tasks for a device have changed.
         *
         * @param tasks The list of tasks currently available on the device.
         */
        void onRemoteTasksChanged(@NonNull Collection<Task> tasks);
    }

    /**
     * Sets the task list of the given association id to the given tasks.
     *
     * @param associationId The association id of the device.
     * @param tasks The list of tasks currently available on the device on first connection.
     */
    public void setTasks(int associationId, @NonNull List<RemoteTaskInfo> tasks) {
        synchronized (mTasks) {
            // Remove all tasks for the given association id.
            Iterator<TaskIdentifier> iterator = mTasks.keySet().iterator();
            while (iterator.hasNext()) {
                TaskIdentifier taskIdentifier = iterator.next();
                if (taskIdentifier.associationId() == associationId) {
                    iterator.remove();
                }
            }

            for (RemoteTaskInfo task : tasks) {
                mTasks.put(new TaskIdentifier(associationId, task.id()), task);
            }

            notifyListeners();
        }
    }

    /**
     * Adds or updates the task with the given id in the store and notifies listeners.
     *
     * @param associationId The association id of the device.
     * @param taskInfo The task to add or update.
     */
    public void upsertTask(int associationId, @NonNull RemoteTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo);
        Slog.v(TAG, "Upserting task: " + taskInfo.id() + " for association: " + associationId);
        synchronized (mTasks) {
            TaskIdentifier taskIdentifier = new TaskIdentifier(associationId, taskInfo.id());
            RemoteTaskInfo previousTask = mTasks.get(taskIdentifier);
            if (previousTask == null) {
                Slog.v(TAG, "Adding task: " + taskInfo.id() + " for association: " + associationId);
                mTasks.put(taskIdentifier, taskInfo);
                notifyListeners();
                return;
            }

            if (previousTask.equals(taskInfo)) {
                Slog.v(
                        TAG,
                        "Attempted to upsert task: "
                                + taskInfo.id()
                                + " for association: "
                                + associationId
                                + " but task is unchanged.");
                return;
            }

            mTasks.put(taskIdentifier, taskInfo);
            notifyListeners();
        }
    }

    /**
     * Removes the task with the given id from the store and notifies listeners.
     *
     * @param associationId The association id of the device.
     * @param taskId The id of the task to remove.
     */
    public void removeTask(int associationId, int taskId) {
        Slog.v(TAG, "Removing task: " + taskId + " for association: " + associationId);
        synchronized (mTasks) {
            if (mTasks.remove(new TaskIdentifier(associationId, taskId)) != null) {
                Slog.v(
                        TAG,
                        "Successfully removed task: "
                                + taskId
                                + " for association: "
                                + associationId);
                notifyListeners();
            } else {
                Slog.e(
                        TAG,
                        "Failed to remove task  with id: "
                                + taskId
                                + ". No such task exists for association: "
                                + associationId);
            }
        }
    }

    /**
     * Removes the device with the given id from the store and notifies listeners.
     *
     * @param associationId The id of the device to remove.
     */
    public void removeAssociation(int associationId) {
        synchronized (mTasks) {
            boolean didRemoveTask = false;
            Iterator<TaskIdentifier> iterator = mTasks.keySet().iterator();
            while (iterator.hasNext()) {
                TaskIdentifier taskIdentifier = iterator.next();
                if (taskIdentifier.associationId() == associationId) {
                    mTasks.remove(taskIdentifier);
                    didRemoveTask = true;
                }
            }
            if (didRemoveTask) {
                Slog.v(
                        TAG,
                        "Notifying listeners of device removal for association: " + associationId);
                notifyListeners();
            } else {
                Slog.e(
                        TAG,
                        "Attempted to remove device: "
                                + associationId
                                + " which is not connected.");
            }
        }
    }

    /** Clears all device information from the store and notifies listeners. */
    public void clear() {
        synchronized (mTasks) {
            Slog.v(TAG, "Clearing all device information.");
            mTasks.clear();
            notifyListeners();
        }
    }

    /**
     * Adds a listener to the store.
     *
     * @param listener The listener to add.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            mListeners.add(Objects.requireNonNull(listener));
        }
    }

    /**
     * Removes a listener from the store.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(Objects.requireNonNull(listener));
        }
    }

    private void notifyListeners() {
        List<Task> remoteTasks = new ArrayList<>();
        synchronized (mTasks) {
            for (Entry<TaskIdentifier, RemoteTaskInfo> entry : mTasks.entrySet()) {
                remoteTasks.add(new Task(entry.getKey().associationId(), entry.getValue()));
            }
        }

        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                listener.onRemoteTasksChanged(remoteTasks);
            }
        }
    }
}
