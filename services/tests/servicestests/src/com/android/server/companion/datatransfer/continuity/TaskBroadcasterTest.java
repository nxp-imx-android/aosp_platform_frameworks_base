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
import static org.mockito.ArgumentMatchers.eq;

import static com.android.server.companion.datatransfer.contextsync.BitmapUtils.renderDrawableToByteArray;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;

import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;

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

    @Mock private ActivityTaskManager mMockActivityTaskManager;
    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    @Mock private PackageManager mMockPackageManager;

    private TaskBroadcaster mTaskBroadcaster;

    private Drawable mTaskIcon;
    private byte[] mSerializedTaskIcon;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext = Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));

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
            mMockTaskContinuityMessenger);
    }

    @Test
    public void testOnAllDevicesDisconnected_doesNothingIfNoDeviceConnected() {
        mTaskBroadcaster.onAllDevicesDisconnected();
        verify(mMockActivityTaskManager, never()).registerTaskStackListener(mTaskBroadcaster);
    }

    @Test
    public void testOnAllDevicesDisconnected_unregistersListener() {
        // Connect a device, verify the listener is registered.
        mTaskBroadcaster.onDeviceConnected(1);
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mTaskBroadcaster);

        // Disconnect all devices, verify the listener is unregistered.
        mTaskBroadcaster.onAllDevicesDisconnected();
        verify(mMockActivityTaskManager, times(1)).unregisterTaskStackListener(mTaskBroadcaster);
    }

    @Test
    public void testOnDeviceConnected_sendsMessageToDevice()
        throws RemoteException, NameNotFoundException {

        // Setup a fake foreground task.
        int taskId = 100;
        String taskLabel = "test";
        long taskLastActiveTime = 100;
        RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);

        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
            .thenReturn(Arrays.asList(taskInfo));

        // Add a new transport
        int associationId = 1;
        mTaskBroadcaster.onDeviceConnected(associationId);

        // Verify the message is sent.
        ContinuityDeviceConnected expectedMessage = new ContinuityDeviceConnected(
            Arrays.asList(new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon)));
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(
            eq(associationId),
            eq(expectedMessage));

        // Verify a listener was registered.
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mTaskBroadcaster);
    }

    @Test
    public void testOnTaskCreated_sendsMessageToAllAssociations()
        throws NameNotFoundException, RemoteException {

        // Define a new task.
        String taskLabel = "newTask";
        int taskId = 123;
        long taskLastActiveTime = 0;
        RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);

        // Mock ActivityTaskManager to return the new task.
        when(mMockActivityTaskManager.getTasks(Integer.MAX_VALUE, true))
                .thenReturn(List.of(taskInfo));

        // Notify TaskBroadcaster of the new task.
        mTaskBroadcaster.onTaskCreated(taskId, null);

        // Verify sendMessage is called
        RemoteTaskAddedMessage expectedMessage = new RemoteTaskAddedMessage(
            new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon));
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnTaskRemoved_sendsMessageToAllAssociations() throws RemoteException {
        int taskId = 123;

        mTaskBroadcaster.onTaskRemoved(taskId);

        // Verify sendMessage is called
        RemoteTaskRemovedMessage expectedMessage = new RemoteTaskRemovedMessage(taskId);
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnTaskMovedToFront_sendsMessageToAllAssociations()
        throws NameNotFoundException, RemoteException {

        // Simulate a task being moved to front.
        int taskId = 1;
        String taskLabel = "newTask";
        long taskLastActiveTime = 0;
        RunningTaskInfo taskInfo = setupTask(taskId, taskLabel, taskLastActiveTime);
        mTaskBroadcaster.onTaskMovedToFront(taskInfo);

        // Verify sendMessage is called for each association.
        RemoteTaskUpdatedMessage expectedMessage = new RemoteTaskUpdatedMessage(
            new RemoteTaskInfo(
                taskId,
                taskLabel,
                taskLastActiveTime,
                mSerializedTaskIcon));
       verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    private RunningTaskInfo setupTask(
        int taskId,
        String label,
        long lastActiveTime) throws NameNotFoundException {

        String packageName = "com.example.app";

        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.baseActivity = new ComponentName(packageName, "className");
        taskInfo.lastActiveTime = lastActiveTime;

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