/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.communal.ui.compose

import android.util.MathUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastIsFinite
import androidx.compose.ui.zIndex
import com.android.compose.modifiers.thenIf
import com.android.internal.R.dimen.system_app_widget_background_radius
import com.android.systemui.Flags.communalEditModeAccessibilityResize
import com.android.systemui.communal.ui.compose.extensions.observeTaps
import com.android.systemui.communal.ui.model.AccessibilityResizeAction
import com.android.systemui.communal.ui.viewmodel.ResizeHandle
import com.android.systemui.communal.ui.viewmodel.ResizeInfo
import com.android.systemui.communal.ui.viewmodel.ResizeableItemFrameViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

private val AccessibilityResizeButtonSize = 48.dp
private const val IconAnimationDelayThreshold = 0.5f

private val MedLowBounceSpring = spring<Float>(stiffness = 400f, dampingRatio = 0.7f)
private val MedLowNoBounceSpring = spring<Float>(stiffness = 400f, dampingRatio = 1f)
private val NoBounceFastSpring = spring<Float>(stiffness = 10000f, dampingRatio = 1f)

private val HighStiffnessSpring = spring<Float>(stiffness = Spring.StiffnessHigh, dampingRatio = 1f)
private val HandleFadeSpring = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = 1f)
private val HandleScaleSpring =
    spring<Float>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )

@Composable
private fun UpdateGridLayoutInfo(
    viewModel: ResizeableItemFrameViewModel,
    key: String,
    gridState: LazyGridState,
    gridContentPadding: PaddingValues,
    verticalArrangement: Arrangement.Vertical,
    minHeightPx: Int,
    maxHeightPx: Int,
    resizeMultiple: Int,
    currentSpan: GridItemSpan,
) {
    val density = LocalDensity.current
    LaunchedEffect(
        density,
        viewModel,
        key,
        gridState,
        gridContentPadding,
        verticalArrangement,
        minHeightPx,
        maxHeightPx,
        resizeMultiple,
        currentSpan,
    ) {
        val verticalItemSpacingPx = with(density) { verticalArrangement.spacing.toPx() }
        val verticalContentPaddingPx =
            with(density) {
                (gridContentPadding.calculateTopPadding() +
                        gridContentPadding.calculateBottomPadding())
                    .toPx()
            }

        combine(
                snapshotFlow { gridState.layoutInfo.maxSpan },
                snapshotFlow { gridState.layoutInfo.viewportSize.height },
                snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                },
                ::Triple,
            )
            .collectLatest { (maxItemSpan, viewportHeightPx, itemInfo) ->
                viewModel.setGridLayoutInfo(
                    verticalItemSpacingPx = verticalItemSpacingPx,
                    currentRow = itemInfo?.row,
                    maxHeightPx = maxHeightPx,
                    minHeightPx = minHeightPx,
                    currentSpan = currentSpan.currentLineSpan,
                    resizeMultiple = resizeMultiple,
                    totalSpans = maxItemSpan,
                    viewportHeightPx = viewportHeightPx,
                    verticalContentPaddingPx = verticalContentPaddingPx,
                )
            }
    }
}

@Composable
private fun AccessibilityResizeButtonWrapper(
    visible: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isTopRounded: Boolean,
) {
    Box(modifier = Modifier.height(AccessibilityResizeButtonSize)) {
        val transformOrigin =
            if (isTopRounded) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)

        // Use a MutableTransitionState to ensure the enter animation runs when the button is first
        // displayed, as AnimatedVisibility typically defaults to no animation for the initial state.
        val visibleState = remember { MutableTransitionState(false) }
        visibleState.targetState = visible

        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                scaleIn(
                    animationSpec = MedLowBounceSpring,
                    initialScale = 0.5f,
                    transformOrigin = transformOrigin,
                ) + fadeIn(animationSpec = MedLowNoBounceSpring),
            exit =
                scaleOut(
                    animationSpec = MedLowBounceSpring,
                    targetScale = 0.5f,
                    transformOrigin = transformOrigin,
                ) + fadeOut(animationSpec = MedLowNoBounceSpring),
        ) {
            ResizeButton(
                icon = icon,
                contentDescription = contentDescription,
                onClick = onClick,
                isTopRounded = isTopRounded,
            )
        }
    }
}

