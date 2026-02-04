/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe

/** Swipes down from the top of the screen to open the notification shade. */
fun ComposeTestRule.swipeDownToOpenShade() {
    // Get the root node bounds to calculate swipe coordinates.
    val rootBounds = this.onRoot().getUnclippedBoundsInRoot()
    val x = (rootBounds.left + rootBounds.right) / 2
    val startY = rootBounds.top
    val endY = (rootBounds.top + rootBounds.bottom) / 2

    // Perform a realistic swipe from the top-center edge of the screen.
    this.onRoot().performTouchInput {
        swipe(
            start = Offset(x.toPx(), startY.toPx()),
            end = Offset(x.toPx(), endY.toPx()),
            durationMillis = 200,
        )
    }
    this.waitForIdle()
}