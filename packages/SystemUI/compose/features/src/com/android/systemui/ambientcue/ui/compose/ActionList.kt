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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.padding
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ActionList(
    actions: List<ActionViewModel>,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val density = LocalDensity.current
    val minOverscrollDelta = (-8).dp
    val maxOverscrollDelta = 0.dp
    val columnSpacing = 8.dp

    val scaleStiffnessMultiplier = 1000
    val scaleDampingRatio = 0.83f
    val translateStiffnessMultiplier = 50
    val overscrollStiffness = 2063f
    var containerHeightPx by remember { mutableIntStateOf(0) }

    // User should be able to drag down vertically to dismiss the action list.
    // The list will shrink as the user drags.
    val anchoredDraggableState = remember {
        AnchoredDraggableState(initialValue = if (visible) End else Start)
    }
    val minOverscrollDeltaPx = with(density) { minOverscrollDelta.toPx() }
    val maxOverscrollDeltaPx = with(density) { maxOverscrollDelta.toPx() }
    val columnSpacingPx = with(LocalDensity.current) { columnSpacing.toPx() }

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

    LaunchedEffect(visible) { anchoredDraggableState.animateTo(if (visible) End else Start) }

    Column(
        modifier =
            modifier
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical,
                    enabled = visible,
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
                .defaultMinSize(minHeight = 200.dp)
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(columnSpacing, Alignment.Bottom),
        horizontalAlignment = horizontalAlignment,
    ) {
        val childHeights = remember(actions) { MutableList(actions.size) { 0 } }
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
                    Modifier.then(
                            if (index == actions.size - 1) Modifier.padding(bottom = 16.dp)
                            else Modifier
                        )
                        .onSizeChanged { childHeights[index] = it.height }
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
