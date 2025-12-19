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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.view.SurfaceControl
import android.window.TaskCreationParams
import android.window.TaskPropertiesRequest
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewTransitions

/**
 * Class to manage the bubble root task.
 *
 * Note: This class should only be accessed through [BubbleHelper].
 */
class BubbleRootTask(
    private val context: Context,
    shellInit: ShellInit,
    private val taskOrganizer: ShellTaskOrganizer,
    private val taskViewTransitions: TaskViewTransitions,
) : ShellTaskOrganizer.TaskListener {

    init {
        if (BubbleAnythingFlagHelper.enableRootTaskForBubble()) {
            shellInit.addInitCallback({ onInit() }, this)
        }
    }

    private var rootTaskInfo: ActivityManager.RunningTaskInfo? = null

    val windowContainerToken: WindowContainerToken?
        get() = rootTaskInfo?.token

    val taskId: Int
        get() = rootTaskInfo?.taskId ?: INVALID_TASK_ID

    /** Create the root task for bubbles. */
    private fun onInit() {
        // Create a root-task in WM Core. The app bubble tasks will be positioned as the leaf
        // tasks under this root-task.
        // The app bubble should be dismissed with proper transition (such as need to convert
        // it to fullscreen) if the bubble task is no longer be a leaf task under this leaf
        // task. Note that bubbles should ignore insets and should not show app compat rounded
        // corners for better UX (e.g. when landscape apps are letterboxed).
        val taskProperties =
            TaskPropertiesRequest().setIgnoreInsets(true).setDisableAppCompatRoundedCorners(true)
        val params =
            TaskCreationParams.Builder()
                .setName("Bubbles")
                .setDisplayId(context.displayId)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setTaskPropertiesRequest(taskProperties)
                .build()
        taskOrganizer.createTask(params, this)
    }

    override fun onTaskAppeared(taskInfo: ActivityManager.RunningTaskInfo, leash: SurfaceControl) {
        if (rootTaskInfo != null) {
            return
        }
        rootTaskInfo = taskInfo
        taskViewTransitions.setTaskViewRootTaskInfo(taskInfo)

        val wct = WindowContainerTransaction()
        wct.reorder(taskInfo.token, false /* onTop */)
        wct.setReparentLeafTaskIfRelaunchFromHome(
            taskInfo.token,
            true, /* reparentLeafTaskIfRelaunchFromHome */
        )
        wct.setDisallowOverrideWindowingModeForChildren(
            taskInfo.token,
            true, /* disallowOverrideWindowingModeForChildren */
        )
        wct.setInterceptBackPressedOnTaskRoot(taskInfo.token, true /* interceptBackPressed */)
        wct.setTaskForceExcludedFromRecents(taskInfo.token, true /* forceExcluded */)
        wct.setDisablePip(taskInfo.token, true /* disablePip */)
        wct.setDisableLaunchAdjacent(taskInfo.token, true /* disableLaunchAdjacent */)
        wct.setForceTranslucent(taskInfo.token, true /* forceTranslucent */)
        taskOrganizer.applyTransaction(wct)
    }
}
