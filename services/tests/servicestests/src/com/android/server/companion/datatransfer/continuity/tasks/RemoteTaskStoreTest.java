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
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createAssociationInfo;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createRunningTaskInfo;

import android.app.ActivityManager;
import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.RemoteTask;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
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

    @Mock
    private ConnectedAssociationStore mMockConnectedAssociationStore;

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
        taskStore = new RemoteTaskStore(mMockConnectedAssociationStore);
        taskStore.addListener(mRemoteTaskListener);
    }

    @Test
    public void constructor_registersObserver() {
        verify(mMockConnectedAssociationStore, times(1))
            .addObserver(taskStore);
    }

    @Test
    public void onTransportConnected_addsNewAssociationAndNotifiesListeners() {
        // Simulate a new association being connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        taskStore.onTransportConnected(associationInfo);
        assertThat(remoteTasksReportedToListener).hasSize(0);

        // Add tasks to the new association.
        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);
        RemoteTask remoteTask
            = remoteTaskInfo.toRemoteTask(associationInfo.getId(), "name");

        taskStore.setTasks(
            associationInfo.getId(),
            Collections.singletonList(remoteTaskInfo));
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(remoteTask);

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
    }

    @Test
    public void setTasks_doesNotAddADeviceIfNoInformationAvailable() {
        when(mMockConnectedAssociationStore.getConnectedAssociationById(0))
            .thenReturn(null);

        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);

        // Add the task. Since ConnectedAssociationStore does not have this
        // association, this should be ignored.
        taskStore.setTasks(0, Collections.singletonList(remoteTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        assertThat(remoteTasksReportedToListener).isEmpty();
    }

    @Test
    public void removeTask_removesTask() {
        // Setup an association.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        taskStore.onTransportConnected(associationInfo);

        // Add two tasks
        RemoteTaskInfo mostRecentTaskInfo = createNewRemoteTaskInfo(1, "task1", 200);
        RemoteTask mostRecentTask
            = mostRecentTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        RemoteTaskInfo secondMostRecentTaskInfo = createNewRemoteTaskInfo(2, "task2", 100);
        RemoteTask secondMostRecentTask
            = secondMostRecentTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        taskStore.setTasks(
            associationInfo.getId(),
            Arrays.asList(mostRecentTaskInfo, secondMostRecentTaskInfo));

        assertThat(taskStore.getMostRecentTasks())
            .containsExactly(mostRecentTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(mostRecentTask);

        taskStore.removeTask(associationInfo.getId(), mostRecentTaskInfo.getId());
        assertThat(taskStore.getMostRecentTasks()).containsExactly(secondMostRecentTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1))
            .containsExactly(secondMostRecentTask);
    }

    @Test
    public void onTransportDisconnected_removesAssociationAndNotifiesListeners() {
        // Create a fake association info, and have connected association store
        // return it.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(1))
                .thenReturn(associationInfo);
        taskStore.onTransportConnected(associationInfo);

        // Set tasks for the association.
        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);
        taskStore.setTasks(associationInfo.getId(), Collections.singletonList(remoteTaskInfo));
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0))
            .containsExactly(remoteTaskInfo.toRemoteTask(1, "name"));

        // Simulate the association being disconnected.
        taskStore.onTransportDisconnected(associationInfo.getId());

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).isEmpty();
    }

    @Test
    public void addTask_addsTaskToAssociationAndNotifiesListeners() {
        // Create a fake association info, and have connected association store return it.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(1))
            .thenReturn(associationInfo);
        taskStore.onTransportConnected(associationInfo);

        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        taskStore.setTasks(1, Collections.singletonList(remoteTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(remoteTask);

        // Add a new task to the association.
        RemoteTaskInfo newRemoteTaskInfo = createNewRemoteTaskInfo(2, "task2", 200L);
        RemoteTask newRemoteTask = newRemoteTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        taskStore.addTask(1, newRemoteTaskInfo);

        // Verify the most recent tasks are added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(newRemoteTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).containsExactly(newRemoteTask);
    }

    @Test
    public void addTask_doesNotAddTaskIfAssociationNotConnected() {
        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);
        taskStore.addTask(1, remoteTaskInfo);
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    @Test
    public void updateTask_updatesTaskAndNotifiesListeners() {
        // Create a fake association info, and have connected association store return it.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(1))
            .thenReturn(associationInfo);
        taskStore.onTransportConnected(associationInfo);

        RemoteTaskInfo initialTaskInfo = createNewRemoteTaskInfo(1, "task1", 100L);
        RemoteTask initialTask = initialTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        taskStore.setTasks(1, Collections.singletonList(initialTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(initialTask);
        assertThat(remoteTasksReportedToListener).hasSize(1);
        assertThat(remoteTasksReportedToListener.get(0)).containsExactly(initialTask);

        // Update the task to have a different name.
        RemoteTaskInfo updatedTaskInfo = createNewRemoteTaskInfo(
            initialTaskInfo.getId(),
            "task1",
            200L);
        RemoteTask updatedTask = updatedTaskInfo.toRemoteTask(associationInfo.getId(), "name");
        taskStore.updateTask(1, updatedTaskInfo);

        assertThat(taskStore.getMostRecentTasks()).containsExactly(updatedTask);
        assertThat(remoteTasksReportedToListener).hasSize(2);
        assertThat(remoteTasksReportedToListener.get(1)).containsExactly(updatedTask);
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(
        int id,
        String label,
        long lastUsedTimeMillis) {

        ActivityManager.RunningTaskInfo runningTaskInfo
            = createRunningTaskInfo(id, label, lastUsedTimeMillis);

        return new RemoteTaskInfo(runningTaskInfo);
    }
}