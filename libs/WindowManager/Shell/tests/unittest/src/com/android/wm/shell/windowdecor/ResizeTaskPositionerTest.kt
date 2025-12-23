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
import android.os.IBinder
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Tests for [ResizeTaskPositioner].
 *
 * Build/Install/Run: atest WMShellUnitTests:ResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ResizeTaskPositionerTest : ShellTestCase() {
    private val mockDisplay = mock<Display>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTaskMover = mock<TaskMover>()
    private val mockTaskResizer = mock<TaskResizer>()
    private val mockTransitions = mock<Transitions>()
    private val mockTransitionBinder = mock<IBinder>()
    private val mockWindowContainerTransaction = mock<WindowContainerTransaction>()
    private val mockWindowDecoration = mock<WindowDecorationWrapper>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var taskPositioner: ResizeTaskPositioner

    @Before
    fun setUp() {
        val displayLayout = DisplayLayout(mContext, mockDisplay)
        whenever(mockDisplayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(mockWindowDecoration.taskInfo)
            .thenReturn(
                ActivityManager.RunningTaskInfo().apply {
                    taskId = TASK_ID
                    configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                }
            )

        whenever(mockTaskResizer.onResizeEnd(any(), any(), any()))
            .thenReturn(mockWindowContainerTransaction)
        whenever(mockTransitions.startTransition(any(), any(), any()))
            .thenReturn(mockTransitionBinder)

        taskPositioner =
            ResizeTaskPositioner(
                mockWindowDecoration,
                mockDisplayController,
                mockTaskMover,
                mockTaskResizer,
                mockTransitions,
                mainHandler,
            )
    }

    @Test
    fun testResize() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_RIGHT,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )

        // Move
        taskPositioner.onDragPositioningMove(0, 200f, 200f)
        verify(mockTaskResizer).onResizeUpdate(any(), eq(200f), eq(200f))

        // End

        taskPositioner.onDragPositioningEnd(0, 50f, 50f)
        verify(mockTaskResizer).onResizeEnd(any(), eq(50f), eq(50f))

        // Start animation
        taskPositioner.startAnimation(
            mockTransitionBinder,
            mock<TransitionInfo>(),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )
        verify(mockTaskResizer).cleanup(any())

        verifyNoInteractions(mockTaskMover)
    }

    @Test
    fun testMove() = runOnUiThread {
        // Start
        taskPositioner.onDragPositioningStart(
            DragPositioningCallback.CTRL_TYPE_UNDEFINED,
            0,
            100f,
            100f,
            DragPositioningCallback.INPUT_METHOD_TYPE_UNKNOWN,
        )

        // Move
        taskPositioner.onDragPositioningMove(1, 200f, 200f)
        verify(mockTaskMover).onMoveUpdate(any(), eq(1), eq(200f), eq(200f))

        // End
        taskPositioner.onDragPositioningEnd(2, 50f, 50f)
        verify(mockTaskMover).onMoveEnd(any(), eq(2), eq(50f), eq(50f))

        // Start animation
        taskPositioner.startAnimation(
            mockTransitionBinder,
            mock<TransitionInfo>(),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verifyNoInteractions(mockTaskResizer)
    }

    companion object {
        private const val TASK_ID = 5
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
    }
}
