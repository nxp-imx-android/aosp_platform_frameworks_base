/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.transition

import android.app.ActivityManager.RunningTaskInfo
import android.content.Intent
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the [InteractiveTasksRepository].
 *
 * Build/Install/Run: atest WMShellUnitTests:InteractiveTasksRepositoryTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class InteractiveTasksRepositoryTest : ShellTestCase() {

    private lateinit var repository: InteractiveTasksRepository

    @Before
    fun setup() {
        repository = InteractiveTasksRepository()
    }

    @Test
    fun testAddInteractiveTask_addedToInteractiveTasks() {
        val task = createTask(taskId = TASK_ID_0, displayId = DEFAULT_DISPLAY, isInteractive = true)
        repository.addTask(task)
        assertTrue(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))
    }

    @Test
    fun testAddNonInteractiveTaskToInteractive_skipAddingIt() {
        val task =
            createTask(taskId = TASK_ID_0, displayId = DEFAULT_DISPLAY, isInteractive = false)
        repository.addTask(task)
        assertFalse(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))
    }

    @Test
    fun testAddInteractiveTask_removeIt_shouldNotBeSavedAsInteractive() {
        val task = createTask(taskId = TASK_ID_0, displayId = DEFAULT_DISPLAY, isInteractive = true)
        repository.addTask(task)
        assertTrue(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))

        repository.removeTask(task)
        assertFalse(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))
    }

    @Test
    fun testInteractiveTaskMovedToAnotherDisplay_cleanUpPrevDisplayReference() {
        val task = createTask(taskId = TASK_ID_0, displayId = DEFAULT_DISPLAY, isInteractive = true)
        repository.addTask(task)
        assertTrue(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))

        task.displayId = 1
        repository.addTask(task)

        // Should be on secondary display
        assertTrue(repository.isTaskInteractiveOnDisplay(DISPLAY_ID_1, TASK_ID_0))
        // Should NOT be on default display
        assertFalse(repository.isTaskInteractiveOnDisplay(DEFAULT_DISPLAY, TASK_ID_0))
    }

    private fun createTask(
        taskId: Int,
        displayId: Int,
        isInteractive: Boolean = false,
        baseIntent: Intent = Intent(),
    ): RunningTaskInfo {
        val info = RunningTaskInfo()
        info.taskId = taskId
        info.displayId = displayId
        info.isInteractive = isInteractive
        info.baseIntent = baseIntent
        return info
    }

    private companion object {
        const val TASK_ID_0 = 0
        const val DISPLAY_ID_1 = 1
    }
}
