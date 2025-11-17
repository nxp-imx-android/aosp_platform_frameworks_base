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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.HandoffActivityParams;
import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RunningTaskFetcherTest {

    private static final int USER_ID = 0;

    @Mock private ActivityTaskManager mockActivityTaskManager;
    @Mock private ActivityTaskManagerInternal mockActivityTaskManagerInternal;
    @Mock private AppOpsManager mockAppOps;

    private RunningTaskFetcher runningTaskFetcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        runningTaskFetcher =
                new RunningTaskFetcher(
                        USER_ID,
                        mockActivityTaskManager,
                        mockActivityTaskManagerInternal,
                        mockAppOps);
    }

    @Test
    public void testGetRunningTasks_returnsRunningTasks() {
        FakeTask[] tasks = {
            new FakeTask(1, USER_ID, "com.example.app1", 100, new HandoffOptions(true, true), true),
            new FakeTask(
                    2, USER_ID, "com.example.app2", 200, new HandoffOptions(false, true), true),
            new FakeTask(4, 1000, "com.example.app4", 400, new HandoffOptions(true, true), false),
        };
        setupRunningTasks(tasks);
        assertThat(runningTaskFetcher.getRunningTasks())
                .containsExactly(tasks[0].toRemoteTaskInfo());
    }

    @Test
    public void testGetRunningTasks_returnsRunningTask() {
        FakeTask[] tasks = {
            new FakeTask(1, USER_ID, "com.example.app1", 100, new HandoffOptions(true, true), true),
            new FakeTask(
                    2, USER_ID, "com.example.app2", 200, new HandoffOptions(false, true), true),
        };
        setupRunningTasks(tasks);
        assertThat(runningTaskFetcher.getRunningTasks())
                .containsExactly(tasks[0].toRemoteTaskInfo());
    }

    @Test
    public void testGetRunningTasks_doesNotReturnTaskWithHandoffDisabled() {
        FakeTask[] tasks = {
            new FakeTask(
                    1, USER_ID, "com.example.app1", 100, new HandoffOptions(false, true), true),
        };
        setupRunningTasks(tasks);
        assertThat(runningTaskFetcher.getRunningTasks()).isEmpty();
    }

    @Test
    public void testGetRunningTasks_packageNotAllowedToContinueAcrossDevices_returnsNull() {
        FakeTask[] tasks = {
            new FakeTask(
                    1, USER_ID, "com.example.app1", 200, new HandoffOptions(true, true), false),
        };
        setupRunningTasks(tasks);
        assertThat(runningTaskFetcher.getRunningTasks()).isEmpty();
    }

    private record FakeTask(
            int taskId,
            int userId,
            String packageName,
            long lastActiveTime,
            HandoffOptions handoffOptions,
            boolean isOpContinueAcrossDevicesAllowed) {

        public RemoteTaskInfo toRemoteTaskInfo() {
            return new RemoteTaskInfo(taskId, packageName, true, lastActiveTime, handoffOptions);
        }
    }

    private void setupRunningTasks(FakeTask... tasks) {
        List<RunningTaskInfo> runningTaskInfos = new ArrayList<>();
        for (FakeTask task : tasks) {
            runningTaskInfos.add(setupTask(task));
        }

        doReturn(runningTaskInfos).when(mockActivityTaskManager).getTasks(Integer.MAX_VALUE, true);
    }

    private RunningTaskInfo setupTask(FakeTask task) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = task.taskId;
        taskInfo.userId = task.userId;
        taskInfo.baseActivity = new ComponentName(task.packageName, "com.example.app.MainActivity");
        taskInfo.lastActiveTime = task.lastActiveTime;
        taskInfo.isVisible = true;
        when(mockActivityTaskManagerInternal.isHandoffEnabledForTask(task.taskId))
                .thenReturn(task.handoffOptions.isHandoffEnabled());
        when(mockActivityTaskManagerInternal.getHandoffActivityParamsForTask(task.taskId))
                .thenReturn(
                        new HandoffActivityParams.Builder()
                                .setAllowHandoffWithoutPackageInstalled(true)
                                .build());
        when(mockAppOps.noteOpNoThrow(
                        AppOpsManager.OP_CONTINUE_ACROSS_DEVICES,
                        taskInfo.userId,
                        task.packageName))
                .thenReturn(
                        task.isOpContinueAcrossDevicesAllowed
                                ? AppOpsManager.MODE_ALLOWED
                                : AppOpsManager.MODE_ERRORED);

        return taskInfo;
    }
}
