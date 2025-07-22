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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import android.companion.datatransfer.continuity.RemoteTask;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskStoreTest {

    private final IRemoteTaskListener mRemoteTaskListener = new IRemoteTaskListener.Stub() {
        @Override
        public void onRemoteTasksChanged(List<RemoteTask> remoteTasks) {
            remoteTasksReportedToListener.add(remoteTasks);
        }
    };

    private final List<List<RemoteTask>> remoteTasksReportedToListener = new ArrayList<>();
    private RemoteTaskStore taskStore;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        remoteTasksReportedToListener.clear();
        taskStore = new RemoteTaskStore();
        taskStore.addListener(mRemoteTaskListener);
    }

    @Test
    public void addDevice_addsDeviceAndNotifiesListeners() {
        // Simulate a new association being connected.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);
        assertThat(remoteTasksReportedToListener).hasSize(0);

        // Add tasks to the new association.
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(deviceId, deviceName);

        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(remoteTask);

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
    }

    @Test
    public void setTasks_doesNotSetIfDeviceNotAdded() {
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);

        // Add the task. Since ConnectedAssociationStore does not have this
        // association, this should be ignored.
        taskStore.setTasks(0, Collections.singletonList(remoteTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        assertThat(remoteTasksReportedToListener).isEmpty();
    }

    @Test
    public void removeTask_removesTask() {
        // Setup an association.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        // Add two tasks
        RemoteTaskInfo mostRecentTaskInfo = new RemoteTaskInfo(1, "task1", 200, new byte[0]);
        RemoteTask mostRecentTask = mostRecentTaskInfo.toRemoteTask(deviceId, deviceName);
        RemoteTaskInfo secondMostRecentTaskInfo = new RemoteTaskInfo(2, "task2", 100,  new byte[0]);
        RemoteTask secondMostRecentTask
            = secondMostRecentTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(
            deviceId,
            Arrays.asList(mostRecentTaskInfo, secondMostRecentTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).containsExactly(mostRecentTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(mostRecentTask);

        taskStore.removeTask(deviceId, mostRecentTaskInfo.id());
        assertThat(taskStore.getMostRecentTasks()).containsExactly(secondMostRecentTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).containsExactly(secondMostRecentTask);
    }

    @Test
    public void removeDevice_removesDeviceAndNotifiesListeners() {
        // Create a fake association info, and have connected association store
        // return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        // Set tasks for the association.
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);
        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0))
            .containsExactly(remoteTaskInfo.toRemoteTask(deviceId, deviceName));

        // Simulate the association being disconnected.
        taskStore.removeDevice(deviceId);

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).isEmpty();
    }

    @Test
    public void addTask_addsTaskToAssociationAndNotifiesListeners() {
        // Create a fake association info, and have connected association store return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(remoteTask);

        // Add a new task to the association.
        RemoteTaskInfo newRemoteTaskInfo = new RemoteTaskInfo(2, "task2", 200L, new byte[0]);
        RemoteTask newRemoteTask = newRemoteTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.addTask(deviceId, newRemoteTaskInfo);

        // Verify the most recent tasks are added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(newRemoteTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).containsExactly(newRemoteTask);
    }

    @Test
    public void addTask_doesNotAddTaskIfDeviceNotAdded() {
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);
        taskStore.addTask(1, remoteTaskInfo);
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    @Test
    public void updateTask_updatesTaskAndNotifiesListeners() {
        // Create a fake association info, and have connected association store return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        RemoteTaskInfo initialTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0]);
        RemoteTask initialTask = initialTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(deviceId, Collections.singletonList(initialTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(initialTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(initialTask);

        RemoteTaskInfo updatedTaskInfo = new RemoteTaskInfo(
            initialTaskInfo.id(),
            "task1",
            200L,
            new byte[0]);

        RemoteTask updatedTask = updatedTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.updateTask(deviceId, updatedTaskInfo);
        assertThat(taskStore.getMostRecentTasks()).containsExactly(updatedTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).containsExactly(updatedTask);
    }
}