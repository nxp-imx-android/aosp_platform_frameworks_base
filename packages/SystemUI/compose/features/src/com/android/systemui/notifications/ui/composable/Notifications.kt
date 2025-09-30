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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
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
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.ShadeColors
import com.android.systemui.statusbar.notification.stack.shared.model.AccessibilityScrollEvent
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimRounding
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrollState
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
         * key only links to an empty box sized to the height of Notifications (placeholder), so STL
         * transitions are not fully supported here, except vertical positioning.
         */
        val NotificationStackPlaceholder = ElementKey("NotificationStackPlaceholder")
        /**
         * The [ElementKey] identifying the space reserved for the top HUN. This key only links to
         * an empty box sized to the height of Notifications (placeholder), so STL transitions are
         * not fully supported here, except vertical positioning.
         */
        val HeadsUpNotificationPlaceholder =
            ElementKey("HeadsUpNotificationPlaceholder", contentPicker = LowestZIndexContentPicker)
    }
}

/** Adds the space where notification stack should appear in the scene. */
@Composable
fun ContentScope.ConstrainedNotificationStack(
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .onSizeChanged { viewModel.onConstrainedAvailableSpaceChanged(it.height) }
                .onGloballyPositioned {
                    if (shouldUseLockscreenStackBounds(layoutState)) {
                        stackScrollView.updateDrawBounds(it.rawBoundsInWindow())
                    }
                }
    ) {
        NotificationPlaceholder(
            stackScrollView = stackScrollView,
            viewModel = viewModel,
            useStackBounds = { shouldUseLockscreenStackBounds(layoutState) },
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
            stackScrollView = stackScrollView,
            viewModel = viewModel,
            useHunBounds = {
                shouldUseLockscreenHunBounds(layoutState, viewModel.quickSettingsShadeContentKey)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Standalone version of the Scrolling Notification Stack. */
@Composable
fun ContentScope.ScrollingNotificationPanel(
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
    scrollState: ScrollState =
        shadeSession.rememberSaveableSession(saver = ScrollState.Saver, key = "ScrollState") {
            ScrollState(initial = 0)
        },
    overscrollEffect: OffsetOverscrollEffect = rememberOffsetOverscrollEffect(),
    onEmptySpaceClick: (() -> Unit)? = null,
) {
    if (!isAlwaysComposedContentVisible()) {
        // Some scenes or overlays that use this Composable may be using alwaysCompose=true which
        // will cause them to compose everything but not be visible. Because this Composable has
        // many side effects that push UI state upstream to its view-model, interactors, and
        // repositories and because the repositories are shared across callers of this Composable,
        // the cleanest way to prevent always-composing but invisible scenes/overlays from polluting
        // the shared state with bogus values is to prevent this entire Composable from actually
        // composing at all.
        //
        // Note that this optimization is very wide and is actively contradicting the point of
        // alwaysCompose=true (which attempts to pre-compose as much as it can), the initial use of
        // alwaysCompose=true is to always compose QS content, not notifications.
        //
        // Should a more granular optimization be preferred, we can let this Composable compose but
        // dive deeper into it and make sure that all of the side effects that send state upstream
        // to its view-model are properly taking lifecycle state into account.
        Box(modifier)
        return
    }

    val composeViewRoot = LocalView.current
    // whether the stack is moving due to a swipe or fling
    val isScrollInProgress = scrollState.isScrollInProgress || overscrollEffect.isInProgress

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            jankMonitor.begin(composeViewRoot, CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
            debugLog(viewModel) { "STACK scroll begins" }
        } else {
            debugLog(viewModel) { "STACK scroll ends" }
            jankMonitor.end(CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
        }
    }

    val shadeScrollState by
        shadeSession.rememberSession(key = "SingleShadeScrollState") {
            derivedStateOf {
                ShadeScrollState(
                    // we are not scrolled to the top unless the scroll position is zero,
                    isScrolledToTop = scrollState.value == 0,
                    scrollPosition = scrollState.value,
                    maxScrollPosition = scrollState.maxValue,
                )
            }
        }
    LaunchedEffect(shadeScrollState) { viewModel.setScrollState(shadeScrollState) }

    NestedScrollingNotificationPanel(
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
        scrollState = scrollState,
        overscrollEffect = overscrollEffect,
        onEmptySpaceClick = onEmptySpaceClick,
        onStackHeightChanged = { /* no-op without nested scroll */ },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContentScope.NestedScrollingNotificationPanel(
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
    onEmptySpaceClick: (() -> Unit)? = null,
    onStackHeightChanged: (Int) -> Unit = {},
) {
    val nestedScrollDispatcher =
        shadeSession.rememberSession(key = "NestedScrollDispatcher") { NestedScrollDispatcher() }
    val coroutineScope = shadeSession.sessionCoroutineScope(key = "NotificationScrollingStack")
    val density = LocalDensity.current
    val screenCornerRadius = LocalScreenCornerRadius.current
    val scrimCornerRadius = dimensionResource(R.dimen.notification_scrim_corner_radius)
    val singleShadeNotificationScrimBgColor =
        Color(
            ShadeColors.singleShadeNotificationScrimBg(
                LocalContext.current,
                blurSupported = isTransparencyEnabled,
            )
        )
    val syntheticScroll = viewModel.syntheticScroll.collectAsStateWithLifecycle(0f)
    val expansionFraction by viewModel.expandFraction.collectAsStateWithLifecycle(0f)
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    /** Total horizontal stack padding in pixels. */
    val stackHorizontalPaddingPx =
        with(LocalDensity.current) { (stackTopPadding + stackBottomPadding).toPx() }.roundToInt()

    /**
     * Space available for the notification stack on the screen. These bounds don't scroll off the
     * screen, and respect the scrim paddings, scrim clipping.
     */
    val stackBoundsOnScreen = remember { mutableStateOf(Rect.Zero) }

    val scrimRounding =
        viewModel.shadeScrimRounding.collectAsStateWithLifecycle(ShadeScrimRounding())

    // set the bounds to null when the scrim disappears
    DisposableEffect(Unit) { onDispose { viewModel.onScrimBoundsChanged(null) } }

    val isRemoteInputActive by viewModel.isRemoteInputActive.collectAsStateWithLifecycle(false)

    // The bottom Y bound of the currently focused remote input notification.
    val remoteInputRowBottom by viewModel.remoteInputRowBottomBound.collectAsStateWithLifecycle(0f)

    // The top y bound of the IME.
    val imeTop = remember { mutableFloatStateOf(0f) }

    // if we receive scroll delta from NSSL, offset the scrim and placeholder accordingly.
    LaunchedEffect(syntheticScroll, scrollState) {
        snapshotFlow { syntheticScroll.value }
            .collect { delta ->
                scrollStackWithNestedScroll(
                    delta = Offset(x = 0f, y = delta),
                    nestedScrollDispatcher = nestedScrollDispatcher,
                    scrollState = scrollState,
                )
            }
    }

    // if remote input state changes, compare the row and IME's overlap and offset the scrim and
    // placeholder accordingly.
    LaunchedEffect(isRemoteInputActive, remoteInputRowBottom, imeTop) {
        imeTop.floatValue = 0f
        snapshotFlow { imeTop.floatValue }
            .collect { imeTopValue ->
                // Only scroll the stack if IME value has been populated (IME placeholder has
                // been composed at least once), and our remote input row overlaps with the ime
                // bounds.
                if (isRemoteInputActive && imeTopValue > 0f && remoteInputRowBottom > imeTopValue) {
                    scrollStackWithNestedScroll(
                        delta = Offset(x = 0f, y = remoteInputRowBottom - imeTopValue),
                        nestedScrollDispatcher = nestedScrollDispatcher,
                        scrollState = scrollState,
                    )
                }
            }
    }

    // TalkBack sends a scroll event, when it wants to navigate to an item that is not displayed in
    // the current viewport.
    LaunchedEffect(viewModel) {
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
            val targetScroll = (scrollPosition + direction * scrollStep).coerceIn(0f, scrollRange)
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

    val overScrollEffect: OffsetOverscrollEffect = rememberOffsetOverscrollEffect()

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .element(Notifications.Elements.NotificationScrim)
                .overscroll(verticalOverscrollEffect)
                .overscroll(overscrollEffect.withoutEventHandling())
                .graphicsLayer {
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
                .onGloballyPositioned { coordinates ->
                    val boundsInWindow = coordinates.boundsInWindow()
                    debugLog(viewModel) {
                        "SCRIM onGloballyPositioned:" +
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
                }
    ) {
        Spacer(
            modifier =
                Modifier.thenIf(shouldFillMaxSize) { Modifier.fillMaxSize() }
                    .drawBehind { drawRect(Color.Black, blendMode = BlendMode.DstOut) }
        )
        Box(
            modifier =
                Modifier.graphicsLayer {
                        alpha = (expansionFraction / EXPANSION_FOR_MAX_SCRIM_ALPHA).coerceAtMost(1f)
                    }
                    .thenIf(shouldDrawScrimBackground) {
                        Modifier.background(color = singleShadeNotificationScrimBgColor)
                    }
                    .thenIf(shouldFillMaxSize) { Modifier.fillMaxSize() }
                    .padding(top = stackTopPadding, bottom = stackBottomPadding)
                    .onGloballyPositioned {
                        if (!shouldUseLockscreenStackBounds(layoutState)) {
                            stackScrollView.updateDrawBounds(it.rawBoundsInWindow())
                        }
                    }
                    .debugBackground(viewModel, DEBUG_BOX_COLOR)
        ) {
            Column(
                modifier =
                    Modifier.disableSwipesWhenScrolling()
                        .nestedScroll(swipeToExpandNotificationScrollConnection)
                        .nestedScroll(
                            connection = object : NestedScrollConnection {},
                            dispatcher = nestedScrollDispatcher,
                        )
                        .verticalScroll(scrollState, overscrollEffect = overScrollEffect)
                        .fillMaxWidth()
                        // Added extra bottom padding for keeping footerView inside parent
                        // Viewbounds during overscroll, refer to b/437347340#comment3
                        .padding(bottom = 4.dp)
                        .onGloballyPositioned { coordinates ->
                            stackBoundsOnScreen.value = coordinates.boundsInWindow()
                        }
            ) {
                NotificationPlaceholder(
                    stackScrollView = stackScrollView,
                    viewModel = viewModel,
                    useStackBounds = { !shouldUseLockscreenStackBounds(layoutState) },
                    modifier =
                        Modifier.notificationStackHeight(view = stackScrollView).onSizeChanged {
                            size ->
                            onStackHeightChanged(size.height + stackHorizontalPaddingPx)
                        },
                )
                Spacer(
                    modifier =
                        Modifier.windowInsetsBottomHeight(WindowInsets.imeAnimationTarget)
                            .onGloballyPositioned { coordinates: LayoutCoordinates ->
                                imeTop.floatValue = screenHeight - coordinates.size.height
                            }
                )
                if (viewModel.isVisualDebuggingEnabled) {
                    Text(
                        text = "NotificationScrollingStack",
                        color = DEBUG_BOX_COLOR.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        if (shouldIncludeHeadsUpSpace) {
            HeadsUpNotificationPlaceholder(
                stackScrollView = stackScrollView,
                viewModel = viewModel,
                useHunBounds = {
                    !shouldUseLockscreenHunBounds(
                        layoutState,
                        viewModel.quickSettingsShadeContentKey,
                    )
                },
                modifier = Modifier.padding(top = stackTopPadding),
            )
        }
    }
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

private fun SceneTransitionLayoutState.isIdleOnLockscreenWithNoShade(): Boolean {
    return isIdle(Scenes.Lockscreen) &&
        !isInCurrentOverlays(Overlays.NotificationsShade) &&
        !isInCurrentOverlays(Overlays.QuickSettingsShade)
}

private fun shouldUseLockscreenStackBounds(state: SceneTransitionLayoutState): Boolean {
    return when {
        // Idle on the Lockscreen without Shade overlays.
        state.isIdleOnLockscreenWithNoShade() -> true

        // When going from Lockscreen to a content without the placeholder, keep the LS bounds.
        state.isTransitioning(from = Scenes.Lockscreen) &&
            !state.isTransitioning(to = Scenes.Shade) &&
            !state.isTransitioning(to = Overlays.NotificationsShade) &&
            !state.isTransitioning(to = Scenes.QuickSettings) -> true

        // When transitioning between LS and Bouncer, keep using the LS bounds, because there is no
        // placeholder on Bouncer.
        state.isTransitioningBetween(content = Scenes.Lockscreen, other = Overlays.Bouncer) -> true

        // Otherwise don't use the LS bounds.
        else -> false
    }
}

private fun shouldUseLockscreenHunBounds(
    state: SceneTransitionLayoutState,
    quickSettingsShade: ContentKey,
): Boolean {
    return when {
        // Idle on the Lockscreen without Shade overlays.
        state.isIdleOnLockscreenWithNoShade() -> true

        // Transitioning from QS to LS.
        state.isTransitioning(from = quickSettingsShade, to = Scenes.Lockscreen) -> true

        // Otherwise don't use the LS bounds.
        else -> false
    }
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
 * The boundaries of this layout relative to the window's origin, without being clipped to the
 * window bounds.
 *
 * This is different from [boundsInWindow], which clips the bounds to the window. Unclipped bounds
 * are needed when a layout is positioned off-screen, for example during a scene transition.
 */
private fun LayoutCoordinates.rawBoundsInWindow(): android.graphics.RectF {
    val root = findRootCoordinates()

    val bounds = root.localBoundingBoxOf(this)
    val boundsLeft = bounds.left
    val boundsTop = bounds.top
    val boundsRight = bounds.right
    val boundsBottom = bounds.bottom

    if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
        return android.graphics.RectF()
    }

    val topLeft = root.localToWindow(Offset(boundsLeft, boundsTop))
    val topRight = root.localToWindow(Offset(boundsRight, boundsTop))
    val bottomRight = root.localToWindow(Offset(boundsRight, boundsBottom))
    val bottomLeft = root.localToWindow(Offset(boundsLeft, boundsBottom))

    val left = fastMinOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
    val right = fastMaxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)

    val top = fastMinOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
    val bottom = fastMaxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)

    return android.graphics.RectF(left, top, right, bottom)
}
