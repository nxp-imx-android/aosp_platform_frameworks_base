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

package com.android.systemui.screencapture.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An enum to identify each of the four corners of the rectangle.
 *
 * @param alignment The alignment of the corner within the box.
 */
enum class Corner(val alignment: Alignment) {
    TopLeft(Alignment.TopStart),
    TopRight(Alignment.TopEnd),
    BottomLeft(Alignment.BottomStart),
    BottomRight(Alignment.BottomEnd),
}

/**
 * A stateful composable that manages the size and position of a resizable RegionBox.
 *
 * @param initialWidth The initial width of the box.
 * @param initialHeight The initial height of the box.
 * @param onDragEnd A callback function that is invoked with the final offset, width, and height
 *   when the user finishes a drag gesture.
 * @param initialOffset The initial top-left offset of the box. Default is (0, 0), which is the
 *   parent's top-left corner.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionBox(
    initialWidth: Dp,
    initialHeight: Dp,
    onDragEnd: (offset: Offset, width: Dp, height: Dp) -> Unit,
    initialOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    // The minimum size allowed for the rectangle.
    // TODO(b/422565042): change when its value is finalized.
    val minSize = 48.dp

    val density = LocalDensity.current
    val minSizePx = remember(density) { with(density) { minSize.toPx() } }

    // State for the region box's geometry.
    var rect by remember {
        mutableStateOf(
            with(density) {
                // offset is how far from the parent's top-left corner the box should be placed.
                Rect(offset = initialOffset, size = Size(initialWidth.toPx(), initialHeight.toPx()))
            }
        )
    }

    val onCornerDrag:
        (dragAmount: Offset, corner: Corner, maxWidth: Float, maxHeight: Float) -> Unit =
        { dragAmount, corner, maxWidth, maxHeight ->
            // Used for calculating the new dimensions based on which corner is dragged.
            var newLeft = rect.left
            var newTop = rect.top
            var newRight = rect.right
            var newBottom = rect.bottom

            val (dragX, dragY) = dragAmount

            // Handle horizontal drag for resizing.
            if (corner == Corner.TopLeft || corner == Corner.BottomLeft) {
                val potentialNewLeft = rect.left + dragX
                val rightLimitForMinWidth = rect.right - minSizePx

                newLeft = potentialNewLeft.coerceIn(0f, rightLimitForMinWidth)
            } else {
                val potentialNewRight = rect.right + dragX
                val leftLimitForMinWidth = rect.left + minSizePx

                newRight = potentialNewRight.coerceIn(leftLimitForMinWidth, maxWidth)
            }

            // Handle vertical drag for resizing.
            if (corner == Corner.TopLeft || corner == Corner.TopRight) {
                val potentialNewTop = rect.top + dragY
                val bottomLimitForMinHeight = rect.bottom - minSizePx

                newTop = potentialNewTop.coerceIn(0f, bottomLimitForMinHeight)
            } else {
                val potentialNewBottom = rect.bottom + dragY
                val topLimitForMinHeight = rect.top + minSizePx

                newBottom = potentialNewBottom.coerceIn(topLimitForMinHeight, maxHeight)
            }

            rect = Rect(newLeft, newTop, newRight, newBottom)
        }

    val onBoxDrag: (dragAmount: Offset, maxWidth: Float, maxHeight: Float) -> Unit =
        { dragAmount, maxWidth, maxHeight ->
            val newOffset = rect.topLeft + dragAmount

            // Constrain the new position within the parent's boundaries
            val constrainedLeft: Float = newOffset.x.coerceIn(0f, maxWidth - rect.width)
            val constrainedTop: Float = newOffset.y.coerceIn(0f, maxHeight - rect.height)

            rect =
                rect.translate(
                    translateX = constrainedLeft - rect.left,
                    translateY = constrainedTop - rect.top,
                )
        }

    ResizableRectangle(
        rect = rect,
        onCornerDrag = onCornerDrag,
        onBoxDrag = onBoxDrag,
        onDragEnd = {
            onDragEnd(
                Offset(rect.left, rect.top),
                with(density) { rect.width.toDp() },
                with(density) { rect.height.toDp() },
            )
        },
        modifier = modifier,
    )
}

/**
 * A box with border lines and centered corner knobs that can be resized and dragged.
 *
 * @param rect The current geometry of the region box.
 * @param onCornerDrag Callback invoked when a corner knob is dragged.
 * @param onBoxDrag Callback invoked when the main body of the box is dragged.
 * @param onDragEnd Callback invoked when a drag gesture finishes.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
private fun ResizableRectangle(
    rect: Rect,
    onCornerDrag: (dragAmount: Offset, corner: Corner, maxWidth: Float, maxHeight: Float) -> Unit,
    onBoxDrag: (dragAmount: Offset, maxWidth: Float, maxHeight: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The diameter of the resizable knob on each corner of the region box.
    val knobDiameter = 8.dp
    // The width of the border stroke around the region box.
    val borderStrokeWidth = 4.dp

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    val density = LocalDensity.current

    // The box that contains the whole screen.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // .onSizeChanged gives us the final size of this box, which is the screen size,
                // after it has been drawn.
                .onSizeChanged { sizeInPixels ->
                    screenWidth = sizeInPixels.width.toFloat()
                    screenHeight = sizeInPixels.height.toFloat()
                }
    ) {
        // The box container for the region box and its knobs.
        Box(
            modifier =
                Modifier.graphicsLayer(translationX = rect.left, translationY = rect.top)
                    .size(
                        width = with(density) { rect.width.toDp() },
                        height = with(density) { rect.height.toDp() },
                    )
        ) {
            // The main box for the region selection.
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .border(borderStrokeWidth, MaterialTheme.colorScheme.onSurfaceVariant)
                        .pointerInput(screenWidth, screenHeight, onBoxDrag, onDragEnd) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onBoxDrag(dragAmount, screenWidth, screenHeight)
                                },
                            )
                        }
            )

            // The offset is half of the knob diameter so that it is centered.
            val knobOffset = knobDiameter / 2

            // Create knobs by looping through the Corner enum values
            Corner.entries.forEach { corner ->
                val xOffset: Dp
                val yOffset: Dp

                if (corner == Corner.TopLeft || corner == Corner.BottomLeft) {
                    xOffset = -knobOffset
                } else {
                    xOffset = knobOffset
                }

                if (corner == Corner.TopLeft || corner == Corner.TopRight) {
                    yOffset = -knobOffset
                } else {
                    yOffset = knobOffset
                }

                Knob(
                    diameter = knobDiameter,
                    modifier =
                        Modifier.align(corner.alignment)
                            .offset(x = xOffset, y = yOffset)
                            .pointerInput(corner, screenWidth, screenHeight, onCornerDrag, onDragEnd) {
                                detectDragGestures(
                                    onDragEnd = onDragEnd,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onCornerDrag(dragAmount, corner, screenWidth, screenHeight)
                                    },
                                )
                            },
                )
            }
        }
    }
}

/**
 * The circular knob on each corner of the box used for dragging each corner.
 *
 * @param diameter The diameter of the knob.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
private fun Knob(diameter: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(diameter)
                .background(color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
    )
}
