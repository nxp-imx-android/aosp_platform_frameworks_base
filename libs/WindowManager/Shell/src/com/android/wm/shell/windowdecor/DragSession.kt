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
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopState

/**
 * A data class to hold the shared state and dependencies for a drag operation.
 *
 * @param windowDecoration The window decoration being dragged.
 * @param displayController A [DisplayController] to query display information.
 * @param desktopState A [DesktopState] to check if desktop mode is active.
 */
data class DragSession(
    val windowDecoration: WindowDecorationWrapper,
    val displayController: DisplayController,
    val desktopState: DesktopState,
    @DragPositioningCallback.CtrlType var ctrlType: Int = 0,
    val taskBoundsAtDragStart: Rect = Rect(),
    val repositionStartPoint: PointF = PointF(),
    val repositionTaskBounds: Rect = Rect(),
    val stableBounds: Rect = Rect(),
    var pendingResizeTransition: IBinder? = null,
    @Surface.Rotation var rotation: Int = 0,
    var isResizingOrAnimatingResize: Boolean = false,
)
