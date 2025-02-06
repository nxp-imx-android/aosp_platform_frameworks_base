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

package com.android.wm.shell.shared.bubbles

/**
 * Manages animating drop targets in response to dragging bubble icons or bubble expanded views
 * across different drag zones.
 */
class DropTargetManager(
    private val isLayoutRtl: Boolean,
    private val dragZoneChangedListener: DragZoneChangedListener
) {

    private var state: DragState? = null

    /** Must be called when a drag gesture is starting. */
    fun onDragStarted(draggedObject: DraggedObject, dragZones: List<DragZone>) {
        val state = DragState(dragZones, draggedObject)
        dragZoneChangedListener.onInitialDragZoneSet(state.initialDragZone)
        this.state = state
    }

    /** Called when the user drags to a new location. */
    fun onDragUpdated(x: Int, y: Int) {
        val state = state ?: return
        val oldDragZone = state.currentDragZone
        val newDragZone = state.getMatchingDragZone(x = x, y = y)
        state.currentDragZone = newDragZone
        if (oldDragZone != newDragZone) {
            dragZoneChangedListener.onDragZoneChanged(from = oldDragZone, to = newDragZone)
        }
    }

    /** Called when the drag ended. */
    fun onDragEnded() {
        state = null
    }

    /** Stores the current drag state. */
    private inner class DragState(
        private val dragZones: List<DragZone>,
        draggedObject: DraggedObject
    ) {
        val initialDragZone =
            if (draggedObject.initialLocation.isOnLeft(isLayoutRtl)) {
                dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
            } else {
                dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
            }
        var currentDragZone: DragZone = initialDragZone

        fun getMatchingDragZone(x: Int, y: Int): DragZone {
            return dragZones.firstOrNull { it.contains(x, y) } ?: currentDragZone
        }
    }

    /** An interface to be notified when drag zones change. */
    interface DragZoneChangedListener {
        /** An initial drag zone was set. Called when a drag starts. */
        fun onInitialDragZoneSet(dragZone: DragZone)
        /** Called when the object was dragged to a different drag zone. */
        fun onDragZoneChanged(from: DragZone, to: DragZone)
    }
}
