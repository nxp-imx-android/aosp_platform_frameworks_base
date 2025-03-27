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
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.TaskStackListenerCallback
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES

/**
 * Listens for task stack changes and handles bubble interactions when activities are restarted.
 *
 * This class monitors task stack events to determine how bubbles should behave when their
 * associated activities are restarted. It handles scenarios where bubbles should be expanded.
 *
 * @property bubbleController The [BubbleController] to manage bubble promotions and expansions.
 * @property bubbleData The [BubbleData] to access and update bubble information.
 */
class BubbleTaskStackListener(
    private val bubbleController: BubbleController,
    private val bubbleData: BubbleData,
) : TaskStackListenerCallback {

    override fun onActivityRestartAttempt(
        task: ActivityManager.RunningTaskInfo,
        homeTaskVisible: Boolean,
        clearedTask: Boolean,
        wasVisible: Boolean,
    ) {
        val taskId = task.taskId
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            selectAndExpandInStackBubble(bubble, task)
            return@onActivityRestartAttempt
        }

        bubbleData.getOverflowBubbleWithTaskId(taskId)?.let { bubble ->
            selectAndExpandOverflowBubble(bubble, task)
        }
    }

    /** Selects and expands a bubble that is currently in the stack. */
    private fun selectAndExpandInStackBubble(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        ProtoLog.d(
            WM_SHELL_BUBBLES,
            "selectAndExpandInStackBubble - taskId=%d selecting matching bubble=%s",
            task.taskId,
            bubble.key,
        )
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
    }

    /** Selects and expands a bubble that is currently in the overflow. */
    private fun selectAndExpandOverflowBubble(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        ProtoLog.d(
            WM_SHELL_BUBBLES,
            "selectAndExpandOverflowBubble - taskId=%d selecting matching overflow bubble=%s",
            task.taskId,
            bubble.key,
        )
        bubbleController.promoteBubbleFromOverflow(bubble)
        bubbleData.setExpanded(true)
    }
}
