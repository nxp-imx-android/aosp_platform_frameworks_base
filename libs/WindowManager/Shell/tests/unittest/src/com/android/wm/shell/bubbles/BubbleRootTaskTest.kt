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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_PRESERVE_LEAF_TASK_IF_RELAUNCH
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Unit tests for [BubbleRootTask].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:BubbleRootTaskTest
 */
@SmallTest
class BubbleRootTaskTest : ShellTestCase() {

    private val shellInit = mock<ShellInit>()
    private val taskOrganizer = mock<ShellTaskOrganizer>()
    private lateinit var bubbleRootTask: BubbleRootTask

    @Before
    fun setUp() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun init_flagDisabled_doNothing() {
        verify(shellInit, never()).addInitCallback<BubbleRootTask>(any(), any())
        verify(taskOrganizer, never()).createTask(any())
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun init_createRootTask() {
        val initCallback = argumentCaptor<Runnable>().let { initCallbackCaptor ->
            verify(shellInit).addInitCallback<BubbleRootTask>(initCallbackCaptor.capture(), any())
            initCallbackCaptor.firstValue
        }
        verify(taskOrganizer, never()).createTask(any())

        initCallback.run()

        verify(taskOrganizer).createTask(any(), eq(bubbleRootTask))
    }

    @Test
    fun onTaskAppeared_updateRootTaskProperties() {
        val token = MockToken.token()
        val binder = token.asBinder()
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123, bubbleRootToken = token)

        assertThat(bubbleRootTask.taskId).isEqualTo(123)
        assertThat(bubbleRootTask.windowContainerToken).isEqualTo(token)

        // verify wct
        val wct = argumentCaptor<WindowContainerTransaction>().let { wctCaptor ->
            verify(taskOrganizer).applyTransaction(wctCaptor.capture())
            wctCaptor.firstValue
        }

        // verify hierarchy ops
        assertThat(wct.hierarchyOps.map { it.type }).containsAtLeast(
            HIERARCHY_OP_TYPE_REORDER,
            HIERARCHY_OP_TYPE_SET_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME,
            HIERARCHY_OP_TYPE_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN,
            HIERARCHY_OP_TYPE_SET_PRESERVE_LEAF_TASK_IF_RELAUNCH,
        )

        // verify changes
        assertThat(wct.changes[binder]).isNotNull()
        val change = wct.changes[binder]!!
        assertThat(change.interceptBackPressed).isTrue()
        assertThat(change.forceExcludedFromRecents).isTrue()
        assertThat(change.disablePip).isTrue()
        assertThat(change.disableLaunchAdjacent).isTrue()
        assertThat(change.forceTranslucent).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun onTaskInfoChanged_updatesRootTask() {
        val initCallbackCaptor = argumentCaptor<Runnable>()
        verify(shellInit).addInitCallback<BubbleRootTask>(initCallbackCaptor.capture(), any())
        initCallbackCaptor.firstValue.run()

        val token = MockToken.token()
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123, bubbleRootToken = token)

        assertThat(bubbleRootTask.taskId).isEqualTo(123)

        val updatedTaskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = 456
            this.token = token
        }
        bubbleRootTask.onTaskInfoChanged(updatedTaskInfo)

        assertThat(bubbleRootTask.taskId).isEqualTo(456)
    }

    companion object {
        /**
         * Test helper to prepare the [BubbleRootTask] instance for testing.
         *
         * @param bubbleRootTaskId the task ID to assign to the mock root task.
         * @param bubbleRootToken the [WindowContainerToken] to assign to the mock root task,
         * defaults to a new [MockToken] if not provided.
         */
        fun BubbleRootTask.prepareRootTaskForTest(
            bubbleRootTaskId: Int,
            bubbleRootToken: WindowContainerToken = MockToken.token()
        ) {
            val bubbleRootTaskInfo = ActivityManager.RunningTaskInfo().apply {
                taskId = bubbleRootTaskId
                token = bubbleRootToken
            }
            onTaskAppeared(bubbleRootTaskInfo, mock<SurfaceControl>())
        }
    }
}
