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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton

/**
 * A composable that represents the button that is positioned inside or outside the region box.
 *
 * @param text The button text.
 * @param icon The button icon.
 * @param boxWidthDp The width of the region box in dp.
 * @param boxHeightDp The height of the region box in dp.
 * @param currentRect The current region box.
 * @param onClick A callback function that is invoked when this button is clicked.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionBoxButton(
    text: String,
    icon: Icon?,
    boxWidthDp: Dp,
    boxHeightDp: Dp,
    currentRect: Rect,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    val buttonWidthDp = with(density) { buttonSize.width.toDp() }
    val buttonHeightDp = with(density) { buttonSize.height.toDp() }

    // Check if the box dimensions is smaller than the button. If so, the button will be positioned
    // outside the box.
    val isButtonOutside = boxWidthDp < buttonWidthDp || boxHeightDp < buttonHeightDp

    // The translation of the button in the X direction.
    val targetTranslationX by
        animateFloatAsState(
            targetValue =
                if (isButtonOutside) {
                    // Position is centered horizontally, just above the box.
                    currentRect.left + (currentRect.width - buttonSize.width) / 2f
                } else {
                    // Position is centered inside the box.
                    currentRect.left + (currentRect.width - buttonSize.width) / 2f
                }
        )

    // The translation of the button in the Y direction.
    val targetTranslationY by
        animateFloatAsState(
            targetValue =
                if (isButtonOutside) {
                    // Position is centered horizontally, just above the box.
                    currentRect.top - buttonSize.height
                } else {
                    // Position is centered inside the box.
                    currentRect.top + (currentRect.height - buttonSize.height) / 2f
                }
        )

    PrimaryButton(
        modifier =
            modifier
                .onSizeChanged { size -> buttonSize = size }
                .graphicsLayer {
                    translationX = targetTranslationX
                    translationY = targetTranslationY
                },
        text = text,
        icon = icon,
        onClick = onClick,
    )
}
