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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import android.graphics.Rect as IntRect
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The different modes of interaction that the user can have with the RegionBox.
private enum class DragMode {
    DRAWING,
    MOVING,
    RESIZING,
    NONE,
}

/**
 * Determines which zone (corner or edge) of a box is being touched based on the press offset.
 *
 * @param boxWidth The total width of the box.
 * @param boxHeight The total height of the box.
 * @param startOffset The position of the initial press.
 * @param touchAreaPx The size of the touch area in pixels.
 * @return The ResizeZone that was pressed, or `null` if the press was not on a zone.
 */
private fun getTouchedZone(
    boxWidth: Float,
    boxHeight: Float,
    startOffset: Offset,
    touchAreaPx: Float,
): ResizeZone? {
    // Check if the touch is within the touch area of the box.
    val touchedZone =
        Rect(
            left = -touchAreaPx,
            top = -touchAreaPx,
            right = boxWidth + touchAreaPx,
            bottom = boxHeight + touchAreaPx,
        )
    if (!touchedZone.contains(startOffset)) {
        return null
    }

    val isTouchingTop = startOffset.y in -touchAreaPx..touchAreaPx
    val isTouchingBottom = startOffset.y in (boxHeight - touchAreaPx)..(boxHeight + touchAreaPx)
    val isTouchingLeft = startOffset.x in -touchAreaPx..touchAreaPx
    val isTouchingRight = startOffset.x in (boxWidth - touchAreaPx)..(boxWidth + touchAreaPx)

    return when {
        // Corners have priority over edges, as they occupy overlapping areas.
        isTouchingTop && isTouchingLeft -> ResizeZone.Corner.TopLeft
        isTouchingTop && isTouchingRight -> ResizeZone.Corner.TopRight
        isTouchingBottom && isTouchingLeft -> ResizeZone.Corner.BottomLeft
        isTouchingBottom && isTouchingRight -> ResizeZone.Corner.BottomRight

        // If not a corner, check for edges.
        isTouchingLeft -> ResizeZone.Edge.Left
        isTouchingTop -> ResizeZone.Edge.Top
        isTouchingRight -> ResizeZone.Edge.Right
        isTouchingBottom -> ResizeZone.Edge.Bottom

        else -> null
    }
}

/**
 * A class that encapsulates the state and logic for the RegionBox composable.
 *
 * @param minSizePx The minimum size of the box in pixels.
 * @param touchAreaPx The size of the touch area for resizing in pixels.
 */
private class RegionBoxState(private val minSizePx: Float, private val touchAreaPx: Float) {
    var rect by mutableStateOf<Rect?>(null)
        private set

    private var dragMode by mutableStateOf(DragMode.NONE)
    private var resizeZone by mutableStateOf<ResizeZone?>(null)

    // The offset of the initial press when the user starts a drag gesture.
    private var newBoxStartOffset by mutableStateOf(Offset.Zero)

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by mutableStateOf(0f)
    var screenHeight by mutableStateOf(0f)

    fun startDrag(startOffset: Offset) {
        val currentRect = rect

        if (currentRect == null) {
            // If the box is not yet created, it is a drawing drag.
            dragMode = DragMode.DRAWING
            newBoxStartOffset = startOffset
        } else {
            // The offset of the existing box.
            val currentRectOffset = startOffset - currentRect.topLeft
            val touchedZone =
                getTouchedZone(
                    currentRect.width,
                    currentRect.height,
                    currentRectOffset,
                    touchAreaPx,
                )
            when {
                touchedZone != null -> {
                    // If the drag was initiated within the current rectangle's drag-to-resize touch
                    // zone, it is a resizing drag.
                    dragMode = DragMode.RESIZING
                    resizeZone = touchedZone
                }
                currentRect.contains(startOffset) -> {
                    // If the drag was initiated inside the rectangle and not within the touch
                    // zones, it is a moving drag.
                    dragMode = DragMode.MOVING
                }
                else -> {
                    // The touch was initiated outside of the rectangle and its touch zone.
                    dragMode = DragMode.DRAWING
                    newBoxStartOffset = startOffset
                }
            }
        }
    }

