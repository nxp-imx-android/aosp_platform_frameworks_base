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

/** Interface for moving a task via drag gestures. */
interface TaskMover {
    /**
     * Called when a drag move starts.
     *
     * @param session The drag session for the current drag operation.
     * @param x The x coordinate of the drag start point.
     * @param y The y coordinate of the drag start point.
     * @return The bounds of the task at the start of the move.
     */
    fun onMoveStart(session: DragSession, x: Float, y: Float): Rect

    /**
     * Called when a drag move updates.
     *
     * @param displayId The ID of the display where the drag is happening.
     * @param x The new x coordinate of the drag point.
     * @param y The new y coordinate of the drag point.
     * @return The new bounds of the task.
     */
    fun onMoveUpdate(displayId: Int, x: Float, y: Float): Rect

    /**
     * Called when a drag move ends.
     *
     * @param displayId The ID of the display where the drag ended.
     * @param x The final x coordinate of the drag point.
     * @param y The final y coordinate of the drag point.
     * @return The final bounds of the task.
     */
    fun onMoveEnd(displayId: Int, x: Float, y: Float): Rect
}
