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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskBroadcasterTest {

    @Mock private RunningTaskFetcher mMockRunningTaskFetcher;
    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;

    private static List<RemoteTaskInfo> REMOTE_TASKS =
            List.of(
                    new RemoteTaskInfo(
                            100 /* taskId */,
                            "package_name" /* packageName */,
                            true /* isResumed */,
                            100 /* lastActiveTime */,
                            new HandoffOptions(true, true)));

    public static TaskContinuityMessage TASK_CONTINUITY_MESSAGE =
            new TaskContinuityMessage.Builder()
                    .setTaskStackBroadcastMessage(
                            new TaskStackBroadcastMessage.Builder()
                                    .setRemoteTasks(REMOTE_TASKS)
                                    .build())
                    .build();

    private TaskBroadcaster mTaskBroadcaster;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockRunningTaskFetcher.getRunningTasks()).thenReturn(REMOTE_TASKS);
        mTaskBroadcaster =
                new TaskBroadcaster(mMockTaskContinuityMessenger, mMockRunningTaskFetcher);
    }

    @Test
    public void testOnDeviceConnected_sendsMessageToDevice() throws RemoteException {
        int associationId = 100;
        mTaskBroadcaster.onDeviceConnected(associationId);
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(TASK_CONTINUITY_MESSAGE));
    }

    @Test
    public void testOnTaskStackChanged_sendsMessageToDevice() throws RemoteException {
        mTaskBroadcaster.onTaskStackChanged();
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(TASK_CONTINUITY_MESSAGE));
    }

    @Test
    public void testOnHandoffEnabledChanged_sendTaskStackBroadcastMessage() throws RemoteException {
        mTaskBroadcaster.onHandoffEnabledChanged(100, true);
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(TASK_CONTINUITY_MESSAGE));
    }
}
