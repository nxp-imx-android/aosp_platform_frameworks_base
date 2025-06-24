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

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.res.R

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
 * Determines which corner of a box is being touched based on the press offset.
 *
 * @param boxWidth The total width of the box.
 * @param boxHeight The total height of the box.
 * @param startOffset The position of the initial press.
 * @param touchAreaPx The size of the touch area in pixels.
 * @return The Corner that was touched, or `null` if the press was not in a corner.
 */
private fun getDraggedCorner(
    boxWidth: Float,
    boxHeight: Float,
    startOffset: Offset,
    touchAreaPx: Float,
): Corner? {
    val isTouchingTop = startOffset.y in -touchAreaPx..touchAreaPx
    val isTouchingBottom = startOffset.y in (boxHeight - touchAreaPx)..boxHeight
    val isTouchingLeft = startOffset.x in -touchAreaPx..touchAreaPx
    val isTouchingRight = startOffset.x in (boxWidth - touchAreaPx)..boxWidth

    return when {
        isTouchingTop && isTouchingLeft -> Corner.TopLeft
        isTouchingTop && isTouchingRight -> Corner.TopRight
        isTouchingBottom && isTouchingLeft -> Corner.BottomLeft
        isTouchingBottom && isTouchingRight -> Corner.BottomRight
        else -> null
    }
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
    // The minimum size allowed for the box.
    val minSize = 1.dp

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
 * A box with a border that can be resized by dragging its corners and moved by dragging its body.
 *
 * @param rect The current geometry of the region box.
 * @param onCornerDrag Callback invoked when a corner is dragged.
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
    // TODO(b/422855266): Preload icons in the view model to avoid loading icons in UI thread and
    // improve performance
    val screenshotIcon =
        IconModel.Resource(res = R.drawable.ic_screen_capture_camera, contentDescription = null)

    // The width of the border stroke around the region box.
    val borderStrokeWidth = 4.dp
    // The touch area for detecting a corner resize drag.
    val cornerTouchArea = 48.dp

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val cornerTouchAreaPx = with(density) { cornerTouchArea.toPx() }

    // State of the corner, can be either dragged or null.
    var draggedCorner by remember { mutableStateOf<Corner?>(null) }

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
        Box(
            modifier =
                Modifier.graphicsLayer(translationX = rect.left, translationY = rect.top)
                    .size(
                        width = with(density) { rect.width.toDp() },
                        height = with(density) { rect.height.toDp() },
                    )
                    .border(borderStrokeWidth, MaterialTheme.colorScheme.onSurfaceVariant)
                    .pointerInput(screenWidth, screenHeight, onCornerDrag, onBoxDrag, onDragEnd) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                draggedCorner =
                                    getDraggedCorner(
                                        boxWidth = size.width.toFloat(),
                                        boxHeight = size.height.toFloat(),
                                        startOffset = startOffset,
                                        touchAreaPx = cornerTouchAreaPx,
                                    )
                            },
                            onDragEnd = {
                                draggedCorner = null
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                // Create a stable and local copy of the dragged corner. This
                                // ensures that the value does not change in the onCornerDrag
                                // callback.
                                val currentCorner = draggedCorner

                                if (currentCorner != null) {
                                    // If 'currentCorner' has a value, it means we are dragging a
                                    // corner for resizing.
                                    onCornerDrag(
                                        dragAmount,
                                        currentCorner,
                                        screenWidth,
                                        screenHeight,
                                    )
                                } else {
                                    // If 'currentCorner' is null, it means we are dragging the box.
                                    onBoxDrag(dragAmount, screenWidth, screenHeight)
                                }
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            PrimaryButton(
                text = stringResource(id = R.string.screen_capture_region_selection_button),
                onClick = {
                    // TODO(b/417534202): trigger a screenshot of the selected area.
                },
                icon = screenshotIcon,
            )
        }
    }
}
