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

import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP

/**
 * Orchestrator class for handling task drag-resizing and drag-moving operations.
 *
 * This class acts as a controller that delegates the actual work to either a [TaskMover] or a
 * [TaskResizer] implementation based on the user's input (ctrlType).
 *
 * @param windowDecoration The [WindowDecorationWrapper] associated with the task being positioned.
 * @param displayController A [DisplayController] to query display information.
 * @param taskMover The [TaskMover] implementation to use for drag-move operations.
 * @param taskResizer The [TaskResizer] implementation to use for drag-resize operations.
 * @param transitions The [Transitions] controller for handling window transitions.
 * @param handler The handler for the main shell thread.
 */
class ResizeTaskPositioner(
    private val windowDecoration: WindowDecorationWrapper,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
    private val taskMover: TaskMover,
    private val taskResizer: TaskResizer,
    private val transitions: Transitions,
    private val handler: Handler,
) : TaskPositioner {
    private var dragSession: DragSession? = null
    private val isResizing: Boolean
        get() =
            dragSession?.let {
                (it.ctrlType and CTRL_TYPE_TOP) != 0 ||
                    (it.ctrlType and CTRL_TYPE_BOTTOM) != 0 ||
                    (it.ctrlType and CTRL_TYPE_LEFT) != 0 ||
                    (it.ctrlType and CTRL_TYPE_RIGHT) != 0
            } ?: false

    override fun onDragPositioningStart(
        ctrlType: Int,
        displayId: Int,
        x: Float,
        y: Float,
        inputMethodType: Int,
    ): Rect {
        val taskBounds = Rect(windowDecoration.taskInfo.configuration.windowConfiguration.bounds)
        val newDragSession =
            DragSession(
                    windowDecoration,
                    displayController,
                    desktopState,
                    ctrlType = ctrlType,
                    taskBoundsAtDragStart = Rect(taskBounds),
                    repositionTaskBounds = Rect(taskBounds),
                    repositionStartPoint = PointF(x, y),
                )
                .also { dragSession = it }

        val rotation = windowDecoration.taskInfo.configuration.windowConfiguration.displayRotation
        newDragSession.rotation = rotation
        displayController
            .getDisplayLayout(windowDecoration.taskInfo.displayId)
            ?.getStableBounds(newDragSession.stableBounds)

        return if (isResizing) {
            taskResizer.onResizeStart(newDragSession, x, y)
        } else {
            taskMover.onMoveStart(newDragSession, x, y)
        }
    }

    override fun onDragPositioningMove(displayId: Int, x: Float, y: Float): Rect {
        check(Looper.myLooper() == handler.looper) {
            "This method must run on the shell main thread."
        }

        return if (isResizing) {
            taskResizer.onResizeUpdate(x, y)
        } else {
            taskMover.onMoveUpdate(displayId, x, y)
        }
    }

    override fun onDragPositioningEnd(displayId: Int, x: Float, y: Float): Rect {
        val resultBounds =
            if (isResizing) {
                taskResizer.onResizeEnd(x, y)
            } else {
                taskMover.onMoveEnd(displayId, x, y)
            }

        dragSession = null

        return resultBounds
    }

    override fun close() {
        transitions.unregisterObserver(taskResizer)
    }

    override fun isResizingOrAnimating(): Boolean {
        return taskResizer.isResizingOrAnimating()
    }

    override fun addDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {}

    override fun removeDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {}
}
