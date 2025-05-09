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
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.WindowManager.TransitionType
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Unit tests of [BubblesTransitionObserver].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:BubblesTransitionObserverTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class BubblesTransitionObserverTest : ShellTestCase() {

    private val bubble = mock<Bubble> {
        on { taskId } doReturn 1
    }
    private val bubbleData = mock<BubbleData> {
        on { isExpanded } doReturn true
        on { selectedBubble } doReturn bubble
    }
    private val bubbleController = mock<BubbleController> {
        on { isStackAnimating } doReturn false
    }
    private val transitionObserver = BubblesTransitionObserver(bubbleController, bubbleData)

    @Test
    fun testOnTransitionReady_openWithTaskTransition_collapsesStack() {
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_openTaskOnAnotherDisplay_doesNotCollapseStack() {
        val taskInfo = createTaskInfo(taskId = 2).apply {
            displayId = 1 // not DEFAULT_DISPLAY
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskInfo)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testOnTransitionReady_openTaskByBubble_doesNotCollapseStack() {
        val taskInfo = createTaskInfo(taskId = 2)
        bubbleController.stub {
            on { shouldBeAppBubble(taskInfo) } doReturn true // Launched by another bubble.
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskInfo)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_toFront_collapsesStack() {
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noTaskInfoNoActivityInfo_skip() {
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskInfo = null) // Null task info

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noTaskId_skip() {
        val info = createTaskTransition(TRANSIT_OPEN, taskId = INVALID_TASK_ID) // Invalid task id

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_notOpening_skip(@TestParameter tc: TransitNotOpeningTestCase) {
        transitionObserver.onTransitionReady(mock(), tc.info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_stackAnimating_skip() {
        bubbleController.stub {
            on { isStackAnimating } doReturn true // Stack is animating
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_stackNotExpanded_skip() {
        bubbleData.stub {
            on { isExpanded } doReturn false // Stack is not expanded
        }
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noSelectedBubble_skip() {
        bubbleData.stub {
            on { selectedBubble } doReturn null // No selected bubble
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_openingMatchesExpanded_skip() {
        // What's moving to front is the same as the opened bubble.
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 1)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    // Transits that aren't opening.
    enum class TransitNotOpeningTestCase(
        @TransitionType private val changeType: Int,
        private val taskId: Int,
    ) {
        CHANGE(TRANSIT_CHANGE, taskId = 2),
        CLOSE(TRANSIT_CLOSE, taskId = 3),
        BACK(TRANSIT_TO_BACK, taskId = 4);

        val info: TransitionInfo
            get() = createTaskTransition(changeType, taskId)
    }

    companion object {
        private fun createTaskTransition(@TransitionType changeType: Int, taskId: Int) =
            createTaskTransition(changeType, taskInfo = createTaskInfo(taskId))

        private fun createTaskTransition(
            @TransitionType changeType: Int,
            taskInfo: ActivityManager.RunningTaskInfo?,
        ) = TransitionInfoBuilder(TRANSIT_OPEN).addChange(changeType, taskInfo).build()

        private fun createTaskInfo(taskId: Int) = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.token = WindowContainerToken(mock() /* realToken */)
            this.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        }
    }
}