@Composable
private fun BoxScope.AccessibilityButtons(
    handle: ResizeHandle,
    isAccessibilityControlsVisible: Boolean,
    canExpand: Boolean,
    canShrink: Boolean,
    onExpand: () -> Unit,
    onShrink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = 8.dp
    val view = LocalView.current
    val expandAnnouncement = stringResource(R.string.accessibility_announcement_expand_widget)
    val shrinkAnnouncement = stringResource(R.string.accessibility_announcement_shrink_widget)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val expandAction =
            AccessibilityResizeAction(
                isVisible = canExpand,
                icon = Icons.Default.Add,
                description = stringResource(R.string.accessibility_action_label_expand_widget),
                onClick = {
                    if (isAccessibilityControlsVisible) {
                        onExpand()
                        view.announceForAccessibility(expandAnnouncement)
                    }
                },
            )
        val shrinkAction =
            AccessibilityResizeAction(
                isVisible = canShrink,
                icon = Icons.Default.Remove,
                description = stringResource(R.string.accessibility_action_label_shrink_widget),
                onClick = {
                    if (isAccessibilityControlsVisible) {
                        onShrink()
                        view.announceForAccessibility(shrinkAnnouncement)
                    }
                },
            )

        val actions =
            if (handle == ResizeHandle.TOP) {
                listOf(expandAction, shrinkAction)
            } else {
                listOf(shrinkAction, expandAction)
            }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            actions.forEachIndexed { index, action ->
                AccessibilityResizeButtonWrapper(
                    visible = action.isVisible,
                    icon = action.icon,
                    contentDescription = action.description,
                    onClick = action.onClick,
                    isTopRounded = index == 0,
                )
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.ResizeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isTopRounded: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape =
        remember(isTopRounded) {
            if (isTopRounded) {
                RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)
            } else {
                RoundedCornerShape(bottomStartPercent = 50, bottomEndPercent = 50)
            }
        }

    val progressionState =
        transition.animateFloat(
            transitionSpec = { MedLowNoBounceSpring },
            label = "ResizeButtonIconProgression",
        ) { state: EnterExitState ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

    val borderColor = MaterialTheme.colorScheme.onTertiaryContainer
    val border = remember(borderColor) { BorderStroke(2.dp, borderColor) }

    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        modifier = modifier.size(AccessibilityResizeButtonSize),
        shape = shape,
        border = border,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier =
                Modifier.graphicsLayer {
                    val progression = progressionState.value
                    val iconProgression =
                        (progression - IconAnimationDelayThreshold).coerceAtLeast(0f) /
                            (1f - IconAnimationDelayThreshold)
                    val iconScale = MathUtils.lerp(0.5f, 1.0f, iconProgression)
                    val iconAlpha = iconProgression

                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = iconAlpha
                },
        )
    }
}

@Immutable
private data class ResizeHandleUiState(
    val orientation: ResizeHandle,
    val isAccessibilityControlsVisible: Boolean,
    val canExpand: Boolean,
    val canShrink: Boolean,
)

@Composable
private fun Modifier.accessibilityResizeClickable(
    label: String,
    onClick: () -> Unit,
): Modifier {
    return this.pointerInput(Unit) { observeTaps(shouldConsume = true) { onClick() } }
        .semantics {
            role = Role.Button
            onClick(label = label) {
                onClick()
                true
            }
        }
}

@Composable
private fun DragPill(brush: Brush, radius: Dp, alpha: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            brush = brush,
            radius = radius.toPx(),
            center = Offset(size.width / 2, size.height / 2),
            alpha = alpha(),
        )
    }
}

