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
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            associationId,
            deviceName);

        assertThat(taskList.getMostRecentTask()).isNull();
        assertThat(taskList.getAssociationId()).isEqualTo(associationId);
        assertThat(taskList.getDeviceName()).isEqualTo(deviceName);
    }

    @Test
    public void testAddTask_updatesMostRecentTask() {
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            0,
            "device name");

        RemoteTaskInfo firstAddedTask = createNewRemoteTaskInfo(2, "task2", 200);

        taskList.addTask(firstAddedTask);

        assertThat(taskList.getMostRecentTask()).isEqualTo(firstAddedTask);

        // Add another task with an older timestamp, verify it doesn't update
        // the most recent task.
        RemoteTaskInfo secondAddedTask = createNewRemoteTaskInfo(1, "task1", 100);
        taskList.addTask(secondAddedTask);
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(firstAddedTask.getId());

        // Add another task with a newer timestamp, verifying it changes the
        // most recently used task.
        RemoteTaskInfo thirdAddedTask = createNewRemoteTaskInfo(3, "task3", 300);
        taskList.addTask(thirdAddedTask);
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(thirdAddedTask.getId());
    }

    @Test
    public void testGetMostRecentTask_noTasks_returnsNull() {
        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            0,
            "device name");

        assertThat(taskList.getMostRecentTask()).isNull();
    }

    @Test
    public void testGetMostRecentTask_multipleTasks_returnsMostRecent() {
        RemoteTaskInfo expectedTask = createNewRemoteTaskInfo(2, "task2", 200);
        int associationId = 123;
        List<RemoteTaskInfo> initialTasks = Arrays.asList(
                createNewRemoteTaskInfo(1, "task1", 100),
                expectedTask,
                createNewRemoteTaskInfo(3, "task3", 150));

        RemoteDeviceTaskList taskList = new RemoteDeviceTaskList(
            associationId,
            "device name");

        taskList.setTasks(initialTasks);

        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(expectedTask.getId());
    }

    @Test
    public void testSetTasks_updatesMostRecentTask() {
        RemoteDeviceTaskList taskList
            = new RemoteDeviceTaskList(0, "device name");

        // Set tasks initially, verify the most recent task is the first one.
        RemoteTaskInfo firstExpectedTask
            = createNewRemoteTaskInfo(1, "task2", 200);
        int associationId = 123;
        List<RemoteTaskInfo> initialTasks = Arrays.asList(
                createNewRemoteTaskInfo(2, "task1", 100),
                firstExpectedTask,
                createNewRemoteTaskInfo(3, "task3", 150));
        taskList.setTasks(initialTasks);
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(firstExpectedTask.getId());

        // Set the tasks to a different list, verify the most recent task is the
        // first one.
        RemoteTaskInfo secondExpectedTask
            = createNewRemoteTaskInfo(4, "task4", 300);
        List<RemoteTaskInfo> secondExpectedTasks = Arrays.asList(
                secondExpectedTask,
                createNewRemoteTaskInfo(5, "task5", 200),
                createNewRemoteTaskInfo(6, "task6", 100));
        taskList.setTasks(secondExpectedTasks);
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(secondExpectedTask.getId());
    }

    @Test
    public void testSetTasks_overwritesExistingTasks() {
        RemoteDeviceTaskList taskList
            = new RemoteDeviceTaskList(0, "device name");

        // Set the initial state of the list.
        RemoteTaskInfo firstExpectedTask = createNewRemoteTaskInfo(1, "task1", 100);
        taskList.setTasks(Arrays.asList(firstExpectedTask));
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(firstExpectedTask.getId());

        // Replace the tasks with a different list. The only task in this was used before the
        // previous task.
        RemoteTaskInfo secondExpectedTask = createNewRemoteTaskInfo(2, "task2", 10);
        taskList.setTasks(Arrays.asList(secondExpectedTask));

        // Because the task list is overwritten, the most recent task should be the second task.
        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(secondExpectedTask.getId());
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(
        int id,
        String label,
        long lastUsedTimeMillis) {

        ActivityManager.RunningTaskInfo runningTaskInfo = createRunningTaskInfo(
            id,
            label,
            lastUsedTimeMillis);

        return new RemoteTaskInfo(runningTaskInfo);
    }
}