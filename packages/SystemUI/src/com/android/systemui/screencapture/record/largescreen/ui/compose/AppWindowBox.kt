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

import android.app.ActivityManager
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureColors

@Composable
fun AppWindowBox(taskInfo: ActivityManager.RunningTaskInfo?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bounds = taskInfo?.configuration?.windowConfiguration?.bounds
    val borderStrokeWidth = 4.dp
    val cornerRadius = 16.dp

    Box(modifier = modifier.fillMaxSize().background(color = ScreenCaptureColors.scrimColor)) {
        if (bounds != null) {
            val boundsRect = bounds.toComposeRect()
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = boundsRect.topLeft,
                    size = boundsRect.size,
                    cornerRadius = CornerRadius(with(density) { cornerRadius.toPx() }),
                    blendMode = BlendMode.Clear,
                )
            }

            val boxWidthDp = with(density) { bounds.width().toDp() }
            val boxHeightDp = with(density) { bounds.height().toDp() }

            Box(
                modifier =
                    Modifier.offset { IntOffset(bounds.left, bounds.top) }
                        .size(boxWidthDp, boxHeightDp)
                        .border(
                            borderStrokeWidth,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(cornerRadius),
                        )
            ) {}
        }
    }
}

private fun AndroidRect.toComposeRect(): ComposeRect {
    return ComposeRect(
        left = this.left.toFloat(),
        top = this.top.toFloat(),
        right = this.right.toFloat(),
        bottom = this.bottom.toFloat(),
    )
}
