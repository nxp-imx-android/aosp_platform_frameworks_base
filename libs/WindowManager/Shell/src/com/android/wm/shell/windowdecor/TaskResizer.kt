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

import android.graphics.Rect
import com.android.wm.shell.transition.Transitions

/** Interface for resizing a task via drag gestures. */
interface TaskResizer : Transitions.TransitionHandler, Transitions.TransitionObserver {
    /**
     * Called when a drag resize starts.
     *
     * @param session The drag session for the current drag operation.
     * @param x The x coordinate of the drag start point.
     * @param y The y coordinate of the drag start point.
     * @return The bounds of the task at the start of the resize.
     */
    fun onResizeStart(session: DragSession, x: Float, y: Float): Rect

    /**
     * Called when a drag resize updates.
     *
     * @param x The new x coordinate of the drag point.
     * @param y The new y coordinate of the drag point.
     * @return The new bounds of the task.
     */
    fun onResizeUpdate(x: Float, y: Float): Rect

    /**
     * Called when a drag resize ends.
     *
     * @param x The final x coordinate of the drag point.
     * @param y The final y coordinate of the drag point.
     * @return The final bounds of the task.
     */
    fun onResizeEnd(x: Float, y: Float): Rect

    /**
     * Returns true if task is currently being resized or animating the final transition after a
     * resize is complete.
     */
    fun isResizingOrAnimating(): Boolean
}
