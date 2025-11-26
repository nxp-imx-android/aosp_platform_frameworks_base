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

package com.android.systemui.notifications.ui.composable

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.onUnplaced
import com.android.compose.modifiers.thenIf
import com.android.compose.modifiers.width
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.ui.YSpace
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import kotlin.math.roundToInt

/**
 * Defines the area where heads up notifications (HUNs) can appear.
 *
 * This is a simple placeholder that reports its bounds and does not handle any user input. For an
 * interactive version that supports snoozing, see [SnoozableHeadsUpNotificationPlaceholder].
 *
 * @param stackScrollView The legacy view that hosts the notification stack.
 * @param viewModel The view model for placeholder state.
 * @param modifier The [Modifier] to be applied to this placeholder.
 */
@Composable
fun ContentScope.HeadsUpNotificationPlaceholder(
    tag: String,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .element(Notifications.Elements.HeadsUpNotificationPlaceholder)
                .fillMaxWidth()
                .notificationHeadsUpHeight(stackScrollView)
                .debugBackground(viewModel, DEBUG_HUN_COLOR)
                .onPlaced { coordinates: LayoutCoordinates ->
                    // Note: boundsInWindow doesn't scroll off the screen, so use positionInWindow
                    // for top bound, which can scroll off screen while snoozing.
                    val positionInWindow = coordinates.positionInWindow()
                    val boundsInWindow = coordinates.boundsInWindow()
                    viewModel.setHeadsUpBounds(
                        YSpace(top = positionInWindow.y, bottom = boundsInWindow.bottom)
                    )
                    debugLog(viewModel) {
                        "$tag.HUNS onPlaced:" +
                            " size=${coordinates.size}" +
                            " bounds=$boundsInWindow"
                    }
                }
                .onUnplaced {
                    debugLog(viewModel) { "$tag.HUNS onUnplaced" }
                    viewModel.resetHeadsUpBounds()
                }
    ) {
        if (viewModel.isVisualDebuggingEnabled) {
            Text(
                text = "$tag.HUNPlaceholder",
                color = DEBUG_HUN_COLOR.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * A version of [HeadsUpNotificationPlaceholder] that can be swiped up off the top edge of the
 * screen by the user. When swiped up, the heads up notification is snoozed.
 *
 * @param useStackBounds Whether to communicate stackBounds updated to the [stackScrollView]. This
 *   should be `true` when content rendering the regular stack is not setting draw bounds anymore,
 *   but HUNs can still appear.
 * @param stackScrollView The legacy view that hosts the notification stack.
 * @param viewModel The view model for placeholder state.
 * @param modifier The [Modifier] to be applied to this placeholder.
 */
@Composable
fun ContentScope.SnoozableHeadsUpNotificationPlaceholder(
    tag: String,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    val isSnoozable by viewModel.isHeadsUpOrAnimatingAway.collectAsStateWithLifecycle(false)

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val headsUpInset = with(LocalDensity.current) { headsUpTopInset().toPx() }
    val minScrollOffset = -headsUpInset
    val maxScrollOffset = 0f

    val scrollableState = rememberScrollableState { delta ->
        consumeDeltaWithinRange(
            current = scrollOffset,
            setCurrent = { scrollOffset = it },
            min = minScrollOffset,
            max = maxScrollOffset,
            delta,
        )
    }

    val snoozeScrollConnection =
        remember(minScrollOffset) {
            object : NestedScrollConnection {
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (
                        velocityOrPositionalThresholdReached(
                            scrollOffset,
                            minScrollOffset,
                            available.y,
                        )
                    ) {
                        scrollableState.animateScrollBy(minScrollOffset, tween())
                    } else {
                        scrollableState.animateScrollBy(-minScrollOffset, tween())
                    }
                    return available
                }
            }
        }

    val horizontalAlignment = viewModel.horizontalAlignment
    val halfScreenWidth = LocalWindowInfo.current.containerSize.width / 2

    LaunchedEffect(isSnoozable) { scrollOffset = 0f }

    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (!scrollableState.isScrollInProgress && scrollOffset <= minScrollOffset) {
            viewModel.setHeadsUpAnimatingAway(false)
            viewModel.snoozeHun()
        }
    }

    HeadsUpNotificationPlaceholder(
        tag = "$tag.Snoozable",
        stackScrollView = stackScrollView,
        viewModel = viewModel,
        modifier =
            modifier
                // In side-aligned layouts, HUNs are limited to half the screen width.
                .thenIf(horizontalAlignment != Alignment.CenterHorizontally) {
                    Modifier.width { halfScreenWidth }
                }
                .offset {
                    IntOffset(
                        x = if (horizontalAlignment == Alignment.End) halfScreenWidth else 0,
                        y =
                            calculateHeadsUpPlaceholderYOffset(
                                scrollOffset.roundToInt(),
                                minScrollOffset.roundToInt(),
                                stackScrollView.topHeadsUpHeight,
                            ),
                    )
                }
                // TODO(462706428) Make NSSL work with HeadsUpPlaceholder values only, and leave
                // stack related updates strictly to the stack placeholders.
                //
                // Make sure NSSL needs to receive some valid stack bounds, even if the
                // HeadsUpPlaceholder is displayed without the regular StackPlaceholder.
                .onPlaced {
                    val bounds = it.boundsInWindow()
                    debugLog(viewModel) { "$tag.Snoozable onPlaced bounds:$bounds" }
                    viewModel.setStackBounds(YSpace(bounds.top, bounds.bottom))
                    // Use -headsUpInset to allow HUN translation outside bounds for snoozing.
                    viewModel.setStackScrollTop(-headsUpInset)
                }
                .onUnplaced {
                    debugLog(viewModel) { "$tag.Snoozable onUnplaced" }
                    viewModel.resetStackBounds()
                    viewModel.resetStackScrollTop()
                }
                .thenIf(isSnoozable) { Modifier.nestedScroll(snoozeScrollConnection) }
                .scrollable(orientation = Orientation.Vertical, state = scrollableState),
    )
}

/** Y position of the HUNs at rest, when the shade is closed. */
@Composable
private fun headsUpTopInset(): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() +
        dimensionResource(R.dimen.heads_up_status_bar_padding)

private fun calculateHeadsUpPlaceholderYOffset(
    scrollOffset: Int,
    minScrollOffset: Int,
    topHeadsUpHeight: Int,
): Int {
    return -minScrollOffset +
        (scrollOffset * (-minScrollOffset + topHeadsUpHeight) / -minScrollOffset)
}

private fun velocityOrPositionalThresholdReached(
    scrollOffset: Float,
    minScrollOffset: Float,
    availableVelocityY: Float,
): Boolean {
    return availableVelocityY < HUN_SNOOZE_VELOCITY_THRESHOLD ||
        (availableVelocityY <= 0f &&
            scrollOffset < minScrollOffset * HUN_SNOOZE_POSITIONAL_THRESHOLD_FRACTION)
}

/**
 * Takes a range, current value, and delta, and updates the current value by the delta, coercing the
 * result within the given range. Returns how much of the delta was consumed.
 */
private fun consumeDeltaWithinRange(
    current: Float,
    setCurrent: (Float) -> Unit,
    min: Float,
    max: Float,
    delta: Float,
): Float {
    return if (delta < 0 && current > min) {
        val remainder = (current + delta - min).coerceAtMost(0f)
        setCurrent((current + delta).coerceAtLeast(min))
        delta - remainder
    } else if (delta > 0 && current < max) {
        val remainder = (current + delta).coerceAtLeast(0f)
        setCurrent((current + delta).coerceAtMost(max))
        delta - remainder
    } else 0f
}

private val DEBUG_HUN_COLOR = Color(0f, 0f, 1f, 0.2f)
private const val HUN_SNOOZE_POSITIONAL_THRESHOLD_FRACTION = 0.25f
private const val HUN_SNOOZE_VELOCITY_THRESHOLD = -70f
