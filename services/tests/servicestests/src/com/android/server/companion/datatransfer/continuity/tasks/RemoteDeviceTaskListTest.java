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
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createRunningTaskInfo;

import android.app.ActivityManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteDeviceTaskListTest {

    @Test
    public void testConstructor_initializesCorrectly() {
        int associationId = 123;
        String deviceName = "device1";
        RemoteTaskInfo expectedTask = createNewRemoteTaskInfo("task1", 100);
        List<RemoteTaskInfo> initialTasks = Arrays.asList(expectedTask);
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            associationId,
            deviceName,
            initialTasks);

        assertThat(taskList.getMostRecentTask()).isEqualTo(expectedTask);
        assertThat(taskList.getAssociationId()).isEqualTo(associationId);
        assertThat(taskList.getDeviceName()).isEqualTo(deviceName);
    }

    @Test
    public void testAddTask_updatesMostRecentTask() {
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            0,
            "device name",
            new ArrayList<>());

        RemoteTaskInfo firstAddedTask = createNewRemoteTaskInfo("task2", 200);

        taskList.addTask(firstAddedTask);

        assertThat(taskList.getMostRecentTask()).isEqualTo(firstAddedTask);

        // Add another task with an older timestamp, verify it doesn't update
        // the most recent task.
        RemoteTaskInfo secondAddedTask = createNewRemoteTaskInfo("task1", 100);
        taskList.addTask(secondAddedTask);
        assertThat(taskList.getMostRecentTask()).isEqualTo(firstAddedTask);

        // Add another task with a newer timestamp, verifying it changes the
        // most recently used task.
        RemoteTaskInfo thirdAddedTask = createNewRemoteTaskInfo("task3", 300);
        taskList.addTask(thirdAddedTask);
        assertThat(taskList.getMostRecentTask()).isEqualTo(thirdAddedTask);
    }

    @Test
    public void testGetMostRecentTask_emptyList_returnsNull() {
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            0,
            "device name",
            new ArrayList<>());

        assertThat(taskList.getMostRecentTask()).isNull();
    }

    @Test
    public void testGetMostRecentTask_multipleTasks_returnsMostRecent() {
        RemoteTaskInfo expectedTask = createNewRemoteTaskInfo("task2", 200);
        int associationId = 123;
        List<RemoteTaskInfo> initialTasks = Arrays.asList(
                createNewRemoteTaskInfo("task1", 100),
                expectedTask,
                createNewRemoteTaskInfo("task3", 150));

        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            associationId,
            "device name",
            initialTasks);

        assertThat(taskList.getMostRecentTask()).isEqualTo(expectedTask);
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(
        String label,
        long lastUsedTimeMillis) {

        ActivityManager.RunningTaskInfo runningTaskInfo = createRunningTaskInfo(
            1,
            label,
            lastUsedTimeMillis);

        return new RemoteTaskInfo(runningTaskInfo);
    }
}