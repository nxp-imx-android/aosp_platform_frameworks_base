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
import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteDeviceTaskListTest {

    private RemoteTask mMostRecentTask;
    private int mObserverCallCount;

    private static final int ASSOCIATION_ID = 123;
    private static final String DEVICE_NAME = "device1";

    private RemoteDeviceTaskList taskList;

    @Before
    public void setUp() {
        mMostRecentTask = null;
        mObserverCallCount = 0;

        taskList = new RemoteDeviceTaskList(
            ASSOCIATION_ID,
            DEVICE_NAME,
            this::onMostRecentTaskChanged);
    }

    @Test
    public void testConstructor_initializesCorrectly() {
        assertThat(taskList.getMostRecentTask()).isNull();
        assertThat(taskList.getAssociationId()).isEqualTo(ASSOCIATION_ID);
        assertThat(taskList.getDeviceName()).isEqualTo(DEVICE_NAME);
    }

    @Test
    public void testAddTask_updatesMostRecentTaskAndNotifiesListeners() {
        // Before adding any tasks, the most recent task should be null.
        assertThat(taskList.getMostRecentTask()).isNull();

        // Add a task, verify it automatically becomes the most recent task.
        RemoteTaskInfo firstAddedTaskInfo
            = createNewRemoteTaskInfo(2, "task2", 200);
        RemoteTask firstAddedTask
            = firstAddedTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.addTask(firstAddedTaskInfo);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(firstAddedTask);
        assertThat(taskList.getMostRecentTask()).isEqualTo(firstAddedTask);

        // Add another task with an older timestamp, verify it doesn't update
        // the most recent task.
        RemoteTaskInfo secondAddedTaskInfo = createNewRemoteTaskInfo(1, "task1", 100);
        taskList.addTask(secondAddedTaskInfo);
        assertThat(taskList.getMostRecentTask()).isEqualTo(firstAddedTask);
        assertThat(mObserverCallCount).isEqualTo(2);
        assertThat(mMostRecentTask).isEqualTo(firstAddedTask);

        // Add another task with a newer timestamp, verifying it changes the
        // most recently used task.
        RemoteTaskInfo thirdAddedTaskInfo = createNewRemoteTaskInfo(3, "task3", 300);
        RemoteTask thirdAddedTask = thirdAddedTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.addTask(thirdAddedTaskInfo);

        assertThat(taskList.getMostRecentTask()).isEqualTo(thirdAddedTask);
        assertThat(mObserverCallCount).isEqualTo(3);
        assertThat(mMostRecentTask).isEqualTo(thirdAddedTask);
    }

    @Test
    public void testGetMostRecentTask_noTasks_returnsNull() {
        assertThat(taskList.getMostRecentTask()).isNull();
    }

    @Test
    public void testGetMostRecentTask_multipleTasks_returnsMostRecent() {
        RemoteTaskInfo expectedTask = createNewRemoteTaskInfo(2, "task2", 200);
        List<RemoteTaskInfo> initialTasks = Arrays.asList(
                createNewRemoteTaskInfo(1, "task1", 100),
                expectedTask,
                createNewRemoteTaskInfo(3, "task3", 150));

        taskList.setTasks(initialTasks);

        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(expectedTask.getId());
    }

    @Test
    public void testRemoveTask_removesTaskAndNotifiesListeners() {
        RemoteTaskInfo mostRecentTaskInfo = createNewRemoteTaskInfo(1, "task2", 200);
        RemoteTask mostRecentTask = mostRecentTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        RemoteTaskInfo secondMostRecentTaskInfo = createNewRemoteTaskInfo(2, "task1", 100);
        RemoteTask secondMostRecentTask
            = secondMostRecentTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);

        taskList.setTasks(Arrays.asList(mostRecentTaskInfo, secondMostRecentTaskInfo));
        assertThat(taskList.getMostRecentTask()).isEqualTo(mostRecentTask);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(mostRecentTask);

        taskList.removeTask(mostRecentTask.getId());
        assertThat(taskList.getMostRecentTask()).isEqualTo(secondMostRecentTask);
        assertThat(mObserverCallCount).isEqualTo(2);
        assertThat(mMostRecentTask).isEqualTo(secondMostRecentTask);
    }

    @Test
    public void testSetTasks_updatesMostRecentTaskAndNotifiesListeners() {

        // Set tasks initially, verify the most recent task is the first one.
        RemoteTaskInfo firstExpectedTaskInfo = createNewRemoteTaskInfo(1, "task2", 200);
        RemoteTask firstExpectedTask
            = firstExpectedTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);

        List<RemoteTaskInfo> initialTasks = Arrays.asList(
                createNewRemoteTaskInfo(2, "task1", 100),
                firstExpectedTaskInfo,
                createNewRemoteTaskInfo(3, "task3", 150));

        taskList.setTasks(initialTasks);

        assertThat(taskList.getMostRecentTask().getId()).isEqualTo(firstExpectedTask.getId());
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask.getId()).isEqualTo(firstExpectedTask.getId());

        // Set the tasks to a different list, verify the most recent task is the
        // first one.
        RemoteTaskInfo secondExpectedTaskInfo = createNewRemoteTaskInfo(4, "task4", 300);
        List<RemoteTaskInfo> secondExpectedTasks = Arrays.asList(
                secondExpectedTaskInfo,
                createNewRemoteTaskInfo(7, "task7", 200),
                createNewRemoteTaskInfo(5, "task5", 200),
                createNewRemoteTaskInfo(6, "task6", 100));
        RemoteTask secondExpectedTask =
            secondExpectedTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);

        taskList.setTasks(secondExpectedTasks);

        assertThat(mObserverCallCount).isEqualTo(2);
        assertThat(mMostRecentTask).isEqualTo(secondExpectedTask);
        assertThat(taskList.getMostRecentTask()).isEqualTo(secondExpectedTask);
    }

    @Test
    public void testSetTasks_overwritesExistingTasksAndNotifiesListeners() {
        // Set the initial state of the list.
        RemoteTaskInfo firstExpectedTask = createNewRemoteTaskInfo(1, "task1", 100);
        RemoteTask firstExpectedRemoteTask =
            firstExpectedTask.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.setTasks(Arrays.asList(firstExpectedTask));
        assertThat(taskList.getMostRecentTask()).isEqualTo(firstExpectedRemoteTask);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(firstExpectedRemoteTask);

        // Replace the tasks with a different list. The only task in this was used before the
        // previous task.
        RemoteTaskInfo secondExpectedTask = createNewRemoteTaskInfo(2, "task2", 200);
        RemoteTask secondExpectedRemoteTask
            = secondExpectedTask.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.setTasks(Arrays.asList(secondExpectedTask));

        // Because the task list is overwritten, the most recent task should be the second task.
        assertThat(taskList.getMostRecentTask()).isEqualTo(secondExpectedRemoteTask);
        assertThat(mObserverCallCount).isEqualTo(2);
        assertThat(mMostRecentTask).isEqualTo(secondExpectedRemoteTask);
    }

    @Test
    public void updateTask_updatesMostRecentTaskAndNotifiesListeners() {
        // Set the initial state of the list.
        RemoteTaskInfo initialTaskInfo = createNewRemoteTaskInfo(1, "task1", 100);
        RemoteTask initialTask =
            initialTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.setTasks(Arrays.asList(initialTaskInfo));
        assertThat(taskList.getMostRecentTask()).isEqualTo(initialTask);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(initialTask);

        RemoteTaskInfo updatedTaskInfo = createNewRemoteTaskInfo(
            initialTaskInfo.getId(),
            "task1",
            200);
        RemoteTask updatedTask = updatedTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.updateTask(updatedTaskInfo);

        assertThat(taskList.getMostRecentTask()).isEqualTo(updatedTask);
        assertThat(mObserverCallCount).isEqualTo(2);
        assertThat(mMostRecentTask).isEqualTo(updatedTask);
    }

    @Test
    public void testUpdateTask_doesNotUpdateMostRecentTask_doesNotNotifyListeners() {
        // Set the initial state of the list.
        RemoteTaskInfo initialTaskInfo = createNewRemoteTaskInfo(1, "task1", 100);
        RemoteTask initialTask =
            initialTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        RemoteTaskInfo topTaskInfo = createNewRemoteTaskInfo(2, "task2", 200);
        RemoteTask topTask = topTaskInfo.toRemoteTask(ASSOCIATION_ID, DEVICE_NAME);
        taskList.setTasks(Arrays.asList(initialTaskInfo, topTaskInfo));
        assertThat(taskList.getMostRecentTask()).isEqualTo(topTask);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(topTask);

        RemoteTaskInfo updatedTaskInfo = createNewRemoteTaskInfo(
            initialTaskInfo.getId(),
            "task1",
            150);
        taskList.updateTask(updatedTaskInfo);
        assertThat(taskList.getMostRecentTask()).isEqualTo(topTask);
        assertThat(mObserverCallCount).isEqualTo(1);
        assertThat(mMostRecentTask).isEqualTo(topTask);
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

    public void onMostRecentTaskChanged(RemoteTask task) {
        mMostRecentTask = task;
        mObserverCallCount++;
    }
}