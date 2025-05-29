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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskStoreTest {

    private RemoteTaskStore taskStore;

    @Before
    public void setUp() {
        taskStore = new RemoteTaskStore();
    }

    @Test
    public void testRegisterDevice_addsDeviceToStore() {
        RemoteTaskInfo expectedTask = createNewRemoteTaskInfo("task1", 100);

        taskStore.registerDevice(0, "device name", Arrays.asList(expectedTask));

        assertThat(taskStore.getMostRecentTasks()).containsExactly(expectedTask);
    }

    @Test
    public void testGetMostRecentTasks_emptyStore_returnsEmptyList() {
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    @Test
    public void testGetMostRecentTasks_multipleDevices_returnsMostRecentFromEach() {
        RemoteTaskInfo expectedTaskFromDevice1 = createNewRemoteTaskInfo("task1", 100);
        RemoteTaskInfo expectedTaskFromDevice2 = createNewRemoteTaskInfo("task2", 200);

        taskStore.registerDevice(
            0,
            "device1",
            Arrays.asList(expectedTaskFromDevice1, createNewRemoteTaskInfo("task2", 1)));

        taskStore.registerDevice(
            1,
            "device2",
            Arrays.asList(expectedTaskFromDevice2, createNewRemoteTaskInfo("task2", 1)));

        assertThat(taskStore.getMostRecentTasks())
            .containsExactly(expectedTaskFromDevice1, expectedTaskFromDevice2);
    }

    @Test
    public void testGetMostRecentTasks_deviceWithNoTasks_returnsEmptyList() {
        taskStore.registerDevice(0, "device name", new ArrayList<>());

        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(String label, long lastUsedTimeMillis) {
        ActivityManager.RunningTaskInfo runningTaskInfo
            = createRunningTaskInfo(1, label, lastUsedTimeMillis);

        return new RemoteTaskInfo(runningTaskInfo);
    }
}