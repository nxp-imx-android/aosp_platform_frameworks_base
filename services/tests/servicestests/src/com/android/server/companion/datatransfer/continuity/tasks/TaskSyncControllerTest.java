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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;
import com.android.server.companion.datatransfer.continuity.tasks.TaskBroadcaster;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskStore;

import java.util.Collection;
import java.util.ArrayList;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskSyncControllerTest {

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    @Mock private TaskBroadcaster mMockTaskBroadcaster;
    @Mock private RemoteTaskStore mMockRemoteTaskStore;

    private TaskSyncController mTaskSyncController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskSyncController =
                new TaskSyncController(
                        mMockTaskContinuityMessenger, mMockTaskBroadcaster, mMockRemoteTaskStore);
    }

    @Test
    public void registerRemoteTaskListener_registersListener() {
        IRemoteTaskListener mockRemoteTaskListener = mock(IRemoteTaskListener.class);
        mTaskSyncController.registerTaskListener(mockRemoteTaskListener);
        verify(mMockRemoteTaskStore).addListener(mockRemoteTaskListener);
    }

    @Test
    public void unregisterRemoteTaskListener_unregistersListener() {
        IRemoteTaskListener mockRemoteTaskListener = mock(IRemoteTaskListener.class);
        mTaskSyncController.unregisterTaskListener(mockRemoteTaskListener);
        verify(mMockRemoteTaskStore).removeListener(mockRemoteTaskListener);
    }

    @Test
    public void removeTask_removesTask() {
        int associationId = 1;
        int taskId = 2;
        mTaskSyncController.removeTask(associationId, taskId);
        verify(mMockRemoteTaskStore).removeTask(associationId, taskId);
    }

    @Test
    public void onAssociationConnected_notifiesStoreAndBroadcaster() {
        int associationId = 1;
        AssociationInfo associationInfo =
                new AssociationInfo.Builder(associationId, 0, "test_package")
                        .setDisplayName("test_device")
                        .build();
        mTaskSyncController.onAssociationConnected(associationInfo);
        verify(mMockRemoteTaskStore).addDevice(1, "test_device");
        verify(mMockTaskBroadcaster).onDeviceConnected(1);
    }

    @Test
    public void onAssociationDisconnected_notifiesStoreAndBroadcaster() {
        int associationId = 1;
        Collection<AssociationInfo> connectedAssociations = new ArrayList<>();
        mTaskSyncController.onAssociationDisconnected(associationId, connectedAssociations);
        verify(mMockRemoteTaskStore).removeDevice(associationId);
        verify(mMockTaskBroadcaster).onAllDevicesDisconnected();
    }

    @Test
    public void onRemoteTaskRemovedMessageReceived_removesTask() {
        int associationId = 1;
        int taskId = 2;
        RemoteTaskRemovedMessage remoteTaskRemovedMessage = new RemoteTaskRemovedMessage(taskId);
        mTaskSyncController.onRemoteTaskRemovedMessageReceived(
                associationId, remoteTaskRemovedMessage);
        verify(mMockRemoteTaskStore).removeTask(associationId, taskId);
    }

    @Test
    public void onRemoteTaskAddedMessageReceived_addsTask() {
        int associationId = 1;
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "label", 100, new byte[0], false);
        RemoteTaskAddedMessage remoteTaskAddedMessage = new RemoteTaskAddedMessage(remoteTaskInfo);
        mTaskSyncController.onRemoteTaskAddedMessageReceived(associationId, remoteTaskAddedMessage);
        verify(mMockRemoteTaskStore).addTask(associationId, remoteTaskInfo);
    }

    @Test
    public void onRemoteTaskUpdatedMessageReceived_updatesTask() {
        int associationId = 1;
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "label", 100, new byte[0], false);
        RemoteTaskUpdatedMessage remoteTaskUpdatedMessage =
                new RemoteTaskUpdatedMessage(remoteTaskInfo);
        mTaskSyncController.onRemoteTaskUpdatedMessageReceived(
                associationId, remoteTaskUpdatedMessage);
        verify(mMockRemoteTaskStore).updateTask(associationId, remoteTaskInfo);
    }

    @Test
    public void onContinuityDeviceConnectedMessageReceived_setsTasks() {
        int associationId = 1;
        ContinuityDeviceConnected continuityDeviceConnected =
                new ContinuityDeviceConnected(new ArrayList<>());
        mTaskSyncController.onContinuityDeviceConnectedMessageReceived(
                associationId, continuityDeviceConnected);
        verify(mMockRemoteTaskStore).setTasks(associationId, new ArrayList<>());
    }
}
