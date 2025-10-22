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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel

/**
 * The fundamental composable that reserves space for the main list of notifications.
 *
 * @param stackScrollView The legacy view that hosts the notification stack.
 * @param useStackBounds A lambda to determine whether this instance should report its bounds. This
 *   is used to avoid conflicting bounds updates during scene transitions when multiple placeholders
 *   might co-exist.
 * @param viewModel The view model for placeholder state.
 * @param modifier The [Modifier] to be applied to this placeholder.
 */
@Composable
internal fun ContentScope.StackPlaceholder(
    stackScrollView: NotificationScrollView,
    useStackBounds: () -> Boolean,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .element(Notifications.Elements.StackPlaceholder)
                .debugBackground(viewModel, DEBUG_STACK_COLOR)
                .onSizeChanged { size -> debugLog(viewModel) { "STACK onSizeChanged: size=$size" } }
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    // This element is opted out of the shared element system, so there can be
                    // multiple instances of it during a transition. Thus we need to determine which
                    // instance should feed its bounds to NSSL to avoid providing conflicting values
                    val useBounds = useStackBounds()
                    if (useBounds) {
                        // NOTE: positionInWindow.y scrolls off screen, but boundsInWindow.top won't
                        val positionInWindow = coordinates.positionInWindow()
                        debugLog(viewModel) {
                            "STACK onGloballyPositioned:" +
                                " size=${coordinates.size}" +
                                " position=$positionInWindow" +
                                " bounds=${coordinates.boundsInWindow()}"
                        }
                        stackScrollView.setStackTop(positionInWindow.y)
                    }
                }
    ) {
        if (viewModel.isVisualDebuggingEnabled) {
            Text(
                text = "NotificationStackPlaceholder",
                color = DEBUG_STACK_COLOR.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private val DEBUG_STACK_COLOR = Color(1f, 0f, 0f, 0.2f)
