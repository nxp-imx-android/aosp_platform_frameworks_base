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

import android.app.ActivityManager
import android.util.SparseArray
import androidx.core.util.forEach
import androidx.core.util.isEmpty
import androidx.core.util.size
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_INTERACTIVE_TASKS

/**
 * A repository that stores interactive tasks.
 *
 * The repository is not synchronized in any way, and it's supposed to be used only within
 * [Transitions] to guarantee valid task representation observer by the Shell.
 */
class InteractiveTasksRepository {

    /**
     * Stores interactive tasks per displays. Outer [SparseArray] is `displayId -> Array<Tasks>`.
     * Inner [SparseArray] is `taskId -> TaskInfo`.
     */
    private val interactiveTasks = SparseArray<SparseArray<ActivityManager.RunningTaskInfo>>()

    /**
     * Adds a [taskInfo] to the repository as an interactive task.
     *
     * This method will automatically clean up previous display references to the task if they
     * exist.
     */
    fun addTask(taskInfo: ActivityManager.RunningTaskInfo) {
        val taskId = taskInfo.taskId
        val displayId = taskInfo.displayId
        ProtoLog.d(
            WM_SHELL_INTERACTIVE_TASKS,
            "Add taskId=%d to displayId=%d as an interactive task.",
            taskId,
            displayId,
        )

        if (!taskInfo.isInteractive) {
            ProtoLog.d(
                WM_SHELL_INTERACTIVE_TASKS,
                "taskId=%d is not interactive. Skip adding to displayId=%d.",
                taskId,
                displayId,
            )
            return
        }

        if (isTaskInteractiveOnDisplay(displayId, taskId)) {
            ProtoLog.d(
                WM_SHELL_INTERACTIVE_TASKS,
                "taskId=%d on displayId=%d is already saved as resumed. Skip it.",
                taskId,
                displayId,
            )
            return
        }

        // Clean up old display references because the task might have been moved.
        removeTask(taskInfo)

        if (displayId !in interactiveTasks) {
            interactiveTasks[displayId] = SparseArray<ActivityManager.RunningTaskInfo>()
        }
        interactiveTasks[displayId][taskId] = taskInfo
    }

    /** Removes a [ActivityManager.RunningTaskInfo] from interactive tasks. */
    fun removeTask(taskInfo: ActivityManager.RunningTaskInfo) {
        val taskId = taskInfo.taskId
        val displayId = taskInfo.displayId
        ProtoLog.d(
            WM_SHELL_INTERACTIVE_TASKS,
            "Remove taskId=%d from the displayId=%d",
            taskId,
            displayId,
        )

        if (isTaskInteractiveOnDisplay(displayId, taskId)) {
            removeTaskFromDisplay(displayId, taskId)
            return
        }

        ProtoLog.d(
            WM_SHELL_INTERACTIVE_TASKS,
            "taskId=%d is not on the displayId=%d. Searching other displays for the task.",
            taskId,
            displayId,
        )
        for (i in 0 until interactiveTasks.size) {
            val candidateDisplayId = interactiveTasks.keyAt(i)
            removeTaskFromDisplay(candidateDisplayId, taskId)
            break
        }
    }

    private fun removeTaskFromDisplay(displayId: Int, taskId: Int) {
        val resumedTasks = interactiveTasks[displayId] ?: return
        resumedTasks.remove(taskId)
        if (resumedTasks.isEmpty()) {
            interactiveTasks.remove(displayId)
        }
    }

    /**
     * Checks whether a task with [taskId] is associated and interactive on a display with
     * [displayId].
     */
    fun isTaskInteractiveOnDisplay(displayId: Int, taskId: Int): Boolean =
        displayId in interactiveTasks && taskId in interactiveTasks[displayId]

    /**
     * Returns interactive tasks [List] associated with a [displayId]. Can be empty, but never
     * `null`.
     */
    fun getTasks(displayId: Int): List<ActivityManager.RunningTaskInfo> {
        val tasks = interactiveTasks[displayId] ?: return emptyList()
        return buildList { tasks.forEach { _, info -> add(info) } }
    }
}
