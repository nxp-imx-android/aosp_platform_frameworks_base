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
import android.app.ActivityTaskManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TaskAppearedInfo
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.window.flags.Flags.FLAG_QUICK_BUBBLE_SWITCH
import com.android.window.flags.Flags.FLAG_VISIBILITY_MANAGEMENT_IN_BUBBLE_ROOT
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.BubbleRootTaskTest.Companion.prepareRootTaskForTest
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Unit tests for [BubbleHelperImpl].
 *
 * Build/Install/Run: atest WMShellUnitTests:BubbleHelperImplTest
 */
@SmallTest
class BubbleHelperImplTest : ShellTestCase() {

    private val leash = mock<SurfaceControl>()
    private val shellInit = mock<ShellInit>()
    private val taskOrganizer = mock<ShellTaskOrganizer>()

    private lateinit var bubbleRootTask: BubbleRootTask
    private lateinit var bubbleHelper: BubbleHelper

    @Before
    fun setUp() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer)
        bubbleHelper = BubbleHelperImpl(bubbleRootTask = bubbleRootTask)
    }

    @Test
    fun getAppBubbleRootTaskToken() {
        val token = MockToken.token()
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123, bubbleRootToken = token)

        assertThat(bubbleHelper.getAppBubbleRootTaskToken()).isEqualTo(token)
    }

    @EnableFlags(FLAG_VISIBILITY_MANAGEMENT_IN_BUBBLE_ROOT)
    @Test
    fun getAppBubbleVisibilityBarrierToken() {
        val taskToken = MockToken.token()
        val bubbleTask =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 123
                token = taskToken
            }
        taskOrganizer.stub {
            on { createTask(any(), any()) } doReturn TaskAppearedInfo(bubbleTask, leash)
        }
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)

        assertThat(bubbleHelper.getAppBubbleVisibilityBarrierToken()).isEqualTo(taskToken)
    }

    @Test
    fun isAppBubbleRootTask() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)
        val taskInfo0 = ActivityManager.RunningTaskInfo().apply { taskId = 456 }
        val taskInfo1 = ActivityManager.RunningTaskInfo().apply { taskId = 123 }

        assertThat(bubbleHelper.isAppBubbleRootTask(777)).isFalse()
        assertThat(bubbleHelper.isAppBubbleRootTask(123)).isTrue()
        assertThat(bubbleHelper.isAppBubbleRootTask(taskInfo0)).isFalse()
        assertThat(bubbleHelper.isAppBubbleRootTask(taskInfo1)).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun isAppBubble_parentTaskMatchesBubbleRootTask_returnsTrue() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 777)

        val taskInfo = ActivityManager.RunningTaskInfo().apply { parentTaskId = 777 }

        assertThat(bubbleHelper.isAppBubbleTask(taskInfo)).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun isAppBubble_parentTaskDoesNotMatchesBubbleRootTask_returnsFalse() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)

        val taskInfo = ActivityManager.RunningTaskInfo().apply { parentTaskId = 456 }

        assertThat(bubbleHelper.isAppBubbleTask(taskInfo)).isFalse()
    }

    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun isAppBubble_taskIsSplitting_returnsFalse() {
        val sideStageRootTask = 5
        val taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                // Task is running in split-screen mode.
                parentTaskId = sideStageRootTask
                // Even though the task was previously marked as an app bubble,
                // it should not be considered a bubble when in split-screen mode.
                isAppBubble = true
            }

        assertThat(bubbleHelper.isAppBubbleTask(taskInfo)).isFalse()
    }

    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun isAppBubble_isAppBubbleNotSplitting_returnsTrue() {
        val taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                isAppBubble = true
                parentTaskId = ActivityTaskManager.INVALID_TASK_ID
            }

        assertThat(bubbleHelper.isAppBubbleTask(taskInfo)).isTrue()
    }

    @Test
    fun getEnterBubbleTask_filterForOpenTask() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)

        // Opening of non-Bubble
        val taskInfo0 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 456
                isAppBubble = false
            }
        val bubble0 =
            TransitionInfo.Change(taskInfo0.token, mock()).apply {
                taskInfo = taskInfo0
                mode = WindowManager.TRANSIT_OPEN
            }

        // Closing of Bubble
        val taskInfo1 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble1 =
            TransitionInfo.Change(taskInfo1.token, mock()).apply {
                taskInfo = taskInfo1
                mode = WindowManager.TRANSIT_CLOSE
            }

        // Opening of Bubble
        val taskInfo2 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble2 =
            TransitionInfo.Change(taskInfo2.token, mock()).apply {
                taskInfo = taskInfo2
                mode = WindowManager.TRANSIT_OPEN
            }

        val info =
            TransitionInfo(WindowManager.TRANSIT_OPEN, 0).apply {
                addChange(bubble0)
                addChange(bubble1)
                addChange(bubble2)
                addRoot(TransitionInfo.Root(0, mock(), 0, 0))
            }

        assertThat(bubbleHelper.getEnterBubbleTask(info)).isEqualTo(bubble2)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK, FLAG_QUICK_BUBBLE_SWITCH)
    @Test
    fun getEnterBubbleTask_filterForReparentToBubbleRootTask() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)

        // Changing of existing Bubble
        val taskInfo0 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble0 =
            TransitionInfo.Change(taskInfo0.token, mock()).apply {
                taskInfo = taskInfo0
                mode = WindowManager.TRANSIT_CHANGE
            }

        // Changing to reparent to Bubble
        val taskInfo1 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble1 =
            TransitionInfo.Change(taskInfo1.token, mock()).apply {
                taskInfo = taskInfo1
                lastParent = MockToken.token()
                mode = WindowManager.TRANSIT_CHANGE
            }

        val info =
            TransitionInfo(WindowManager.TRANSIT_CHANGE, 0).apply {
                addChange(bubble0)
                addChange(bubble1)
                addRoot(TransitionInfo.Root(0, mock(), 0, 0))
            }

        assertThat(bubbleHelper.getEnterBubbleTask(info)).isEqualTo(bubble1)
    }

    @Test
    fun getClosingBubbleTask_filterForCloseTask() {
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123)

        // Closing of non-Bubble
        val taskInfo0 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 456
                isAppBubble = false
            }
        val bubble0 =
            TransitionInfo.Change(taskInfo0.token, mock()).apply {
                taskInfo = taskInfo0
                mode = WindowManager.TRANSIT_CLOSE
            }

        // Opening of Bubble
        val taskInfo1 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble1 =
            TransitionInfo.Change(taskInfo1.token, mock()).apply {
                taskInfo = taskInfo1
                mode = WindowManager.TRANSIT_OPEN
            }

        // Closing of Bubble
        val taskInfo2 =
            ActivityManager.RunningTaskInfo().apply {
                token = MockToken().token()
                configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
                parentTaskId = 123
                isAppBubble = true
            }
        val bubble2 =
            TransitionInfo.Change(taskInfo2.token, mock()).apply {
                taskInfo = taskInfo2
                mode = WindowManager.TRANSIT_CLOSE
            }

        val info =
            TransitionInfo(WindowManager.TRANSIT_OPEN, 0).apply {
                addChange(bubble0)
                addChange(bubble1)
                addChange(bubble2)
                addRoot(TransitionInfo.Root(0, mock(), 0, 0))
            }

        assertThat(bubbleHelper.getClosingBubbleTask(info)).isEqualTo(bubble2)
    }
}
