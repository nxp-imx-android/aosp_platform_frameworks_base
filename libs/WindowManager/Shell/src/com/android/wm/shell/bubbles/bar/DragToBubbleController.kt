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

package com.android.wm.shell.bubbles.bar

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.draganddrop.DragAndDropController.DragAndDropListener
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DragZone
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DraggedObject
import com.android.wm.shell.shared.bubbles.DraggedObject.LauncherIcon
import com.android.wm.shell.shared.bubbles.DropTargetManager

/** Handles scenarios when launcher icon is being dragged to the bubble bar drop zones. */
class DragToBubbleController(
    val context: Context,
    val bubblePositioner: BubblePositioner,
    val bubbleController: BubbleController,
) : DragAndDropListener {

    private val containerView: FrameLayout =
        FrameLayout(context).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
        }

    private val isRtl: Boolean
        get() = containerView.isLayoutRtl

    private val dropTargetManager: DropTargetManager =
        DropTargetManager(context, containerView, createDragZoneListener())

    private val dragZoneFactory = createDragZoneFactory()
    private var lastDragZone: DragZone? = null

    /** Returns the container view in which drop targets are added. */
    fun getDropTargetContainer(): ViewGroup = containerView

    /** Called when the drag is tarted. */
    override fun onDragStarted() {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            return
        }
        val draggedObject = LauncherIcon(bubbleBarHasBubbles = true) {}
        val dragZones = dragZoneFactory.createSortedDragZones(draggedObject)
        dropTargetManager.onDragStarted(draggedObject, dragZones)
    }

    /**
     * Called when drag position is updated.
     *
     * @return true if drag is over any bubble bar drop zones
     */
    fun onDragUpdate(x: Int, y: Int): Boolean {
        lastDragZone = dropTargetManager.onDragUpdated(x, y)
        return lastDragZone != null
    }

    /** Called when the item with the [ShortcutInfo] is dropped over the bubble bar drop target. */
    fun onItemDropped(shortcutInfo: ShortcutInfo) {
        val dropLocation = lastDragZone?.getBubbleBarLocation() ?: return
        bubbleController.expandStackAndSelectBubble(shortcutInfo, dropLocation)
    }

    /**
     * Called when the item with the [PendingIntent] and the [UserHandle] is dropped over the
     * bubble bar drop target.
     */
    fun onItemDropped(pendingIntent: PendingIntent, userHandle: UserHandle) {
        val dropLocation = lastDragZone?.getBubbleBarLocation() ?: return
        bubbleController.expandStackAndSelectBubble(pendingIntent, userHandle, dropLocation)
    }

    /** Called when the drag is ended. */
    override fun onDragEnded() {
        dropTargetManager.onDragEnded()
    }

    private fun createDragZoneFactory(): DragZoneFactory {
        return DragZoneFactory(
            context,
            bubblePositioner.currentConfig,
            { SplitScreenMode.UNSUPPORTED },
            { false },
            object : BubbleBarPropertiesProvider {},
        )
    }

    private fun createDragZoneListener() = object : DropTargetManager.DragZoneChangedListener {

            private var lastUpdateLocation: BubbleBarLocation? = null
            private val isLocationChangedFromOriginal: Boolean
                get() = lastUpdateLocation != null
                        && isDifferentSides(lastUpdateLocation, bubbleController.bubbleBarLocation)

            override fun onInitialDragZoneSet(dragZone: DragZone?) {}

            override fun onDragZoneChanged(
                draggedObject: DraggedObject,
                from: DragZone?,
                to: DragZone?,
            ) {
                val updateLocation = getBarLocation(to)
                updateBubbleBarLocation(updateLocation)
                lastUpdateLocation = updateLocation
            }

            override fun onDragEnded(zone: DragZone?) {
                updateBubbleBarLocation(updateLocation = null)
            }

            fun updateBubbleBarLocation(updateLocation: BubbleBarLocation?) {
                val updatedBefore = lastUpdateLocation != null
                val originalLocation = bubbleController.bubbleBarLocation
                val isLocationUpdated = isDifferentSides(lastUpdateLocation, updateLocation)
                if (!bubbleController.hasBubbles()) {
                    // has no bubbles, so showing the pin view
                    if (updateLocation == null || !updatedBefore || isLocationUpdated) {
                        bubbleController.showBubbleBarPinAtLocation(updateLocation)
                    }
                    return
                }
                if (updateLocation == null && isLocationChangedFromOriginal) {
                    bubbleController.animateBubbleBarLocation(originalLocation)
                    return
                }
                if (updatedBefore && isLocationUpdated) {
                    // updated before and location updated - update to new location
                    bubbleController.animateBubbleBarLocation(updateLocation)
                    return
                }
                if (!updatedBefore && isDifferentSides(originalLocation, updateLocation)) {
                    // not updated before and location changed from original
                    bubbleController.animateBubbleBarLocation(updateLocation)
                }
            }

            fun getBarLocation(dragZone: DragZone?): BubbleBarLocation? {
                return when (dragZone) {
                    is DragZone.Bubble.Left -> BubbleBarLocation.LEFT
                    is DragZone.Bubble.Right -> BubbleBarLocation.RIGHT
                    else -> null
                }
            }

            fun isDifferentSides(f: BubbleBarLocation?, s: BubbleBarLocation?): Boolean {
                return f != null && s != null && f.isOnLeft(isRtl) != s.isOnLeft(isRtl)
            }
        }

    private fun DragZone.getBubbleBarLocation(): BubbleBarLocation? =
        when (this) {
            is DragZone.Bubble.Left -> BubbleBarLocation.LEFT
            is DragZone.Bubble.Right -> BubbleBarLocation.RIGHT
            else -> null
        }
}
