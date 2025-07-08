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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel

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
    drawableLoaderViewModel: DrawableLoaderViewModel,
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
        onResizeDrag = { dragAmount, zone, maxWidth, maxHeight ->
            rect = zone.processResizeDrag(rect, dragAmount, minSizePx, maxWidth, maxHeight)
        },
        onBoxDrag = onBoxDrag,
        onDragEnd = {
            onDragEnd(
                Offset(rect.left, rect.top),
                with(density) { rect.width.toDp() },
                with(density) { rect.height.toDp() },
            )
        },
        drawableLoaderViewModel = drawableLoaderViewModel,
        modifier = modifier,
    )
}

/**
 * A box with a border that can be resized by dragging its zone (corner or edge), and moved by
 * dragging its body.
 *
 * @param rect The current geometry of the region box.
 * @param onResizeDrag Callback invoked when a corner or edge is dragged.
 * @param onBoxDrag Callback invoked when the main body of the box is dragged.
 * @param onDragEnd Callback invoked when a drag gesture finishes.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
private fun ResizableRectangle(
    rect: Rect,
    onResizeDrag: (dragAmount: Offset, zone: ResizeZone, maxWidth: Float, maxHeight: Float) -> Unit,
    onBoxDrag: (dragAmount: Offset, maxWidth: Float, maxHeight: Float) -> Unit,
    onDragEnd: () -> Unit,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
) {
    // The width of the border stroke around the region box.
    val borderStrokeWidth = 4.dp
    // The touch area for detecting an edge or corner resize drag.
    val touchArea = 48.dp

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val touchAreaPx = with(density) { touchArea.toPx() }

    // The zone being dragged for resizing, if any.
    var draggedZone by remember { mutableStateOf<ResizeZone?>(null) }

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
                    .pointerInput(screenWidth, screenHeight, onResizeDrag, onBoxDrag, onDragEnd) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                draggedZone =
                                    getTouchedZone(
                                        boxWidth = size.width.toFloat(),
                                        boxHeight = size.height.toFloat(),
                                        startOffset = startOffset,
                                        touchAreaPx = touchAreaPx,
                                    )
                            },
                            onDragEnd = {
                                draggedZone = null
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                // Create a stable and local copy of the draggedZone. This
                                // ensures that the value does not change in the onResizeDrag
                                // callback.
                                val currentZone = draggedZone

                                if (currentZone != null) {
                                    // If currentZone has a value, it means we are dragging a zone
                                    // for resizing.
                                    onResizeDrag(dragAmount, currentZone, screenWidth, screenHeight)
                                } else {
                                    // If currentZone is null, it means we are dragging the box.
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
                icon =
                    loadIcon(
                        viewModel = drawableLoaderViewModel,
                        resId = R.drawable.ic_screen_capture_camera,
                        contentDescription = null,
                    ),
            )
        }
    }
}
