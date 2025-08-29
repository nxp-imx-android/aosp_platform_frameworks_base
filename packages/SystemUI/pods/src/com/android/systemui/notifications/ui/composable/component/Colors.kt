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

package com.android.systemui.notifications.ui.composable.component

import androidx.annotation.FloatRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Note: This is forked from
// frameworks/base/packages/SystemUI/compose/features/src/com/android/systemui/notifications/ui/composable/row/NotificationRowColors.kt

// TODO: b/432249649 - Once we move the compose code for bundles into a pod, we should consolidate
//  these duplicated files.

@Composable
@ReadOnlyComposable
internal fun notificationProtectionColor(): Color {
    // Per android.app.Notification.Colors, this is a 90% blend
    // of materialColorOnSurface over materialColorSurfaceContainerHigh
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryText = MaterialTheme.colorScheme.onSurface
    return blendARGB(primaryText, background, 0.9f)
}

/**
 * Blend between two ARGB colors using the given ratio.
 *
 * A blend ratio of 0.0 will result in [color1], 0.5 will give an even blend, 1.0 will result in
 * [color2].
 *
 * @param color1 the first ARGB color
 * @param color2 the second ARGB color
 * @param ratio the blend ratio of [color1] to [color2]
 * @see [com.android.internal.graphics.ColorUtils.blendARGB]
 */
private fun blendARGB(
    color1: Color,
    color2: Color,
    @FloatRange(from = 0.0, to = 1.0) ratio: Float,
): Color {
    val inverseRatio = 1 - ratio
    return Color(
        red = color1.red * inverseRatio + color2.red * ratio,
        green = color1.green * inverseRatio + color2.green * ratio,
        blue = color1.blue * inverseRatio + color2.blue * ratio,
        alpha = color1.alpha * inverseRatio + color2.alpha * ratio,
    )
}
