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

package com.android.server.companion.datatransfer.continuity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createAssociationInfo;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createRunningTaskInfo;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.companion.AssociationInfo;
import android.companion.IOnTransportsChangedListener;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.AssociationInfo;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskBroadcasterTest {

    private Context mMockContext;

    @Mock
    private ActivityTaskManager mMockActivityTaskManager;

    @Mock
    private ICompanionDeviceManager mMockCompanionDeviceManagerService;

    private CompanionDeviceManager mCompanionDeviceManager;

    @Mock private ConnectedAssociationStore mMockConnectedAssociationStore;

    private TaskBroadcaster mTaskBroadcaster;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext =  Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));

        // Setup fake services.
        mCompanionDeviceManager
            = new CompanionDeviceManager(
                mMockCompanionDeviceManagerService,
                mMockContext);

        when(mMockContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
            .thenReturn(mMockActivityTaskManager);
        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
            .thenReturn(mCompanionDeviceManager);

        // Create TaskBroadcaster.
        mTaskBroadcaster = new TaskBroadcaster(
            mMockContext,
            mMockConnectedAssociationStore);
    }

    @Test
    public void testStopBroadcasting_doesNothingIfNotBroadcasting()
        throws Exception {

        mTaskBroadcaster.stopBroadcasting();
        verify(mMockConnectedAssociationStore, never())
            .addObserver(mTaskBroadcaster);
    }

    @Test
    public void testStartAndStopBroadcasting_updatesTransportsListener()
        throws Exception {

        // Start broadcasting, verifying an association listener is added.
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1))
            .addObserver(mTaskBroadcaster);
        verify(mMockConnectedAssociationStore, times(1))
            .addObserver(mTaskBroadcaster);
        verify(mMockActivityTaskManager, times(1))
            .registerTaskStackListener(mTaskBroadcaster);

        // Stop broadcasting, verifying the association listener is removed.
        mTaskBroadcaster.stopBroadcasting();
        verify(mMockConnectedAssociationStore, times(1))
            .removeObserver(mTaskBroadcaster);
        verify(mMockActivityTaskManager, times(1))
            .unregisterTaskStackListener(mTaskBroadcaster);
        verify(mMockConnectedAssociationStore, times(1))
            .removeObserver(mTaskBroadcaster);
    }

    @Test
    public void testStartBroadcasting_startsBroadcasting() throws Exception {
        // Start broadcasting, verifying a transport listener is added.
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1))
            .addObserver(mTaskBroadcaster);

        // Setup a fake foreground task.
        String expectedLabel = "test";
        ActivityManager.RunningTaskInfo taskInfo = createRunningTaskInfo(1, expectedLabel, 0);

        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
            .thenReturn(Arrays.asList(taskInfo));

        // Add a new transport
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        mTaskBroadcaster.onTransportConnected(associationInfo);

        // Verify the message is sent.
        ArgumentCaptor<byte[]> messageCaptor
            = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_TASK_CONTINUITY),
            messageCaptor.capture(),
            eq(new int[] {1}));
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(
            messageCaptor.getValue());
        assertThat(taskContinuityMessage.getData()).isInstanceOf(
            ContinuityDeviceConnected.class);
        ContinuityDeviceConnected continuityDeviceConnected
            = (ContinuityDeviceConnected) taskContinuityMessage.getData();
        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(taskInfo.taskId);
        assertThat(continuityDeviceConnected.getRemoteTasks()).hasSize(1);
        assertThat(continuityDeviceConnected.getRemoteTasks().get(0).getId())
            .isEqualTo(taskInfo.taskId);
        assertThat(continuityDeviceConnected.getRemoteTasks().get(0).getLabel())
            .isEqualTo(expectedLabel);
    }

    @Test
    public void testOnTaskCreated_sendsMessageToAllAssociations() throws Exception {
        // Start broadcasting.
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1)).addObserver(mTaskBroadcaster);
        AssociationInfo associationInfo = createAssociationInfo(1, "name1");
        when(mMockConnectedAssociationStore.getConnectedAssociations())
            .thenReturn(Arrays.asList(associationInfo));

        // Define a new task.
        String taskLabel = "newTask";
        int taskId = 123;
        ActivityManager.RunningTaskInfo taskInfo = createRunningTaskInfo(taskId, taskLabel, 0);

        // Mock ActivityTaskManager to return the new task.
        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
                .thenReturn(List.of(taskInfo));

        // Notify TaskBroadcaster of the new task.
        mTaskBroadcaster.onTaskCreated(taskId, null);

        // Verify sendMessage is called
        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
                eq(CompanionDeviceManager.MESSAGE_TASK_CONTINUITY),
                messageCaptor.capture(),
                any(int[].class));
        byte[] capturedMessage = messageCaptor.getValue();
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(capturedMessage);
        assertThat(taskContinuityMessage.getData()).isInstanceOf(RemoteTaskAddedMessage.class);
        RemoteTaskAddedMessage remoteTaskAddedMessage =
                (RemoteTaskAddedMessage) taskContinuityMessage.getData();
        assertThat(remoteTaskAddedMessage.getTask().getId()).isEqualTo(taskId);
    }
}