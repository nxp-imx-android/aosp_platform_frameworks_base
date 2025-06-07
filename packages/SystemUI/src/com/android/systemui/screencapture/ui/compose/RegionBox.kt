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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/** An enum to identify each of the four corners of the rectangle. */
enum class Corner {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

/**
 * A stateful composable that manages the size of a resizable RegionBox.
 *
 * @param initialWidth The initial width of the box.
 * @param initialHeight The initial height of the box.
 * @param onDragEnd A callback function that is invoked with the final width and height when the
 *   user finishes a drag gesture.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionBox(
    initialWidth: Dp,
    initialHeight: Dp,
    onDragEnd: (width: Dp, height: Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The minimum size allowed for the rectangle.
    // TODO(b/422565042): change when its value is finalized.
    val MIN_SIZE = 48.dp

    // The current state of the box.
    var width by remember { mutableStateOf(initialWidth) }
    var height by remember { mutableStateOf(initialHeight) }
    val density = LocalDensity.current

    val onDrag: (dragAmount: Offset, corner: Corner, maxWidth: Dp, maxHeight: Dp) -> Unit =
        { dragAmount, corner, maxWidth, maxHeight ->
            val (dragX, dragY) = dragAmount
            with(density) {
                // Calculate the potential new width and height based on the drag amount.
                // The width and height are increased or decreased by twice the drag amount as the
                // drag is calculated from the center of the box, so we need to add twice the drag
                // amount to account for both sides of the box.
                val newWidth =
                    when (corner) {
                        Corner.TopLeft,
                        Corner.BottomLeft -> width - (dragX.toDp() * 2)
                        Corner.TopRight,
                        Corner.BottomRight -> width + (dragX.toDp() * 2)
                    }
                val newHeight =
                    when (corner) {
                        Corner.TopLeft,
                        Corner.TopRight -> height - (dragY.toDp() * 2)
                        Corner.BottomLeft,
                        Corner.BottomRight -> height + (dragY.toDp() * 2)
                    }

                // The new width and height cannot be smaller than the minimum allowed, or bigger
                // than the screen size.
                width = max(min(newWidth, maxWidth), MIN_SIZE)
                height = max(min(newHeight, maxHeight), MIN_SIZE)
            }
        }

    ResizableRectangle(
        width = width,
        height = height,
        onDrag = onDrag,
        onDragEnd = { onDragEnd(width, height) },
        modifier = modifier,
    )
}

/**
 * A box with border lines and centered corner knobs that can be resized.
 *
 * @param width The current width of the box.
 * @param height The current height of the box.
 * @param onDrag Callback invoked when a knob is dragged.
 * @param onDragEnd Callback invoked when a drag gesture finishes.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
private fun ResizableRectangle(
    width: Dp,
    height: Dp,
    onDrag: (dragAmount: Offset, corner: Corner, maxWidth: Dp, maxHeight: Dp) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The diameter of the resizable knob on each corner of the region box.
    val KNOB_DIAMETER = 8.dp
    // The width of the border stroke around the region box.
    val BORDER_STROKE_WIDTH = 4.dp

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by remember { mutableStateOf(0.dp) }
    var screenHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // The box that contains the whole screen.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // .onSizeChanged gives us the final size of this box, which is the screen size,
                // after it has been drawn.
                .onSizeChanged { sizeInPixels ->
                    screenWidth = with(density) { sizeInPixels.width.toDp() }
                    screenHeight = with(density) { sizeInPixels.height.toDp() }
                },
        contentAlignment = Alignment.Center,
    ) {
        // The box container for the region box and its knobs.
        Box(modifier = Modifier.size(width, height)) {
            // The main box for the region selection.
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .border(BORDER_STROKE_WIDTH, MaterialTheme.colorScheme.onSurfaceVariant)
            )

            // The offset is half of the knob diameter so that it is centered.
            val knobOffset = KNOB_DIAMETER / 2

            // Top left knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .offset(x = -knobOffset, y = -knobOffset)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount, Corner.TopLeft, screenWidth, screenHeight)
                                },
                            )
                        },
            )

            // Top right knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = knobOffset, y = -knobOffset)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount, Corner.TopRight, screenWidth, screenHeight)
                                },
                            )
                        },
            )

            // Bottom left knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .offset(x = -knobOffset, y = knobOffset)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount, Corner.BottomLeft, screenWidth, screenHeight)
                                },
                            )
                        },
            )

            // Bottom right knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .offset(x = knobOffset, y = knobOffset)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(
                                        dragAmount,
                                        Corner.BottomRight,
                                        screenWidth,
                                        screenHeight,
                                    )
                                },
                            )
                        },
            )
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
