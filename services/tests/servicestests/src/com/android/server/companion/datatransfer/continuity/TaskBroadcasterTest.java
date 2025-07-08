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
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

import static com.android.server.companion.datatransfer.contextsync.BitmapUtils.renderDrawableToByteArray;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createMockContext;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createMockCompanionDeviceManager;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createAssociationInfo;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createRunningTaskInfo;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.companion.AssociationInfo;
import android.companion.IOnTransportsChangedListener;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.RemoteTask;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageData;

import com.android.frameworks.servicestests.R;


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

    private ICompanionDeviceManager mMockCompanionDeviceManagerService;

    @Mock private ConnectedAssociationStore mMockConnectedAssociationStore;

    @Mock private PackageManager mMockPackageManager;

    private TaskBroadcaster mTaskBroadcaster;

    private Drawable mTaskIcon;
    private byte[] mSerializedTaskIcon;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext =  createMockContext();
        mMockCompanionDeviceManagerService = createMockCompanionDeviceManager(mMockContext);

        when(mMockContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
            .thenReturn(mMockActivityTaskManager);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        Bitmap bitmap = BitmapFactory.decodeResource(
                mMockContext.getResources(), R.drawable.black_32x32);
        mTaskIcon = new BitmapDrawable(mMockContext.getResources(), bitmap);
        mSerializedTaskIcon = renderDrawableToByteArray(mTaskIcon);

        // Create TaskBroadcaster.
        mTaskBroadcaster = new TaskBroadcaster(
            mMockContext,
            mMockConnectedAssociationStore);
    }

    @Test
    public void testStopBroadcasting_doesNothingIfNotBroadcasting()
        throws Exception {

        mTaskBroadcaster.stopBroadcasting();
        verify(mMockConnectedAssociationStore, never()).addObserver(mTaskBroadcaster);
    }

    @Test
    public void testStartAndStopBroadcasting_updatesTransportsListener()
        throws Exception {

        // Start broadcasting, verifying an association listener is added.
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1)).addObserver(mTaskBroadcaster);
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mTaskBroadcaster);

        // Stop broadcasting, verifying the association listener is removed.
        mTaskBroadcaster.stopBroadcasting();
        verify(mMockConnectedAssociationStore, times(1)).removeObserver(mTaskBroadcaster);
        verify(mMockActivityTaskManager, times(1)).unregisterTaskStackListener(mTaskBroadcaster);
    }

    @Test
    public void testStartBroadcasting_startsBroadcasting() throws Exception {
        // Start broadcasting, verifying a transport listener is added.
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1)).addObserver(mTaskBroadcaster);

        // Setup a fake foreground task.
        int taskId = 100;
        String taskLabel = "test";
        long taskLastActiveTime = 100;
        ActivityManager.RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);

        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
            .thenReturn(Arrays.asList(taskInfo));

        // Add a new transport
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        mTaskBroadcaster.onTransportConnected(associationInfo);

        // Verify the message is sent.
        ArgumentCaptor<byte[]> messageCaptor
            = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
            messageCaptor.capture(),
            eq(new int[] {1}));
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(
            messageCaptor.getValue());
        assertThat(taskContinuityMessage.getData()).isInstanceOf(
            ContinuityDeviceConnected.class);
        ContinuityDeviceConnected expectedMessage = new ContinuityDeviceConnected(
            Arrays.asList(new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon)));
        ContinuityDeviceConnected actualMessage
            = (ContinuityDeviceConnected) taskContinuityMessage.getData();
        assertThat(actualMessage).isEqualTo(expectedMessage);
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
        long taskLastActiveTime = 0;
        ActivityManager.RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);

        // Mock ActivityTaskManager to return the new task.
        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
                .thenReturn(List.of(taskInfo));

        // Notify TaskBroadcaster of the new task.
        mTaskBroadcaster.onTaskCreated(taskId, null);

        // Verify sendMessage is called
        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
                eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
                messageCaptor.capture(),
                any(int[].class));
        byte[] capturedMessage = messageCaptor.getValue();
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(capturedMessage);
        assertThat(taskContinuityMessage.getData()).isInstanceOf(RemoteTaskAddedMessage.class);
        RemoteTaskAddedMessage expectedMessage = new RemoteTaskAddedMessage(
            new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon));
        RemoteTaskAddedMessage actualMessage =
                (RemoteTaskAddedMessage) taskContinuityMessage.getData();
        assertThat(actualMessage).isEqualTo(expectedMessage);
    }

        @Test
    public void testOnTaskRemoved_sendsMessageToAllAssociations() throws Exception {
        // Start broadcasting.
        int taskId = 123;
        mTaskBroadcaster.startBroadcasting();
        verify(mMockConnectedAssociationStore, times(1)).addObserver(mTaskBroadcaster);
        AssociationInfo associationInfo = createAssociationInfo(1, "name1");
        when(mMockConnectedAssociationStore.getConnectedAssociations())
            .thenReturn(Arrays.asList(associationInfo));

        mTaskBroadcaster.onTaskRemoved(taskId);

        // Verify sendMessage is called
        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
                eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
                messageCaptor.capture(),
                any(int[].class));
        byte[] capturedMessage = messageCaptor.getValue();
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(capturedMessage);
        assertThat(taskContinuityMessage.getData()).isInstanceOf(RemoteTaskRemovedMessage.class);
        RemoteTaskRemovedMessage remoteTaskRemovedMessage =
                (RemoteTaskRemovedMessage) taskContinuityMessage.getData();
        assertThat(remoteTaskRemovedMessage.taskId()).isEqualTo(taskId);
    }

    @Test
    public void testOnTaskMovedToFront_sendsMessageToAllAssociations() throws Exception {
        // Setup
        int associationId = 1;
        String associationName = "name1";
        when(mMockConnectedAssociationStore.getConnectedAssociations())
            .thenReturn(List.of(createAssociationInfo(associationId, associationName)));
        mTaskBroadcaster.startBroadcasting();

        // Simulate a task being moved to front.
        int taskId = 1;
        String taskLabel = "newTask";
        long taskLastActiveTime = 0;
        ActivityManager.RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);
        mTaskBroadcaster.onTaskMovedToFront(taskInfo);

        // Verify sendMessage is called for each association.
        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
            messageCaptor.capture(),
            eq(new int[] {1}));
        TaskContinuityMessage sentMessage = new TaskContinuityMessage(messageCaptor.getValue());
        assertThat(sentMessage.getData()).isInstanceOf(RemoteTaskUpdatedMessage.class);
        RemoteTaskUpdatedMessage expectedMessage = new RemoteTaskUpdatedMessage(
            new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon));
        RemoteTaskUpdatedMessage actualMessage = (RemoteTaskUpdatedMessage) sentMessage.getData();
        assertThat(actualMessage).isEqualTo(expectedMessage);
    }

    private ActivityManager.RunningTaskInfo setupTask(
        int taskId,
        String label,
        long lastActiveTime) throws Exception {

        String packageName = "com.example.app";
        ActivityManager.RunningTaskInfo taskInfo = createRunningTaskInfo(
            taskId,
            packageName,
            lastActiveTime);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.name = packageName;
        when(mMockPackageManager.getPackageInfo(eq(packageName), eq(PackageManager.GET_META_DATA)))
            .thenReturn(packageInfo);
        when(mMockPackageManager.getApplicationLabel(any(ApplicationInfo.class)))
            .thenReturn(label);

        when(mMockPackageManager.getApplicationIcon(any(ApplicationInfo.class)))
            .thenReturn(mTaskIcon);

        return taskInfo;
    }
}