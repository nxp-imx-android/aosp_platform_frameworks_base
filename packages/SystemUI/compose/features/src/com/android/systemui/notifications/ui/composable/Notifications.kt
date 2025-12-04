/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.notifications.ui.composable

import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.withoutEventHandling
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOf
import androidx.compose.ui.util.fastMinOf
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.onUnplaced
import com.android.compose.modifiers.thenIf
import com.android.compose.nestedscroll.OnStopScope
import com.android.compose.nestedscroll.PriorityNestedScrollConnection
import com.android.compose.nestedscroll.ScrollController
import com.android.internal.jank.Cuj.CUJ_NOTIFICATION_SHADE_SCROLL_FLING
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.sessionCoroutineScope
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.ShadeColors
import com.android.systemui.statusbar.notification.stack.shared.model.AccessibilityScrollEvent
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimRounding
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrollState
import com.android.systemui.statusbar.notification.stack.ui.YSpace
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_CORNER_RADIUS
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_SCRIM_ALPHA
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

object Notifications {
    object Elements {
        /**
         * The [ElementKey] identifying the rounded rect surface behind Notifications the shade.
         * This surface is fully defined in Compose, so this key can be used to define the Scrim's
         * behaviour during STL transitions.
         */
        val NotificationScrim = ElementKey("NotificationScrim")
        /**
         * The [ElementKey] identifying the space reserved for the main list of notifications. This
         * key only links to an empty box sized to the height of the Stack (placeholder), so STL
         * transitions are not fully supported here, except vertical positioning.
         *
         * If you change the contentPicker of this element, consider also changing
         * [StackPlaceholderContentPicker].
         */
        val StackPlaceholder = ElementKey("StackPlaceholder")
        /**
         * The [ElementKey] identifying the space reserved for the top HUN. This key only links to
         * an empty box sized to the height of Notifications (placeholder), so STL transitions are
         * not fully supported here, except vertical positioning.
         *
         * If you change the contentPicker of this element, consider also changing
         * [HeadsUpPlaceholderContentPicker].
         */
        val HeadsUpNotificationPlaceholder =
            ElementKey("HeadsUpNotificationPlaceholder", contentPicker = LowestZIndexContentPicker)
    }
}