    fun drag(endOffset: Offset, dragAmount: Offset) {
        val currentRect = rect
        when (dragMode) {
            DragMode.DRAWING -> {
                // Ensure that the box remains within the boundaries of the screen.
                val newBoxEndOffset =
                    Offset(
                        x = endOffset.x.coerceIn(0f, screenWidth),
                        y = endOffset.y.coerceIn(0f, screenHeight),
                    )
                rect =
                    Rect(
                        left = min(newBoxStartOffset.x, newBoxEndOffset.x),
                        top = min(newBoxStartOffset.y, newBoxEndOffset.y),
                        right = max(newBoxStartOffset.x, newBoxEndOffset.x),
                        bottom = max(newBoxStartOffset.y, newBoxEndOffset.y),
                    )
            }
            DragMode.MOVING -> {
                if (currentRect != null) {
                    val newOffset = currentRect.topLeft + dragAmount

                    // Constrain the new position within the parent's boundaries
                    val constrainedLeft = newOffset.x.coerceIn(0f, screenWidth - currentRect.width)
                    val constrainedTop = newOffset.y.coerceIn(0f, screenHeight - currentRect.height)

                    rect =
                        currentRect.translate(
                            translateX = constrainedLeft - currentRect.left,
                            translateY = constrainedTop - currentRect.top,
                        )
                }
            }
            DragMode.RESIZING -> {
                if (currentRect != null && resizeZone != null) {
                    rect =
                        resizeZone!!.processResizeDrag(
                            currentRect,
                            dragAmount,
                            minSizePx,
                            screenWidth,
                            screenHeight,
                        )
                }
            }
            DragMode.NONE -> {
                // Do nothing.
            }
        }
    }

    fun dragEnd() {
        dragMode = DragMode.NONE
        resizeZone = null
    }
}

/**
 * A composable that allows the user to create, move, resize, and redraw a rectangular region.
 *
 * @param onRegionSelected A callback function that is invoked with the final rectangle when the
 *   user finishes a drag gesture. This rectangle is used for taking a screenshot. The rectangle is
 *   of type [android.graphics.Rect] because the screenshot API requires int values.
 * @param drawableLoaderViewModel The view model that is used to load drawables.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionBox(
    onRegionSelected: (rect: IntRect) -> Unit,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // The minimum size allowed for the box.
    val minSize = 1.dp
    val minSizePx = remember(density) { with(density) { minSize.toPx() } }

    // The touch area for detecting an edge or corner resize drag.
    val touchArea = 48.dp
    val touchAreaPx = remember(density) { with(density) { touchArea.toPx() } }

    val state = remember { RegionBoxState(minSizePx, touchAreaPx) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // .onSizeChanged gives us the final size of this box, which is the screen size,
                // after it has been drawn.
                .onSizeChanged { sizeInPixels: IntSize ->
                    state.screenWidth = sizeInPixels.width.toFloat()
                    state.screenHeight = sizeInPixels.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset: Offset -> state.startDrag(startOffset) },
                        onDrag = { change: PointerInputChange, dragAmount: Offset ->
                            change.consume()
                            state.drag(change.position, dragAmount)
                        },
                        onDragEnd = {
                            state.dragEnd()
                            state.rect?.let { rect: Rect ->
                                // Store the rectangle to the ViewModel for taking a screenshot.
                                // The screenshot API requires a Rect class with int values.
                                onRegionSelected(
                                    IntRect(
                                        rect.left.roundToInt(),
                                        rect.top.roundToInt(),
                                        rect.right.roundToInt(),
                                        rect.bottom.roundToInt(),
                                    )
                                )
                            }
                        },
                        onDragCancel = { state.dragEnd() },
                    )
                }
    ) {
        // The width of the border stroke around the region box.
        val borderStrokeWidth = 4.dp

        state.rect?.let { currentRect ->
            val boxWidthDp = with(density) { currentRect.width.toDp() }
            val boxHeightDp = with(density) { currentRect.height.toDp() }

            // The box that represents the region.
            Box(
                modifier =
                    Modifier.graphicsLayer(
                            translationX = currentRect.left,
                            translationY = currentRect.top,
                        )
                        .size(width = boxWidthDp, height = boxHeightDp)
                        .border(borderStrokeWidth, MaterialTheme.colorScheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {}

            // The screenshot button that is positioned inside or outside the region box.
            RegionScreenshotButton(
                boxWidthDp,
                boxHeightDp,
                currentRect,
                drawableLoaderViewModel = drawableLoaderViewModel,
            )
        }
    }
}
