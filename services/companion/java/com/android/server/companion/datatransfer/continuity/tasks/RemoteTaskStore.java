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

import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import android.companion.datatransfer.continuity.RemoteTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoteTaskStore implements ConnectedAssociationStore.Observer {

    private static final String TAG = "RemoteTaskStore";

    private final ConnectedAssociationStore mConnectedAssociationStore;
    private final Map<Integer, RemoteDeviceTaskList> mRemoteDeviceTaskLists
        = new HashMap<>();
    private final RemoteCallbackList<IRemoteTaskListener>
        mRemoteTaskListeners = new RemoteCallbackList<>();

    public RemoteTaskStore(ConnectedAssociationStore connectedAssociationStore) {
        mConnectedAssociationStore = connectedAssociationStore;
        mConnectedAssociationStore.addObserver(this);
    }

    /**
     * Sets the task list of the given association id to the given tasks.
     *
     * @param associationId The association id of the device.
     * @param tasks The list of tasks currently available on the device on first
     * connection.
     */
    public void setTasks(
        int associationId,
        List<RemoteTaskInfo> tasks) {
        synchronized (mRemoteDeviceTaskLists) {
            if (!mRemoteDeviceTaskLists.containsKey(associationId)) {
                Slog.e(
                    TAG,
                    "Attempted to set tasks for association: " + associationId
                        + " which is not connected.");

                return;
            }

            mRemoteDeviceTaskLists.get(associationId).setTasks(tasks);
        }
    }

      public void addTask(int associationId, RemoteTaskInfo taskInfo) {
        synchronized (mRemoteDeviceTaskLists) {
            if (!mRemoteDeviceTaskLists.containsKey(associationId)) {
                Slog.e(
                    TAG,
                    "addTask failure for association: " + associationId + " - not connected.");

                return;
            }

            Slog.v(TAG, "Adding task: " + taskInfo.id() + " for association: " + associationId);
            mRemoteDeviceTaskLists.get(associationId).addTask(taskInfo);
        }
    }

   public void removeTask(int associationId, int taskId) {
        synchronized (mRemoteDeviceTaskLists) {
            if (!mRemoteDeviceTaskLists.containsKey(associationId)) {
                return;
            }

            mRemoteDeviceTaskLists.get(associationId).removeTask(taskId);
        }
    }

    public void updateTask(int associationId, RemoteTaskInfo taskInfo) {
        synchronized (mRemoteDeviceTaskLists) {
            if (!mRemoteDeviceTaskLists.containsKey(associationId)) {
                return;
            }

            mRemoteDeviceTaskLists.get(associationId).updateTask(taskInfo);
        }
    }

    /**
     * Returns the most recent tasks from all devices in the task store.
     *
     * @return A list of the most recent tasks from all devices in the task
     * store.
     */
    public List<RemoteTask> getMostRecentTasks() {
        synchronized (mRemoteDeviceTaskLists) {
            List<RemoteTask> mostRecentTasks = new ArrayList<>();
            for (RemoteDeviceTaskList taskList : mRemoteDeviceTaskLists.values()) {
                RemoteTask mostRecentTask = taskList.getMostRecentTask();
                if (mostRecentTask != null) {
                    mostRecentTasks.add(mostRecentTask);
                }
            }
            return mostRecentTasks;
        }
    }

    public void addListener(IRemoteTaskListener listener) {
        synchronized (mRemoteTaskListeners) {
            mRemoteTaskListeners.register(listener);
        }
    }

    public void removeListener(IRemoteTaskListener listener) {
        synchronized (mRemoteTaskListeners) {
            mRemoteTaskListeners.unregister(listener);
        }
    }

    @Override
    public void onTransportConnected(AssociationInfo associationInfo) {
        synchronized (mRemoteDeviceTaskLists) {
            if (!mRemoteDeviceTaskLists.containsKey(associationInfo.getId())) {
                Slog.v(
                    TAG,
                    "Creating new RemoteDeviceTaskList for association: "
                        + associationInfo.getId());

                RemoteDeviceTaskList taskList
                    = new RemoteDeviceTaskList(
                        associationInfo.getId(),
                        associationInfo.getDisplayName().toString(),
                        this::onMostRecentTaskChanged);

                mRemoteDeviceTaskLists.put(associationInfo.getId(), taskList);
            } else {
                Slog.v(
                    TAG,
                    "Transport already connected for association: " + associationInfo.getId());
            }
        }
    }

    @Override
    public void onTransportDisconnected(int associationId) {
        synchronized (mRemoteDeviceTaskLists) {
            Slog.v(
                TAG,
                "Deleting RemoteDeviceTaskList for association: " + associationId);

            mRemoteDeviceTaskLists.remove(associationId);
            notifyListeners();
        }
    }

    private void onMostRecentTaskChanged(RemoteTask task) {
        notifyListeners();
    }

    private void notifyListeners() {
       synchronized (mRemoteTaskListeners) {
            List<RemoteTask> remoteTasks = getMostRecentTasks();
            int i = mRemoteTaskListeners.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mRemoteTaskListeners
                        .getBroadcastItem(i)
                        .onRemoteTasksChanged(remoteTasks);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to notify listener: " + e.getMessage());
                }
            }
            mRemoteTaskListeners.finishBroadcast();
        }
    }
}