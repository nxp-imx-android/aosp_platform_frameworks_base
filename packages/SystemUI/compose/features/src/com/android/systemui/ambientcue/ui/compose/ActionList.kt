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

package com.android.systemui.ambientcue.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapping.SnapPosition.End
import androidx.compose.foundation.gestures.snapping.SnapPosition.Start
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ActionList(
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val density = LocalDensity.current
    val minOverscrollDelta = (-8).dp
    val maxOverscrollDelta = 0.dp
    val columnSpacing = 8.dp
    val topPadding = 32.dp
    val minGradientHeight = 70.dp
    val edgeAligned = horizontalAlignment == Alignment.Start || horizontalAlignment == Alignment.End
    val smartScrimAlpha by
        animateFloatAsState(
            if (expanded) {
                0.25f
            } else if (visible && !edgeAligned) {
                0.1f
            } else {
                0f
            }
        )

    val scaleStiffnessMultiplier = 1000
    val scaleDampingRatio = 0.83f
    val translateStiffnessMultiplier = 50
    val overscrollStiffness = 2063f
    var containerHeightPx by remember { mutableIntStateOf(0) }

    val leftGradientColor = MaterialTheme.colorScheme.tertiary
    val rightGradientColor = MaterialTheme.colorScheme.primaryFixedDim

    // User should be able to drag down vertically to dismiss the action list.
    // The list will shrink as the user drags.
    val anchoredDraggableState = remember {
        AnchoredDraggableState(initialValue = if (visible && expanded) End else Start)
    }
    val minOverscrollDeltaPx = with(density) { minOverscrollDelta.toPx() }
    val maxOverscrollDeltaPx = with(density) { maxOverscrollDelta.toPx() }
    val columnSpacingPx = with(density) { columnSpacing.toPx() }
    val minGradientHeightPx = with(density) { minGradientHeight.toPx() }

    val scope = rememberCoroutineScope()
    val overscrollEffect = remember {
        OverscrollEffect(
            scope = scope,
            orientation = Orientation.Vertical,
            minOffset = minOverscrollDeltaPx,
            maxOffset = maxOverscrollDeltaPx,
            flingAnimationSpec =
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = overscrollStiffness),
        )
    }
    // A ratio from 0..1 representing the expansion of the list
    val progress by remember {
        derivedStateOf {
            // We combine the anchor offset with the overscroll offset to animate
            abs(anchoredDraggableState.offset + overscrollEffect.offset.value) /
                max(1, containerHeightPx)
        }
    }
    LaunchedEffect(progress) {
        if (progress == 0f) {
            onDismiss()
        }
    }

    val scrimProgress by
        animateFloatAsState(
            progress,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )

    LaunchedEffect(visible, expanded) {
        anchoredDraggableState.animateTo(if (visible && expanded) End else Start)
    }

    Column(
        modifier =
            modifier
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical,
                    enabled = expanded,
                    overscrollEffect = overscrollEffect,
                )
                .onGloballyPositioned { layoutCoordinates ->
                    containerHeightPx = layoutCoordinates.size.height
                    anchoredDraggableState.updateAnchors(
                        DraggableAnchors {
                            Start at 0f // Hidden
                            End at -containerHeightPx.toFloat() // Visible
                        }
                    )
                }
                .drawBehind {
                    val sidePaddingPx =
                        with(density) { padding.calculateLeftPadding(layoutDirection).toPx() }
                    val radius = size.width - sidePaddingPx * 2f
                    val minScaleY = minGradientHeightPx / size.height
                    val scaleY = max(minScaleY, size.height / (radius * 2f) * scrimProgress)

                    if (!(radius > 0)) return@drawBehind

                    scale(scaleX = 1f, scaleY = scaleY, pivot = Offset(0f, size.height)) {
                        val leftGradientCenter =
                            Offset(size.width / 2 + sidePaddingPx, size.height - radius)
                        val rightGradientCenter =
                            Offset(size.width / 2 - sidePaddingPx, size.height - radius)
                        val leftBrush =
                            Brush.radialGradient(
                                colors =
                                    listOf(leftGradientColor, leftGradientColor.copy(alpha = 0f)),
                                center = rightGradientCenter,
                                radius = radius,
                            )
                        val rightBrush =
                            Brush.radialGradient(
                                colors =
                                    listOf(rightGradientColor, rightGradientColor.copy(alpha = 0f)),
                                center = leftGradientCenter,
                                radius = radius,
                            )
                        drawCircle(
                            brush = rightBrush,
                            alpha = smartScrimAlpha,
                            radius = radius,
                            center = leftGradientCenter,
                        )
                        drawCircle(
                            brush = leftBrush,
                            alpha = smartScrimAlpha,
                            radius = radius,
                            center = rightGradientCenter,
                        )
                    }
                }
                .padding(top = topPadding)
                .padding(padding),
        verticalArrangement = Arrangement.spacedBy(columnSpacing, Alignment.Bottom),
        horizontalAlignment = horizontalAlignment,
    ) {
        val childHeights = remember { MutableList(actions.size) { 0 } }
        actions.forEachIndexed { index, action ->
            val scale by
                animateFloatAsState(
                    progress,
                    animationSpec =
                        spring(
                            dampingRatio = scaleDampingRatio,
                            stiffness =
                                Spring.StiffnessLow + index * index * scaleStiffnessMultiplier,
                        ),
                )
            val translation by
                animateFloatAsState(
                    progress,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness =
                                Spring.StiffnessLow + index * index * translateStiffnessMultiplier,
                        ),
                )

            var appxColumnY by remember(childHeights) { mutableFloatStateOf(0f) }
            LaunchedEffect(childHeights) {
                appxColumnY =
                    childHeights.subList(index, childHeights.size).sum() +
                        columnSpacingPx * max((childHeights.size - index - 1f), 0f)
            }

            Chip(
                action = action,
                modifier =
                    Modifier.onSizeChanged { childHeights[index] = it.height }
                        .graphicsLayer {
                            translationY = (1f - translation) * appxColumnY
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
            )
        }
    }
}
