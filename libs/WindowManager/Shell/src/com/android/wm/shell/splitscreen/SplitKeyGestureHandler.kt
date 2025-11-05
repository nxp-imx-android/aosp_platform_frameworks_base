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

import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.os.IBinder
import android.util.Log
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.transition.FocusTransitionObserver
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Handles key gesture events (keyboard shortcuts) in Split Screen. */
class SplitKeyGestureHandler(
    private val stageCoordinator: StageCoordinator,
    private val desktopTasksController: Optional<DesktopTasksController>,
    inputManager: InputManager,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val taskOrganizer: ShellTaskOrganizer
) : KeyGestureEventHandler {

    private val TAG = "SplitKeyGestureHandler"

    init {
        val supportedGestures =
            listOf(
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT,
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT
            )
        inputManager.registerKeyGestureEventHandler(supportedGestures, this)
    }

    override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
        when (event.keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT -> {
                handleShortcutInternal(SPLIT_POSITION_TOP_OR_LEFT, event.displayId)
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT -> {
                handleShortcutInternal(SPLIT_POSITION_BOTTOM_OR_RIGHT, event.displayId)
            }
        }
    }

    private fun handleShortcutInternal(position: Int, displayId: Int) {
        val focusedTaskId = focusTransitionObserver.globallyFocusedTaskId
        // We explicitly pass in the userId below because it helps make testing
        // easier by avoiding needing to make full working mocks for DesktopTaskController.
        // Technically we could avoid and let it use ShellController.currentUserId
        val runningTaskInfo = taskOrganizer.getRunningTaskInfo(focusedTaskId)
        if (runningTaskInfo == null) {
            Log.w(TAG, "No running task for split keyboard shortcut. Focused task: $focusedTaskId")
            return
        }

        val desktopController = desktopTasksController.getOrNull()
        if (desktopController?.isAnyDeskActive(displayId, runningTaskInfo.userId) == true) {
            desktopController.enterSplit(
                displayId,
                runningTaskInfo.userId,
                position == SPLIT_POSITION_TOP_OR_LEFT
            )
        } else {
            // Handle entering stage split from fullscreen
            if (stageCoordinator.isSplitScreenVisible) return
            if (runningTaskInfo.windowingMode != WINDOWING_MODE_FULLSCREEN) return

            val taskBounds = runningTaskInfo.configuration.windowConfiguration.bounds
            stageCoordinator.requestEnterSplitSelect(runningTaskInfo,
                position, taskBounds, true /*startRecents*/, null /*withRecentsWct*/)
        }
    }
}
