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
import java.util.List;

/**
 * Tracks remote tasks currently available on a specific remote device.
 */
class RemoteDeviceTaskList {
    private final int mAssociationId;
    private final String mDeviceName;
    private final List<RemoteTaskInfo> mTasks = new ArrayList<>();

    RemoteDeviceTaskList(
        int associationId,
        String deviceName) {

        mAssociationId = associationId;
        mDeviceName = deviceName;
    }

    /**
     * Returns the association ID of the remote device.
     */
    int getAssociationId() {
        return mAssociationId;
    }

    /**
     * Returns the device name of the remote device.
     */
    String getDeviceName() {
        return mDeviceName;
    }

    /**
     * Adds a task to the list of tasks currently available on the remote
     * device.
     */
    void addTask(RemoteTaskInfo taskInfo) {
        mTasks.add(taskInfo);
    }

    /**
     * Sets the list of tasks currently available on the remote device.
     */
    void setTasks(List<RemoteTaskInfo> tasks) {
        mTasks.clear();
        mTasks.addAll(tasks);
    }

    /**
     * Gets the most recently used task on this device, or null if there are no
     * tasks.
     */
    RemoteTaskInfo getMostRecentTask() {
        if (mTasks.isEmpty()) {
            return null;
        }

        RemoteTaskInfo mostRecentTask = mTasks.get(0);
        for (RemoteTaskInfo task : mTasks) {
            if (
                task.getLastUsedTimeMillis()
                    > mostRecentTask.getLastUsedTimeMillis()) {

                mostRecentTask = task;
            }
        }
        return mostRecentTask;
    }
}