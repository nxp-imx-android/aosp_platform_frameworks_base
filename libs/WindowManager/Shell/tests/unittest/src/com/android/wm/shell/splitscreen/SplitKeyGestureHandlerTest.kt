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

package com.android.wm.shell.splitscreen

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.hardware.input.AidlKeyGestureEvent
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.transition.FocusTransitionObserver
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Optional

//import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class SplitKeyGestureHandlerTest : ShellTestCase() {

    @Mock
    private lateinit var stageCoordinator: StageCoordinator

    @Mock
    private lateinit var desktopTasksController: DesktopTasksController

    @Mock
    private lateinit var inputManager: InputManager

    @Mock
    private lateinit var focusTransitionObserver: FocusTransitionObserver

    @Mock
    private lateinit var taskOrganizer: ShellTaskOrganizer

    private lateinit var splitKeyGestureHandler: SplitKeyGestureHandler
    private val displayId = 1
    val runningTaskInfo = RunningTaskInfo()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val taskId = 123
        runningTaskInfo.taskId = taskId
        runningTaskInfo.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(taskId)
        whenever(taskOrganizer.getRunningTaskInfo(taskId)).thenReturn(runningTaskInfo)

        splitKeyGestureHandler = SplitKeyGestureHandler(
            stageCoordinator,
            Optional.of(desktopTasksController),
            inputManager,
            focusTransitionObserver,
            taskOrganizer,
        )
    }

    @Test
    fun testLeftNavigation_desktopModeActive() {
        whenever(desktopTasksController.isAnyDeskActive(displayId, 0)).thenReturn(true)

        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(desktopTasksController).enterSplit(eq(displayId),
            eq(runningTaskInfo.userId), eq(true))
    }

    @Test
    fun testRightNavigation_desktopModeActive() {
        whenever(desktopTasksController.isAnyDeskActive(displayId, 0)).thenReturn(true)

        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(desktopTasksController).enterSplit(eq(displayId),
            eq(runningTaskInfo.userId), eq(false))
    }

    @Test
    fun testLeftNavigation_desktopModeInactive() {
        whenever(desktopTasksController.isAnyDeskActive(displayId, 0)).thenReturn(false)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(false)

        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(stageCoordinator).requestEnterSplitSelect(
            eq(runningTaskInfo),
            eq(SPLIT_POSITION_TOP_OR_LEFT),
            any(),
            eq(true),
            eq(null)
        )
    }

    @Test
    fun testRightNavigation_desktopModeInactive() {
        whenever(desktopTasksController.isAnyDeskActive(displayId, 0)).thenReturn(false)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(false)

        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(stageCoordinator).requestEnterSplitSelect(
            eq(runningTaskInfo),
            eq(SPLIT_POSITION_BOTTOM_OR_RIGHT),
            any(),
            eq(true),
            eq(null)
        )
    }

    @Test
    fun testNavigation_splitScreenVisible() {
        whenever(desktopTasksController.isAnyDeskActive(displayId, 0)).thenReturn(false)
        whenever(stageCoordinator.isSplitScreenVisible).thenReturn(true)

        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(stageCoordinator, never()).requestEnterSplitSelect(
            any(),
            anyInt(),
            any(),
            anyBoolean(),
            any()
        )
    }

    @Test
    fun testNavigation_noFocusedTask() {
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(INVALID_TASK_ID)
        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(stageCoordinator, never()).requestEnterSplitSelect(
            any(),
            anyInt(),
            any(),
            anyBoolean(),
            any()
        )

        verifyNoInteractions(desktopTasksController)
    }

    @Test
    fun testNavigation_notFullscreen() {
        runningTaskInfo.configuration.windowConfiguration.windowingMode =
            WINDOWING_MODE_MULTI_WINDOW
        val aidlEvent = AidlKeyGestureEvent()
        aidlEvent.gestureType = KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT
        aidlEvent.displayId = displayId
        val event = KeyGestureEvent(aidlEvent)
        splitKeyGestureHandler.handleKeyGestureEvent(event, null)

        verify(stageCoordinator, never()).requestEnterSplitSelect(
            any(),
            anyInt(),
            any(),
            anyBoolean(),
            any()
        )

        verify(desktopTasksController, never()).enterSplit(
            eq(displayId),
            eq(runningTaskInfo.userId),
            eq(false)
        )
    }
}
