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
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskStoreTest {

    @Mock
    private ConnectedAssociationStore mMockConnectedAssociationStore;

    private RemoteTaskStore taskStore;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        taskStore = new RemoteTaskStore(mMockConnectedAssociationStore);
    }

    @Test
    public void constructor_registersObserver() {
        verify(mMockConnectedAssociationStore, times(1))
            .addObserver(taskStore);
    }

    @Test
    public void onTransportConnected_addsNewAssociation() {
        // Simulate a new association being connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        taskStore.onTransportConnected(associationInfo);

        // Add tasks to the new association.
        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo("task1", 100L);
        taskStore.setTasks(
            associationInfo.getId(),
            Collections.singletonList(remoteTaskInfo));

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks())
            .containsExactly(remoteTaskInfo);
    }

    @Test
    public void setTasks_doesNotAddADeviceIfNoInformationAvailable() {
        when(mMockConnectedAssociationStore.getConnectedAssociationById(0))
            .thenReturn(null);

        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo("task1", 100L);

        // Add the task. Since ConnectedAssociationStore does not have this
        // association, this should be ignored.
        taskStore.setTasks(0, Collections.singletonList(remoteTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    @Test
    public void onTransportDisconnected_removesAssociation() {
        // Create a fake association info, and have connected association store
        // return it.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(1))
                .thenReturn(associationInfo);

        // Set tasks for the association.
        RemoteTaskInfo remoteTaskInfo = createNewRemoteTaskInfo("task1", 100L);
        taskStore.setTasks(0, Collections.singletonList(remoteTaskInfo));

        // Simulate the association being disconnected.
        taskStore.onTransportDisconnected(0);

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(
        String label,
        long lastUsedTimeMillis) {

        ActivityManager.RunningTaskInfo runningTaskInfo
            = createRunningTaskInfo(1, label, lastUsedTimeMillis);

        return new RemoteTaskInfo(runningTaskInfo);
    }
}