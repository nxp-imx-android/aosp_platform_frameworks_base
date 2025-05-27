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

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoteTaskStore {
    private final Map<Integer, RemoteDeviceTaskList> mRemoteDeviceTaskLists
        = new HashMap<>();

    public RemoteTaskStore() {}

    /**
     * Registers a device with the task store.
     *
     * @param associationId The ID of the device.
     * @param deviceName The name of the device.
     * @param tasks The list of tasks currently available on the device on first
     * connection.
     */
    public void registerDevice(
        int associationId,
        String deviceName,
        List<RemoteTaskInfo> tasks) {

        RemoteDeviceTaskList taskList
            = new RemoteDeviceTaskList(associationId, deviceName, tasks);
        mRemoteDeviceTaskLists.put(associationId, taskList);
    }

    /**
     * Returns the most recent tasks from all devices in the task store.
     *
     * @return A list of the most recent tasks from all devices in the task
     * store.
     */
    public List<RemoteTaskInfo> getMostRecentTasks() {
        List<RemoteTaskInfo> mostRecentTasks = new ArrayList<>();
        for (RemoteDeviceTaskList taskList : mRemoteDeviceTaskLists.values()) {
            RemoteTaskInfo mostRecentTask = taskList.getMostRecentTask();
            if (mostRecentTask != null) {
                mostRecentTasks.add(mostRecentTask);
            }
        }
        return mostRecentTasks;
    }
}