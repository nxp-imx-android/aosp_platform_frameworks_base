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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityTaskManager;
import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskSyncControllerTest {

    private static final int USER_ID = 1;

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    @Mock private TaskBroadcaster mMockTaskBroadcaster;
    @Mock private RemoteTaskStore mMockRemoteTaskStore;
    @Mock private RemoteTaskListenerHolder mMockRemoteTaskListenerHolder;
    @Mock private ActivityTaskManager mMockActivityTaskManager;
    @Mock private ActivityTaskManagerInternal mMockActivityTaskManagerInternal;
    @Mock private RemoteTaskFactory mMockRemoteTaskFactory;
    @Mock private ConnectedAssociationStore mMockConnectedAssociationStore;

    private TaskSyncController mTaskSyncController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockTaskContinuityMessenger.getConnectedAssociationStore())
                .thenReturn(mMockConnectedAssociationStore);
        mTaskSyncController =
                new TaskSyncController(
                        USER_ID,
                        mMockTaskContinuityMessenger,
                        mMockTaskBroadcaster,
                        mMockRemoteTaskStore,
                        mMockRemoteTaskListenerHolder,
                        mMockActivityTaskManager,
                        mMockActivityTaskManagerInternal,
                        mMockRemoteTaskFactory);
    }

    @Test
    public void onEnableAndDisable_associationConnected_registersAndUnregistersListeners() {
        when(mMockConnectedAssociationStore.getConnectedAssociations())
                .thenReturn(
                        List.of(
                                new AssociationInfo.Builder(1, 0, "test_package")
                                        .setDisplayName("test_device")
                                        .build()));
        mTaskSyncController.enable();
        verify(mMockActivityTaskManager).registerTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal)
                .registerHandoffEnablementListener(mMockTaskBroadcaster);

        mTaskSyncController.disable();
        verify(mMockActivityTaskManager).unregisterTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal)
                .unregisterHandoffEnablementListener(mMockTaskBroadcaster);
    }

    @Test
    public void onEnableAndDisable_associationNotConnected_doesNotListenToActivityTaskManager() {
        when(mMockConnectedAssociationStore.getConnectedAssociations()).thenReturn(List.of());
        mTaskSyncController.enable();
        verify(mMockActivityTaskManager, never()).registerTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, never())
                .registerHandoffEnablementListener(mMockTaskBroadcaster);

        mTaskSyncController.disable();
        verify(mMockActivityTaskManager, never()).unregisterTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, never())
                .unregisterHandoffEnablementListener(mMockTaskBroadcaster);
    }

    @Test
    public void onDisable_clearsRemoteTaskStore() {
        mTaskSyncController.enable();
        mTaskSyncController.disable();

        verify(mMockRemoteTaskStore).clear();
    }

    @Test
    public void registerRemoteTaskListener_registersListener() {
        IRemoteTaskListener mockRemoteTaskListener = mock(IRemoteTaskListener.class);
        mTaskSyncController.registerTaskListener(mockRemoteTaskListener);
        verify(mMockRemoteTaskListenerHolder).addListener(mockRemoteTaskListener);
    }

    @Test
    public void unregisterRemoteTaskListener_unregistersListener() {
        IRemoteTaskListener mockRemoteTaskListener = mock(IRemoteTaskListener.class);
        mTaskSyncController.unregisterTaskListener(mockRemoteTaskListener);
        verify(mMockRemoteTaskListenerHolder).removeListener(mockRemoteTaskListener);
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
        when(mMockConnectedAssociationStore.getConnectedAssociations())
                .thenReturn(List.of(associationInfo));
        mTaskSyncController.onAssociationConnected(associationInfo);
        verify(mMockTaskBroadcaster).onDeviceConnected(1);

        verify(mMockActivityTaskManager).registerTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal)
                .registerHandoffEnablementListener(mMockTaskBroadcaster);
    }

    @Test
    public void onSecondAssociationConnected_onlyRegistersListenersOnce() {
        int associationId = 1;
        AssociationInfo associationInfo =
                new AssociationInfo.Builder(associationId, 0, "test_package")
                        .setDisplayName("test_device")
                        .build();
        when(mMockConnectedAssociationStore.getConnectedAssociations())
                .thenReturn(List.of(associationInfo));
        mTaskSyncController.onAssociationConnected(associationInfo);
        mTaskSyncController.onAssociationConnected(associationInfo);
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, times(1))
                .registerHandoffEnablementListener(mMockTaskBroadcaster);
    }

    @Test
    public void onAssociationDisconnected_notifiesStoreAndBroadcaster() {
        int associationId = 1;
        AssociationInfo associationInfo =
                new AssociationInfo.Builder(associationId, 0, "test_package")
                        .setDisplayName("test_device")
                        .build();
        when(mMockConnectedAssociationStore.getConnectedAssociations())
                .thenReturn(List.of(associationInfo));
        mTaskSyncController.onAssociationConnected(associationInfo);
        when(mMockConnectedAssociationStore.getConnectedAssociations()).thenReturn(List.of());
        mTaskSyncController.onAssociationDisconnected(associationId);
        verify(mMockRemoteTaskStore).removeAssociation(associationId);
        verify(mMockActivityTaskManager).unregisterTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal)
                .unregisterHandoffEnablementListener(mMockTaskBroadcaster);
    }

    @Test
    public void onAssociationDisconnected_notListening_doesNotUnregisterListeners() {
        int associationId = 1;
        mTaskSyncController.onAssociationDisconnected(associationId);
        verify(mMockActivityTaskManager, never()).unregisterTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, never())
                .unregisterHandoffEnablementListener(mMockTaskBroadcaster);
    }


    @Test
    public void onTaskStackBroadcastMessageReceived_setsTasks() {
        int associationId = 1;
        TaskStackBroadcastMessage taskStackBroadcastMessage =
                new TaskStackBroadcastMessage(new ArrayList<>());
        mTaskSyncController.onTaskStackBroadcastMessageReceived(
                associationId, taskStackBroadcastMessage);
        verify(mMockRemoteTaskStore).setTasks(associationId, new ArrayList<>());
    }

    @Test
    public void onRemoteTasksChanged_notifiesListener() {
        int associationId = 1;
        String associationDisplayName = "test_device";
        when(mMockTaskContinuityMessenger.getAssociationInfo(associationId))
                .thenReturn(
                        new AssociationInfo.Builder(associationId, 0, "test_package")
                                .setDisplayName(associationDisplayName)
                                .build());
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, "package_name", true, 100, new HandoffOptions(true, true));
        RemoteTask remoteTask =
                new RemoteTask.Builder(1, 1).setAssociationDisplayName("test_device").build();
        when(mMockRemoteTaskFactory.create(associationId, associationDisplayName, remoteTaskInfo))
                .thenReturn(remoteTask);
        List<RemoteTaskStore.Task> tasks =
                List.of(new RemoteTaskStore.Task(associationId, remoteTaskInfo));
        ArgumentCaptor<List<RemoteTask>> remoteTasksCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Integer> associationIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> associationDisplayNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RemoteTaskInfo> remoteTaskInfoCaptor =
                ArgumentCaptor.forClass(RemoteTaskInfo.class);
        mTaskSyncController.onRemoteTasksChanged(tasks);
        verify(mMockRemoteTaskFactory)
                .create(
                        associationIdCaptor.capture(),
                        associationDisplayNameCaptor.capture(),
                        remoteTaskInfoCaptor.capture());
        assertThat(associationIdCaptor.getValue()).isEqualTo(associationId);
        assertThat(associationDisplayNameCaptor.getValue()).isEqualTo(associationDisplayName);
        assertThat(remoteTaskInfoCaptor.getValue()).isEqualTo(remoteTaskInfo);
        verify(mMockRemoteTaskListenerHolder).notifyListeners(remoteTasksCaptor.capture());
        assertThat(remoteTasksCaptor.getValue()).hasSize(1);
        assertThat(remoteTasksCaptor.getValue().get(0)).isEqualTo(remoteTask);
    }
}
