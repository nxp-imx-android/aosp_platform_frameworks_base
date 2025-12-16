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

package com.android.server.wm;

import static com.android.window.flags.Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.os.PersistableBundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;

import com.android.server.wm.utils.StubOrganizer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link PackageUpdateManager}.
 *
 * Build/run with:
 * atest FrameworksServicesTests:com.android.server.wm.PackageUpdateManagerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class PackageUpdateManagerTests extends WindowTestsBase {

    private static final String PKG_1 = "com.test.app1";
    private static final String PKG_2 = "com.test.app2";

    private PackageUpdateManager mManager;
    private PersistableBundle mBundle1;
    private PersistableBundle mBundle2;

    @Before
    public void setUp() {
        // Create the class under test
        mManager = new PackageUpdateManager(mAtm);

        // Set up test bundles
        mBundle1 = new PersistableBundle();
        mBundle1.putString("key", "value1");

        mBundle2 = new PersistableBundle();
        mBundle2.putString("key", "value2");
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGetPersistentTask_success() {
        final Task task = setUpTask(PKG_1);

        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(task);
        assertNotNull(result);
        assertEquals(mBundle1, result);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testGetPersistentTask_stateIsRemovedAfterGet() {
        final Task task = setUpTask(PKG_1);

        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        // First get should succeed
        PersistableBundle result1 = mManager.getPersistentStateForTask(task);
        assertNotNull(result1);

        // Second get for the *same task* should fail, as it's been removed
        PersistableBundle result2 = mManager.getPersistentStateForTask(task);
        assertNull(result2);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testGetPersistentTask_packageNotFound() {
        final Task task = setUpTask(PKG_1);
        final Task taskNotAdded = setUpTask(PKG_2);
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(taskNotAdded);

        assertNull(result);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGet_MultipleTasksSamePackage() {
        final Task task1 = setUpTask(PKG_1);
        final Task task2 = setUpTask(PKG_1);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);
        mManager.addPersistentTaskForPackage(PKG_1, task2.mTaskId, mBundle2);

        // Get state for the first task
        PersistableBundle result1 = mManager.getPersistentStateForTask(task1);
        assertNotNull(result1);
        assertEquals(mBundle1, result1);

        // Get state for the second task
        PersistableBundle result2 = mManager.getPersistentStateForTask(task2);
        assertNotNull(result2);
        assertEquals(mBundle2, result2);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGet_MultiplePackages() {
        final Task task1 = setUpTask(PKG_1);
        final Task task2 = setUpTask(PKG_2);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);
        mManager.addPersistentTaskForPackage(PKG_2, task2.mTaskId, mBundle2);

        PersistableBundle result1 = mManager.getPersistentStateForTask(task1);
        PersistableBundle result2 = mManager.getPersistentStateForTask(task2);

        assertNotNull(result1);
        assertEquals(mBundle1, result1);
        assertNotNull(result2);
        assertEquals(mBundle2, result2);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testMethods_WhenFlagIsDisabled() {
        final Task task1 = setUpTask(PKG_1);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(task1);

        assertNull(result);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnPackageUpdateFinished_notifiesController() {
        PackageUpdateOrganizer o = new PackageUpdateOrganizer();
        mWm.mAtmService.mTaskOrganizerController.registerTaskOrganizer(o);
        final Task task1 = setUpTask(PKG_1);
        final Task task2 = setUpTask(PKG_1);
        ArraySet<Task> tasks = new ArraySet<>();
        tasks.add(task1);
        tasks.add(task2);
        mManager.addUpdatingTasksForPackage(PKG_1, tasks);

        mManager.onPackageUpdateFinished(PKG_1, 123);

        assertThat(o.mUpdatedTaskIds).containsExactly(task1.mTaskId, task2.mTaskId);
    }

    static class PackageUpdateOrganizer extends StubOrganizer {
        List<Integer> mUpdatedTaskIds = new ArrayList<>();

        @Override
        public void onPackageUpdateFinished(
                List<ActivityManager.RunningTaskInfo> updatedTasks) {
            for (int i = 0; i < updatedTasks.size(); i++) {
                mUpdatedTaskIds.add(updatedTasks.get(i).taskId);
            }
        }
    }

    private Task setUpTask(String pkg) {
        final Task rootTask = createTask(mDisplayContent);
        when(rootTask.getBasePackageName()).thenReturn(pkg);
        return rootTask;
    }
}
