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

import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopState

/** Implementation of [TaskResizer] for fluid resizing of tasks. */
class FluidTaskResizer(
    private val taskOrganizer: ShellTaskOrganizer,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
) : TaskResizer {
    override fun onResizeStart(session: DragSession) {
        for (listener in session.resizeEventListeners) {
            listener.onDragResizeStarted(
                session.windowDecoration.taskInfo.taskId,
                session.resizeTrigger,
                session.inputMethod,
                session.taskBoundsAtDragStart,
            )
        }
    }

    override fun onResizeUpdate(session: DragSession, x: Float, y: Float) {
        val wct = WindowContainerTransaction()
        if (
            DragPositioningCallbackUtility.changeBounds(
                session.ctrlType,
                session.repositionTaskBounds,
                session.taskBoundsAtDragStart,
                session.stableBounds,
                DragPositioningCallbackUtility.calculateDelta(x, y, session.repositionStartPoint),
                displayController,
                session.windowDecoration,
                desktopState.canEnterDesktopMode,
            )
        ) {
            if (!session.isResizingOrAnimatingResize) {
                // This is the first bounds change since drag resize operation started.
                wct.setDragResizing(
                    session.windowDecoration.taskInfo.token,
                    /* dragResizing= */ true,
                )

                for (listener in session.resizeEventListeners) {
                    listener.onDragMove(session.windowDecoration.taskInfo.taskId)
                }

                // Clear the stored pre-snapped/maximized bounds to prevent unintended restoration
                // if the user manually resizes the window.
                if (DesktopExperienceFlags.ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT.isTrue) {
                    session.desktopRepository?.removeBoundsBeforeSnapOrMaximize(
                        session.windowDecoration.taskInfo.taskId
                    )
                }
                session.isResizingOrAnimatingResize = true
            }
            wct.setBounds(session.windowDecoration.taskInfo.token, session.repositionTaskBounds)
            taskOrganizer.applyTransaction(wct)
        }
    }

    override fun onResizeEnd(
        session: DragSession,
        x: Float,
        y: Float,
    ): WindowContainerTransaction? {
        for (listener in session.resizeEventListeners) {
            listener.onDragResizeEnded(
                session.windowDecoration.taskInfo.taskId,
                session.resizeTrigger,
                session.inputMethod,
                session.repositionTaskBounds,
            )
        }
        val boundsChangedBetweenUpdateAndEnd =
            DragPositioningCallbackUtility.changeBounds(
                session.ctrlType,
                session.repositionTaskBounds,
                session.taskBoundsAtDragStart,
                session.stableBounds,
                DragPositioningCallbackUtility.calculateDelta(x, y, session.repositionStartPoint),
                displayController,
                session.windowDecoration,
                desktopState.canEnterDesktopMode,
            )

        logD(
            TAG,
            session.windowDecoration.taskInfo.taskId,
            "onResizeEnd: isResizingOrAnimatingResize=%b, boundsChanged=%b, bounds=%s",
            session.isResizingOrAnimatingResize,
            boundsChangedBetweenUpdateAndEnd,
            session.repositionTaskBounds,
        )
        if (!session.isResizingOrAnimatingResize && !boundsChangedBetweenUpdateAndEnd) {
            return null
        }

        return WindowContainerTransaction().apply {
            if (session.isResizingOrAnimatingResize) {
                setDragResizing(session.windowDecoration.taskInfo.token, false /* dragResizing */)
            }
            if (boundsChangedBetweenUpdateAndEnd) {
                setBounds(session.windowDecoration.taskInfo.token, session.repositionTaskBounds)
            }
        }
    }

    override fun cleanup(session: DragSession) {
        logD(TAG, session.windowDecoration.taskInfo.taskId, "cleanup")
        // No-op for fluid resize, as there are no visual artifacts to clean up.
    }

    companion object {
        private const val TAG = "FluidTaskResizer"
    }
}