@Composable
private fun BoxScope.UnifiedResizeHandle(
    state: ResizeHandleUiState,
    draggableState: AnchoredDraggableState<Int>,
    onToggleAccessibilityControls: () -> Unit,
    onExpand: () -> Unit,
    onShrink: () -> Unit,
    dragHandleRadius: Dp,
    outlinePadding: Dp,
    brush: Brush,
    contentAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val directionalModifier = if (state.orientation == ResizeHandle.TOP) -1 else 1
    val currentOnToggle by rememberUpdatedState(onToggleAccessibilityControls)

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    translationY =
                        directionalModifier * (size.height / 2 + outlinePadding.toPx()) +
                            (draggableState.offset.takeIf { it.fastIsFinite() } ?: 0f)
                }
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = !state.isAccessibilityControlsVisible,
                )
    ) {
        if (communalEditModeAccessibilityResize()) {
            if (!state.canExpand && !state.canShrink) return@Box

            val transition =
                updateTransition(
                    targetState = state.isAccessibilityControlsVisible,
                    label = "ResizeHandleMode",
                )

            val transformOriginY = if (state.orientation == ResizeHandle.TOP) 1f else 0f
            val dragHandleTransformOrigin = TransformOrigin(0.5f, transformOriginY)

            val dragPillAlpha by
                transition.animateFloat(
                    transitionSpec = { if (targetState) HighStiffnessSpring else HandleFadeSpring },
                    label = "dragPillAlpha",
                ) { isVisible ->
                    if (isVisible) 0f else 1f
                }

            val dragPillScale by
                transition.animateFloat(
                    transitionSpec = { if (targetState) NoBounceFastSpring else HandleScaleSpring },
                    label = "dragPillScale",
                ) { isVisible ->
                    if (isVisible) 0.33f else 1f
                }

            val accessibilityButtonsAlpha by
                transition.animateFloat(
                    transitionSpec = { if (targetState) HandleFadeSpring else HighStiffnessSpring },
                    label = "accessibilityButtonsAlpha",
                ) { isVisible ->
                    if (isVisible) 1f else 0f
                }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (!state.isAccessibilityControlsVisible || !transition.currentState) {
                    Box(
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = dragPillAlpha
                                scaleX = dragPillScale
                                scaleY = dragPillScale
                                transformOrigin = dragHandleTransformOrigin
                            }
                    ) {
                        DragPill(
                            brush = brush,
                            radius = dragHandleRadius,
                            alpha = { dragPillAlpha * contentAlpha() },
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(dragHandleRadius * 2)
                                    .accessibilityResizeClickable(
                                        label = stringResource(R.string.accessibility_action_label_resize_widget),
                                        onClick = currentOnToggle,
                                    ),
                        )
                    }
                }

                if (state.isAccessibilityControlsVisible || transition.currentState) {
                    Box(modifier = Modifier.graphicsLayer { alpha = accessibilityButtonsAlpha }) {
                        AccessibilityButtons(
                            handle = state.orientation,
                            isAccessibilityControlsVisible = state.isAccessibilityControlsVisible,
                            canExpand = state.canExpand,
                            canShrink = state.canShrink,
                            onExpand = onExpand,
                            onShrink = onShrink,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            // Replicate the original DragHandle behavior when the flag is off.
            if (draggableState.anchors.size > 1) {
                DragPill(
                    brush = brush,
                    radius = dragHandleRadius,
                    alpha = contentAlpha,
                    modifier = Modifier.fillMaxWidth().height(dragHandleRadius * 2),
                )
            }
        }
    }
}

/**
 * Draws a frame around the content with drag handles on the top and bottom of the content.
 *
 * @param key The unique key of this element, must be the same key used in the [LazyGridState].
 * @param currentSpan The current span size of this item in the grid.
 * @param gridState The [LazyGridState] for the grid containing this item.
 * @param gridContentPadding The content padding used for the grid, needed for determining offsets.
 * @param verticalArrangement The vertical arrangement of the grid items.
 * @param modifier Optional modifier to apply to the frame.
 * @param enabled Whether resizing is enabled.
 * @param outlinePadding The padding to apply around the entire frame, in [Dp]
 * @param outlineColor Optional color to make the outline around the content.
 * @param cornerRadius Optional radius to give to the outline around the content.
 * @param strokeWidth Optional stroke width to draw the outline with.
 * @param minHeightPx Optional minimum height in pixels that this widget can be resized to.
 * @param maxHeightPx Optional maximum height in pixels that this widget can be resized to.
 * @param resizeMultiple Optional number of spans that we allow resizing by. For example, if set to
 *   3, then we only allow resizing in multiples of 3 spans.
 * @param alpha Optional function to provide an alpha value for the outline. Can be used to fade the
 *   outline in and out. This is wrapped in a function for performance, as the value is only
 *   accessed during the draw phase.
 * @param onResize Optional callback which gets executed when the item is resized to a new span.
 * @param content The content to draw inside the frame.
 */
@Composable
fun ResizableItemFrame(
    key: String,
    currentSpan: GridItemSpan,
    gridState: LazyGridState,
    gridContentPadding: PaddingValues,
    verticalArrangement: Arrangement.Vertical,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragHandleRadius: Dp = 8.dp,
    outlinePadding: Dp = 0.dp,
    outlineColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = dimensionResource(system_app_widget_background_radius),
    strokeWidth: Dp = 4.dp,
    minHeightPx: Int = 0,
    maxHeightPx: Int = Int.MAX_VALUE,
    resizeMultiple: Int = 1,
    alpha: () -> Float = { 1f },
    viewModel: ResizeableItemFrameViewModel,
    onResize: (info: ResizeInfo) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val accessibilityResizeHandle by viewModel.visibleAccessibilityResizeHandle

    // When resizing is disabled (e.g. the widget is deselected), ensure we reset the state of
    // the accessibility resize controls.
    LaunchedEffect(enabled) {
        if (!enabled) {
            viewModel.clearAccessibilityResizeHandle()
        }
    }

    val brush = SolidColor(outlineColor)
    val onResizeUpdated by rememberUpdatedState(onResize)
    val isDragging by
        remember(viewModel) {
            derivedStateOf {
                val topOffset = viewModel.topResizeState.offset.takeIf { it.fastIsFinite() } ?: 0f
                val bottomOffset =
                    viewModel.bottomResizeState.offset.takeIf { it.fastIsFinite() } ?: 0f
                topOffset > 0 || bottomOffset > 0
            }
        }

    // Draw content surrounded by resize handles at top and bottom. Allow resize handles
    // to overlap content.
    Box(
        modifier
            .thenIf(communalEditModeAccessibilityResize()) { Modifier.fillMaxSize() }
            .thenIf(
                isDragging ||
                    (communalEditModeAccessibilityResize() && accessibilityResizeHandle != null)
            ) {
                Modifier.zIndex(1f)
            }
    ) {
        content()

        if (enabled) {
            listOf(ResizeHandle.TOP, ResizeHandle.BOTTOM).forEach { handle ->
                val uiState =
                    ResizeHandleUiState(
                        orientation = handle,
                        isAccessibilityControlsVisible =
                            communalEditModeAccessibilityResize() &&
                                accessibilityResizeHandle == handle,
                        canExpand = viewModel.canExpand(handle),
                        canShrink = viewModel.canShrink(handle),
                    )
                val draggableState =
                    if (handle == ResizeHandle.TOP) viewModel.topResizeState
                    else viewModel.bottomResizeState

                UnifiedResizeHandle(
                    state = uiState,
                    draggableState = draggableState,
                    onToggleAccessibilityControls = {
                        if (communalEditModeAccessibilityResize()) {
                            viewModel.toggleAccessibilityResizeHandle(handle)
                        }
                    },
                    onExpand = { viewModel.expand(handle) },
                    onShrink = { viewModel.shrink(handle) },
                    modifier =
                        Modifier.align(
                            if (handle == ResizeHandle.TOP) Alignment.TopCenter
                            else Alignment.BottomCenter
                        ),
                    dragHandleRadius = dragHandleRadius,
                    outlinePadding = outlinePadding,
                    brush = brush,
                    contentAlpha = alpha,
                )
            }

            // Draw outline around the element.
            Canvas(modifier = Modifier.matchParentSize()) {
                val paddingPx = outlinePadding.toPx()
                val topOffset = viewModel.topResizeState.offset.takeIf { it.fastIsFinite() } ?: 0f
                val bottomOffset =
                    viewModel.bottomResizeState.offset.takeIf { it.fastIsFinite() } ?: 0f
                drawRoundRect(
                    brush,
                    alpha = alpha(),
                    topLeft = Offset(-paddingPx, topOffset + -paddingPx),
                    size =
                        Size(
                            width = size.width + paddingPx * 2,
                            height = -topOffset + bottomOffset + size.height + paddingPx * 2,
                        ),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = strokeWidth.toPx()),
                )
            }

            UpdateGridLayoutInfo(
                viewModel = viewModel,
                key = key,
                gridState = gridState,
                currentSpan = currentSpan,
                gridContentPadding = gridContentPadding,
                verticalArrangement = verticalArrangement,
                minHeightPx = minHeightPx,
                maxHeightPx = maxHeightPx,
                resizeMultiple = resizeMultiple,
            )
            LaunchedEffect(viewModel) {
                viewModel.resizeInfo.collectLatest { info -> onResizeUpdated(info) }
            }
        }
    }
}