/** Adds the space where notification stack should appear in the scene. */
@Composable
fun ContentScope.ConstrainedNotificationStack(
    stackScrollView: NotificationScrollView,
    sceneContainerLayoutState: SceneTransitionLayoutState,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .onSizeChanged { viewModel.onConstrainedAvailableSpaceChanged(it.height) }
                .onPlaced {
                    val rawBounds = it.rawBoundsInWindow()
                    debugLog(viewModel) { "Constrained.container onPlaced bounds=$rawBounds" }
                    viewModel.setStackBounds(rawBounds)
                }
                .onUnplaced {
                    debugLog(viewModel) { "Constrained.container onUnplaced" }
                    viewModel.resetStackBounds()
                }
    ) {
        StackPlaceholder(
            tag = "Constrained",
            viewModel = viewModel,
            modifier =
                Modifier.fillMaxWidth()
                    .notificationStackHeight(view = stackScrollView, constrainToMaxHeight = true)
                    .onGloballyPositioned { coordinates ->
                        viewModel.onLockScreenStackBottomChanged(
                            coordinates.boundsInWindow().bottom
                        )
                    },
        )
        HeadsUpNotificationPlaceholder(
            tag = "Constrained",
            stackScrollView = stackScrollView,
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Standalone version of the Scrolling Notification Stack. */
@Composable
fun ContentScope.ScrollingNotificationPanel(
    tag: String,
    shadeSession: SaveableSession,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    jankMonitor: InteractionJankMonitor,
    shouldPunchHoleBehindScrim: Boolean,
    isTransparencyEnabled: Boolean,
    stackTopPadding: Dp,
    stackBottomPadding: Dp,
    modifier: Modifier = Modifier,
    shouldFillMaxSize: Boolean = true,
    shouldIncludeHeadsUpSpace: Boolean = true,
    shouldDrawScrimBackground: Boolean = true,
    isActivated: Boolean = true,
    scrollState: ScrollState =
        shadeSession.rememberSaveableSession(saver = ScrollState.Saver, key = "ScrollState") {
            ScrollState(initial = 0)
        },
    overscrollEffect: OffsetOverscrollEffect = rememberOffsetOverscrollEffect(),
    onEmptySpaceClick: (() -> Unit)? = null,
) {
    if (isActivated) {
        val composeViewRoot = LocalView.current
        // whether the stack is moving due to a swipe or fling
        val isScrollInProgress = scrollState.isScrollInProgress || overscrollEffect.isInProgress

        LaunchedEffectWithLifecycle(isScrollInProgress) {
            if (isScrollInProgress) {
                jankMonitor.begin(composeViewRoot, CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
                debugLog(viewModel) { "STACK scroll begins" }
            } else {
                debugLog(viewModel) { "STACK scroll ends" }
                jankMonitor.end(CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
            }
        }

        val shadeScrollState by
            shadeSession.rememberSession(key = "ScrollingNotificationPanelScrollState") {
                derivedStateOf {
                    ShadeScrollState(
                        // we are not scrolled to the top unless the scroll position is zero,
                        isScrolledToTop = scrollState.value == 0,
                        scrollPosition = scrollState.value,
                        maxScrollPosition = scrollState.maxValue,
                    )
                }
            }
        LaunchedEffectWithLifecycle(shadeScrollState) { viewModel.setScrollState(shadeScrollState) }
    }

    NestedScrollingNotificationPanel(
        tag = "$tag.Scrolling",
        shadeSession = shadeSession,
        stackScrollView = stackScrollView,
        viewModel = viewModel,
        modifier = modifier,
        shouldPunchHoleBehindScrim = shouldPunchHoleBehindScrim,
        isTransparencyEnabled = isTransparencyEnabled,
        stackTopPadding = stackTopPadding,
        stackBottomPadding = stackBottomPadding,
        shouldFillMaxSize = shouldFillMaxSize,
        shouldDrawScrimBackground = shouldDrawScrimBackground,
        shouldIncludeHeadsUpSpace = shouldIncludeHeadsUpSpace,
        isActivated = isActivated,
        scrollState = scrollState,
        overscrollEffect = overscrollEffect,
        onEmptySpaceClick = onEmptySpaceClick,
        onStackHeightChanged = { /* no-op without nested scroll */ },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContentScope.NestedScrollingNotificationPanel(
    tag: String,
    shadeSession: SaveableSession,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    shouldPunchHoleBehindScrim: Boolean,
    isTransparencyEnabled: Boolean,
    stackTopPadding: Dp,
    stackBottomPadding: Dp,
    scrollState: ScrollState,
    overscrollEffect: OffsetOverscrollEffect,
    modifier: Modifier = Modifier,
    shouldFillMaxSize: Boolean = true,
    shouldIncludeHeadsUpSpace: Boolean = true,
    shouldDrawScrimBackground: Boolean = true,
    isActivated: Boolean = true,
    onEmptySpaceClick: (() -> Unit)? = null,
    onStackHeightChanged: (Int) -> Unit = {},
) {
    /**
     * Space available for the notification stack on the screen. These bounds don't scroll off the
     * screen, and respect the scrim paddings, scrim clipping.
     */
    val stackBoundsOnScreen = remember { mutableStateOf(Rect.Zero) }

    val nestedScrollDispatcher =
        shadeSession.rememberSession(key = "NestedScrollDispatcher") { NestedScrollDispatcher() }

    // The top y bound of the IME.
    val imeTop = remember { mutableFloatStateOf(0f) }

    if (isActivated) {
        val coroutineScope = shadeSession.sessionCoroutineScope(key = "NotificationScrollingStack")

        // set the bounds to null when the scrim disappears
        DisposableEffectWithLifecycle(Unit) { onDispose { viewModel.onScrimBoundsChanged(null) } }

        val isRemoteInputActive by viewModel.isRemoteInputActive.collectAsStateWithLifecycle(false)

        // The bottom Y bound of the currently focused remote input notification.
        val remoteInputRowBottom by
            viewModel.remoteInputRowBottomBound.collectAsStateWithLifecycle(0f)

        // if remote input state changes, compare the row and IME's overlap and offset the scrim and
        // placeholder accordingly.
        LaunchedEffectWithLifecycle(isRemoteInputActive, remoteInputRowBottom, imeTop) {
            imeTop.floatValue = 0f
            snapshotFlow { imeTop.floatValue }
                .collect { imeTopValue ->
                    // Only scroll the stack if IME value has been populated (IME placeholder has
                    // been composed at least once), and our remote input row overlaps with the ime
                    // bounds.
                    if (
                        isRemoteInputActive &&
                            imeTopValue > 0f &&
                            remoteInputRowBottom > imeTopValue
                    ) {
                        scrollStackWithNestedScroll(
                            delta = Offset(x = 0f, y = remoteInputRowBottom - imeTopValue),
                            nestedScrollDispatcher = nestedScrollDispatcher,
                            scrollState = scrollState,
                        )
                    }
                }
        }

        // TalkBack sends a scroll event, when it wants to navigate to an item that is not displayed
        // in
        // the current viewport.
        LaunchedEffectWithLifecycle(viewModel) {
            viewModel.setAccessibilityScrollEventConsumer { event ->
                // scroll up, or down by the height of the visible portion of the notification stack
                val direction =
                    when (event) {
                        AccessibilityScrollEvent.SCROLL_UP -> -1
                        AccessibilityScrollEvent.SCROLL_DOWN -> 1
                    }
                val viewPortHeight = stackBoundsOnScreen.value.height
                val scrollStep = max(0f, viewPortHeight - stackScrollView.stackBottomInset)
                val scrollPosition = scrollState.value.toFloat()
                val scrollRange = scrollState.maxValue.toFloat()
                val targetScroll =
                    (scrollPosition + direction * scrollStep).coerceIn(0f, scrollRange)
                coroutineScope.launch {
                    scrollStackWithNestedScroll(
                        delta = Offset(x = 0f, y = targetScroll - scrollPosition),
                        nestedScrollDispatcher = nestedScrollDispatcher,
                        scrollState = scrollState,
                    )
                }
            }
            try {
                awaitCancellation()
            } finally {
                viewModel.setAccessibilityScrollEventConsumer(null)
            }
        }
    }

    val density = LocalDensity.current
    val screenCornerRadius = LocalScreenCornerRadius.current
    val scrimCornerRadius = dimensionResource(R.dimen.notification_scrim_corner_radius)
    val classicShadeNotificationScrimBgColor =
        Color(
            ShadeColors.classicShadeNotificationScrimBg(
                LocalContext.current,
                blurSupported = isTransparencyEnabled,
                // When the Notification Scrim punches a hole in the scene bg, we need to use the
                // composite colors of the Scene Scrim, and the Notification Scrim to achieve the
                // same color as  in the shade types where we are NOT punching a hole.
                composited = shouldPunchHoleBehindScrim,
            )
        )
    val expansionFraction by viewModel.expandFraction.collectAsStateWithLifecycle(0f)
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    /** Total horizontal stack padding in pixels. */
    val stackHorizontalPaddingPx =
        with(LocalDensity.current) { (stackTopPadding + stackBottomPadding).toPx() }.roundToInt()

    val scrimRounding =
        viewModel.shadeScrimRounding.collectAsStateWithLifecycle(ShadeScrimRounding())

    val swipeToExpandNotificationScrollConnection =
        shadeSession.rememberSession(
            key = "SwipeToExpandNotificationScrollConnection",
            density,
            viewModel.isCurrentGestureExpandingNotification,
        ) {
            PriorityNestedScrollConnection(
                orientation = Orientation.Vertical,
                canStartPreScroll = { _, _, _ -> false },
                canStartPostScroll = { _, _, _ -> viewModel.isCurrentGestureExpandingNotification },
                onStart = { _ ->
                    object : ScrollController {
                        override fun onScroll(
                            deltaScroll: Float,
                            source: NestedScrollSource,
                        ): Float {
                            return if (viewModel.isCurrentGestureExpandingNotification) {
                                // consume all the amount, when this swipe is expanding a
                                // notification
                                deltaScroll
                            } else {
                                // don't consume anything, when the expansion is done
                                0f
                            }
                        }

                        override fun onCancel() {
                            // No-op
                        }

                        override fun canStopOnPreFling(): Boolean = false

                        override suspend fun OnStopScope.onStop(initialVelocity: Float): Float = 0f
                    }
                },
            )
        }

    val scrimOverscrollEffect: OffsetOverscrollEffect = rememberOffsetOverscrollEffect()

    val interactionSource = remember { MutableInteractionSource() }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Layout(
        modifier =
            modifier
                .element(Notifications.Elements.NotificationScrim)
                .overscroll(verticalOverscrollEffect)
                .overscroll(scrimOverscrollEffect.withoutEventHandling())
                .onGloballyPositioned { coordinates ->
                    val boundsInWindow = coordinates.boundsInWindow()
                    debugLog(viewModel) {
                        "$tag.SCRIM onGloballyPositioned:" +
                            " size=${coordinates.size}" +
                            " bounds=$boundsInWindow"
                    }
                    viewModel.onScrimBoundsChanged(
                        ShadeScrimBounds(
                            left = boundsInWindow.left,
                            top = boundsInWindow.top,
                            right = boundsInWindow.right,
                            bottom = boundsInWindow.bottom,
                        )
                    )
                }
                .thenIf(onEmptySpaceClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // Prevent flicker on transition
                        onClick = { onEmptySpaceClick?.invoke() },
                    )
                },
        contents =
            listOf(
                {
                    // NotificationPanel background
                    Box(
                        modifier =
                            Modifier.graphicsLayer {
                                    shape =
                                        calculateCornerRadius(
                                                scrimCornerRadius,
                                                screenCornerRadius,
                                                { expansionFraction },
                                                shouldAnimateScrimCornerRadius(
                                                    layoutState,
                                                    shouldPunchHoleBehindScrim,
                                                    viewModel.notificationsShadeContentKey,
                                                ),
                                            )
                                            .let { scrimRounding.value.toRoundedCornerShape(it) }
                                    clip = true
                                }
                                // The DstOut blend mode is used to punch a transparent hole through
                                // the scrim's background, cutting out the QQS tiles. When used in
                                // conjunction with CompositingStrategy.Offscreen on the parent,
                                // it will only affects content on the current Scene.
                                .thenIf(shouldPunchHoleBehindScrim) {
                                    Modifier.drawBehind {
                                        drawRect(Color.Black, blendMode = BlendMode.DstOut)
                                    }
                                }
                                .graphicsLayer {
                                    alpha =
                                        (expansionFraction / EXPANSION_FOR_MAX_SCRIM_ALPHA)
                                            .coerceAtMost(1f)
                                }
                                // The background color that makes the surface behind Notifications.
                                .thenIf(shouldDrawScrimBackground) {
                                    Modifier.background(
                                        color = classicShadeNotificationScrimBgColor
                                    )
                                }
                    )
                },
                {
                    // NotificationPanel content
                    Box {
                        Column(
                            modifier =
                                Modifier.padding(top = stackTopPadding, bottom = stackBottomPadding)
                                    .onPlaced {
                                        val rawBounds = it.rawBoundsInWindow()
                                        debugLog(viewModel) {
                                            "$tag.NestedScroll.container onPlaced bounds=$rawBounds"
                                        }
                                        viewModel.setStackBounds(rawBounds)
                                    }
                                    .onUnplaced {
                                        debugLog(viewModel) {
                                            "$tag.NestedScroll.container onUnplaced"
                                        }
                                        viewModel.resetStackBounds()
                                    }
                                    .debugBackground(viewModel, DEBUG_BOX_COLOR)
                                    .disableSwipesWhenScrolling()
                                    .nestedScroll(swipeToExpandNotificationScrollConnection)
                                    .nestedScroll(
                                        connection = object : NestedScrollConnection {},
                                        dispatcher = nestedScrollDispatcher,
                                    )
                                    // Adding these 3 modifiers is needed to enable overscroll
                                    // when the list fits within its bounds: b/295810376
                                    .overscroll(overscrollEffect)
                                    .verticalScroll(scrollState)
                                    .scrollable(
                                        rememberScrollableState { 0f },
                                        Orientation.Vertical,
                                        overscrollEffect = overscrollEffect,
                                    )
                                    .fillMaxWidth()
                                    // Added extra bottom padding for keeping footerView inside
                                    // parent Viewbounds during overscroll, refer to
                                    // b/437347340#comment3
                                    .padding(bottom = 4.dp)
                                    .onGloballyPositioned { coordinates ->
                                        stackBoundsOnScreen.value = coordinates.boundsInWindow()
                                    }
                        ) {
                            StackPlaceholder(
                                tag = "NestedScroll",
                                viewModel = viewModel,
                                modifier =
                                    Modifier.notificationStackHeight(view = stackScrollView)
                                        .onSizeChanged { size ->
                                            onStackHeightChanged(
                                                size.height + stackHorizontalPaddingPx
                                            )
                                        },
                            )
                            Spacer(
                                modifier =
                                    Modifier.windowInsetsBottomHeight(
                                            WindowInsets.imeAnimationTarget
                                        )
                                        .onGloballyPositioned { coordinates: LayoutCoordinates ->
                                            imeTop.floatValue =
                                                screenHeight - coordinates.size.height
                                        }
                            )
                            if (viewModel.isVisualDebuggingEnabled) {
                                Text(
                                    text = "$tag.Nested",
                                    color = DEBUG_BOX_COLOR.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                )
                            }
                        }
                        if (shouldIncludeHeadsUpSpace) {
                            HeadsUpNotificationPlaceholder(
                                tag = "$tag.Nested",
                                stackScrollView = stackScrollView,
                                viewModel = viewModel,
                                modifier = Modifier.padding(top = stackTopPadding),
                            )
                        }
                    }
                },
            ),
        measurePolicy = { measurables, constraints ->
            check(measurables.size == 2)
            check(measurables[0].size == 1) { "background should have one composable" }
            check(measurables[1].size == 1) { "content should have one composable" }

            val backgroundMeasurable = measurables[0][0]
            val contentMeasurable = measurables[1][0]

            if (shouldFillMaxSize) {
                // Fill the entire available space with the content. We force the background to
                // match the screen height to ensure it covers the full display area.

                val content =
                    contentMeasurable.measure(
                        Constraints.fixed(
                            width = constraints.maxWidth,
                            height = constraints.maxHeight,
                        )
                    )

                val background =
                    backgroundMeasurable.measure(
                        Constraints.fixed(
                            width = constraints.maxWidth,
                            height = screenHeightDp.roundToPx(),
                        )
                    )

                layout(width = content.width, height = content.height) {
                    content.place(IntOffset.Zero)
                    background.place(IntOffset.Zero)
                }
            } else {
                // Make the background size match the content size.
                // The component should be only as large as its content requires. We measure the
                // content first, then force the background to be the *exact* same size. The final
                // layout size is determined by the content.

                val content = contentMeasurable.measure(constraints)
                val backgroundConstraints = Constraints.fixed(content.width, content.height)
                val background = backgroundMeasurable.measure(backgroundConstraints)

                layout(width = content.width, height = content.height) {
                    background.place(IntOffset.Zero)
                    content.place(IntOffset.Zero)
                }
            }
        },
    )
}

private suspend fun scrollStackWithNestedScroll(
    delta: Offset,
    nestedScrollDispatcher: NestedScrollDispatcher,
    scrollState: ScrollState,
): Offset {
    val preConsumed =
        nestedScrollDispatcher.dispatchPreScroll(
            available = -delta, // need a negative delta here to move the scrim up
            source = NestedScrollSource.UserInput,
        )
    val available = delta - preConsumed
    val consumed = Offset(x = 0f, y = scrollState.scrollBy(available.y))
    val left = available - consumed
    val postConsumed =
        nestedScrollDispatcher.dispatchPostScroll(
            consumed = consumed,
            available = -left, // need to invert it here, just like on preScroll
            source = NestedScrollSource.UserInput,
        )
    return consumed + preConsumed + postConsumed
}

private fun shouldAnimateScrimCornerRadius(
    state: SceneTransitionLayoutState,
    shouldPunchHoleBehindScrim: Boolean,
    notificationsShade: ContentKey,
): Boolean {
    return shouldPunchHoleBehindScrim ||
        state.isTransitioning(from = notificationsShade, to = Scenes.Lockscreen)
}

private fun calculateCornerRadius(
    scrimCornerRadius: Dp,
    screenCornerRadius: Dp,
    expansionFraction: () -> Float,
    transitioning: Boolean,
): Dp {
    return if (transitioning) {
        lerp(
                start = screenCornerRadius.value,
                stop = scrimCornerRadius.value,
                fraction = (expansionFraction() / EXPANSION_FOR_MAX_CORNER_RADIUS).coerceIn(0f, 1f),
            )
            .dp
    } else {
        scrimCornerRadius
    }
}

internal inline fun debugLog(viewModel: NotificationsPlaceholderViewModel, msg: () -> Any) {
    if (viewModel.isDebugLoggingEnabled) {
        Log.d(TAG, msg().toString())
    }
}

internal fun Modifier.debugBackground(
    viewModel: NotificationsPlaceholderViewModel,
    color: Color,
): Modifier =
    if (viewModel.isVisualDebuggingEnabled) {
        background(color)
    } else {
        this
    }

private fun ShadeScrimRounding.toRoundedCornerShape(radius: Dp): RoundedCornerShape {
    val topRadius = if (isTopRounded) radius else 0.dp
    val bottomRadius = if (isBottomRounded) radius else 0.dp
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

private const val TAG = "FlexiNotifs"
private val DEBUG_BOX_COLOR = Color(0f, 1f, 0f, 0.2f)

/**
 * The vertical boundaries of this layout relative to the window's origin, without being clipped to
 * the window bounds.
 *
 * This is different from [boundsInWindow], which clips the bounds to the window. Unclipped bounds
 * are needed when a layout is positioned off-screen, for example during a scene transition.
 */
private fun LayoutCoordinates.rawBoundsInWindow(): YSpace {
    val root = findRootCoordinates()

    // Explicitly set clipBounds=false to ensure we get the raw, unclipped bounds, as the default
    // (true) would clip if any layout between sourceCoordinates and root has clip enabled.
    val bounds = root.localBoundingBoxOf(sourceCoordinates = this, clipBounds = false)
    val boundsLeft = bounds.left
    val boundsTop = bounds.top
    val boundsRight = bounds.right
    val boundsBottom = bounds.bottom

    if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
        return YSpace.Zero
    }

    val topLeft = root.localToWindow(Offset(boundsLeft, boundsTop))
    val topRight = root.localToWindow(Offset(boundsRight, boundsTop))
    val bottomRight = root.localToWindow(Offset(boundsRight, boundsBottom))
    val bottomLeft = root.localToWindow(Offset(boundsLeft, boundsBottom))

    val top = fastMinOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
    val bottom = fastMaxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)

    return YSpace(top, bottom)
}
