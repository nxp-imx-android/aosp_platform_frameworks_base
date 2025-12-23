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
import android.os.IBinder
import android.view.Surface

/**
 * A data class to hold the shared state and dependencies for a single drag-move or drag-resize
 * operation. An instance of this class is created at the start of a drag and discarded at the end.
 *
 * @param windowDecoration The [WindowDecorationWrapper] of the window being dragged.
 * @param ctrlType The control type that initiated the drag, indicating whether it's a move or which
 *   type of resize (e.g., [DragPositioningCallback.CTRL_TYPE_LEFT]).
 * @param taskBoundsAtDragStart The bounds of the task when the drag operation began. This is used
 *   as a baseline for calculating new bounds.
 * @param repositionStartPoint The initial coordinates (x, y) of the pointer at the start of the
 *   drag.
 * @param repositionTaskBounds The task's current bounds, which are updated throughout the drag
 *   operation. This should be used to store the in-progress bounds.
 * @param stableBounds The stable bounds of the display area, used to constrain window resizing.
 * @param rotation The display rotation at the start of the drag.
 * @param isResizingOrAnimatingResize A flag indicating whether there is a resize operation or a
 *   post-resize animation ongoing.
 * @param hasMovedTaskSurfaceOffScreen A flag used in drag-move scenarios to track if the main task
 *   surface has been moved off-screen (while a drag indicator is shown instead).
 * @param dragResizeEndTransition The binder token for the transition that is started when a
 *   drag-resize operation completes, used to track the post-drag animation.
 */
data class DragSession(
    val windowDecoration: WindowDecorationWrapper,
    @param:DragPositioningCallback.CtrlType val ctrlType: Int = 0,
    val taskBoundsAtDragStart: Rect = Rect(),
    val repositionStartPoint: PointF = PointF(),
    val repositionTaskBounds: Rect = Rect(),
    val stableBounds: Rect = Rect(),
    @param:Surface.Rotation val rotation: Int = 0,
    var isResizingOrAnimatingResize: Boolean = false,
    var hasMovedTaskSurfaceOffScreen: Boolean = false,
    var dragResizeEndTransition: IBinder? = null,
)
