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
package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [ResizeTaskPositioner].
 *
 * Build/Install/Run: atest WMShellUnitTests:ResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ResizeTaskPositionerTest : ShellTestCase() {
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTaskMover = mock<TaskMover>()
    private val mockTaskResizer = mock<TaskResizer>()
    private val mockTransitions = mock<Transitions>()

    private val desktopState = FakeDesktopState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var taskPositioner: ResizeTaskPositioner

    @Before
    fun setUp() {
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                }
            )

        taskPositioner =
            ResizeTaskPositioner(
                mockWindowDecoration,
                mockDisplayController,
                desktopState,
                mockTaskMover,
                mockTaskResizer,
                mockTransitions,
                mainHandler,
            )
    }

    @Test
    fun dragMove_whenDragEventsAreDispatched_thenTaskMoverIsInvoked() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_UNDEFINED,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )
        taskPositioner.onDragPositioningMove(0, 200f, 200f)
        taskPositioner.onDragPositioningEnd(1, 300f, 300f)

        verify(mockTaskMover).onMoveStart(any(), any(), any())
        verify(mockTaskMover).onMoveUpdate(0, 200f, 200f)
        verify(mockTaskMover).onMoveEnd(1, 300f, 300f)
    }

    @Test
    fun dragResize_whenResizeEventsAreDispatched_thenTaskResizerIsInvoked() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_RIGHT,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )
        taskPositioner.onDragPositioningMove(0, 200f, 200f)
        taskPositioner.onDragPositioningEnd(0, 200f, 200f)

        verify(mockTaskResizer).onResizeStart(any(), any(), any())
        verify(mockTaskResizer).onResizeUpdate(any(), any())
        verify(mockTaskResizer).onResizeEnd(any(), any())
    }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
    }
}
