/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.ui.composable

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.LayoutCoordinates
import com.android.compose.gesture.NestedDraggable
import com.android.compose.gesture.nestedDraggable
import com.android.systemui.ExpandHelper
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.ExpandableView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A [Modifier] that enables swipe-to-expand gestures for notifications by bridging Compose nested
 * scroll events to the legacy NSSL. It uses the [callback] to obtain and directly manipulate the
 * target [ExpandableView] while synchronizing with the Compose gesture system.
 */
fun Modifier.swipeToExpandNotification(draggable: SwipeToExpandNotificationDraggable): Modifier =
    this.nestedDraggable(draggable = draggable, orientation = Orientation.Vertical)

class SwipeToExpandNotificationDraggable(
    private val callback: ExpandHelper.Callback,
    private val layoutCoordinatesProvider: () -> LayoutCoordinates?,
    private val allowStartGesture: () -> Boolean = { true },
    private val velocityThresholdPx: Float,
    private val distanceThresholdPx: Float,
) : NestedDraggable {

    override fun onDragStarted(
        position: Offset,
        sign: Float,
        pointersDown: Int,
        pointerType: PointerType?,
    ): NestedDraggable.Controller {
        if (!allowStartGesture()) {
            return NoopController()
        }

        val target =
            obtainExpandableTarget(position)
                ?: run {
                    debugLog { "No target found." }
                    return NoopController()
                }
        return SwipeToExpandDragController(
            target,
            callback,
            velocityThresholdPx = velocityThresholdPx,
            distanceThresholdPx = distanceThresholdPx,
        )
    }

    private fun obtainExpandableTarget(positionDown: Offset): ExpandableView? {
        debugLog { "DOWN at $positionDown" }

        val coords = layoutCoordinatesProvider()
        if (coords == null || !coords.isAttached) {
            return null
        }

        // Touch coordinates are relative to the Composable where this node is attached.
        // Expand target positions are relative to the NSSL, so map them to be correct.
        val startPositionInWindow = coords.localToWindow(positionDown)

        // Find expandable view at position. Use the raw position to account for shade modes when
        // NSSL is not full screen.
        val targetView =
            callback.getChildAtRawPosition(startPositionInWindow.x, startPositionInWindow.y)
                ?: return null

        if (!callback.canChildBeExpanded(targetView)) {
            return null
        }

        // Check if fully expanded
        if (targetView.intrinsicHeight == targetView.maxContentHeight) {
            return null
        }

        debugLog {
            "Obtained target ${(targetView as? ExpandableNotificationRow)?.key ?: targetView}"
        }

        return targetView
    }
}

private class NoopController : NestedDraggable.Controller {
    override fun onDrag(delta: Float): Float = 0f

    override suspend fun onDragStopped(velocity: Float, awaitFling: suspend () -> Unit): Float = 0f
}

private class SwipeToExpandDragController(
    private val targetView: ExpandableView,
    private val callback: ExpandHelper.Callback,
    private val velocityThresholdPx: Float,
    private val distanceThresholdPx: Float,
) : NestedDraggable.Controller {
    var isExpanding = false

    var currentHeight = targetView.actualHeight.toFloat()
    val collapsedHeight = targetView.collapsedHeight.toFloat()
    val expandedHeight = targetView.maxContentHeight.toFloat()

    var totalDrag: Float = 0f
    var hasPopped = false

    // Latch flag to keep the view expanded once it reaches the max height, to prevent upward drags
    // from collapsing it during this gesture.
    var hasFullyExpanded = false

    override fun onDrag(delta: Float): Float {
        debugLog { "onDrag:$delta isExpanding:$isExpanding hasPopped:$hasPopped total:$totalDrag" }

        totalDrag += delta

        if (!isExpanding) {
            val movingDown = delta > 0
            if (movingDown) {
                debugLog { "START expanding" }
                isExpanding = true
                callback.setUserSwipingToExpand(targetView, true)
                callback.expansionStateChanged(true)
            } else {
                debugLog { "Upward drag, not for us" }
                return 0f
            }
        }

        // Theoretical new height based on the drag
        val rawHeight = currentHeight + delta

        // NEW: Latch the state when reaching the expand boundary
        if (rawHeight >= expandedHeight) {
            hasFullyExpanded = true
        }

        // After fully expanded, the new minimum height becomes the expandedHeight,
        // otherwise clamp new height to the limits.
        val newHeight =
            if (hasFullyExpanded) expandedHeight
            else rawHeight.coerceIn(collapsedHeight, expandedHeight)
        val actualDelta = newHeight - currentHeight

        if (!hasPopped && abs(actualDelta) > 0) {
            hasPopped = true
        }
        if (currentHeight != newHeight) {
            targetView.setFinalActualHeight(newHeight.roundToInt())
            currentHeight = newHeight
        }

        debugLog {
            "Expanded to newHeight$newHeight currentHeight:$currentHeight newHeight:$newHeight"
        }

        return actualDelta
    }

    override suspend fun onDragStopped(velocity: Float, awaitFling: suspend () -> Unit): Float {
        debugLog { "onDragStopped expansionStarted:$isExpanding" }

        if (isExpanding) {
            finishExpansionCompose(
                view = targetView,
                callback = callback,
                hasPoppedToExpanded = hasFullyExpanded,
                currentHeight = currentHeight,
                collapsedHeight = collapsedHeight,
                expandedHeight = expandedHeight,
                initialVelocity = velocity,
            )

            isExpanding = false
        }

        return velocity // Consume all to prevent a fling after the animated expansion.
    }

    private suspend fun finishExpansionCompose(
        view: ExpandableView,
        callback: ExpandHelper.Callback,
        hasPoppedToExpanded: Boolean,
        currentHeight: Float,
        collapsedHeight: Float,
        expandedHeight: Float,
        initialVelocity: Float,
    ) {
        val totalChange = expandedHeight - collapsedHeight
        if (totalChange <= 0) return

        val progress = (currentHeight - collapsedHeight) / totalChange

        val shouldExpand =
            when {
                // Keep the view expanded once it reached the max height
                hasPoppedToExpanded -> true

                // Fast fling down -> Expand
                initialVelocity > velocityThresholdPx -> true

                // Fast fling up -> Collapse
                initialVelocity < -velocityThresholdPx -> false

                // Dragging slowly: decide based on the position
                else -> {
                    val draggedFarEnough = (currentHeight - collapsedHeight) > distanceThresholdPx
                    val isMoreThanHalfWay = progress > 0.5f
                    draggedFarEnough || isMoreThanHalfWay
                }
            }

        callback.expansionStateChanged(false)
        val targetHeight = if (shouldExpand) expandedHeight else collapsedHeight

        if (targetHeight != currentHeight) {
            val animatable = Animatable(currentHeight)
            try {
                animatable.animateTo(
                    targetValue = targetHeight,
                    initialVelocity = initialVelocity,
                    animationSpec = spring(),
                ) {
                    view.setFinalActualHeight(value.roundToInt())
                }
            } finally {
                finalizeAnimation(view, callback, shouldExpand)
            }
        } else {
            finalizeAnimation(view, callback, shouldExpand)
        }
    }

    private fun finalizeAnimation(
        view: ExpandableView,
        callback: ExpandHelper.Callback,
        expanded: Boolean,
    ) {
        callback.setUserExpandedChild(view, expanded)
        callback.setUserSwipingToExpand(view, false)
    }
}

private inline fun debugLog(msg: () -> String) {
    if (IS_DEBUG) {
        Log.d(TAG, msg())
    }
}

private const val TAG = "SwipeToExpandNotification"
private const val IS_DEBUG = false
