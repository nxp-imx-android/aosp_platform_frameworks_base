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

package com.android.systemui.screencapture.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A static, non-interactive box with border lines and centered corner knobs.
 *
 * @param width The width of the box.
 * @param height The height of the box.
 */
@Composable
fun RegionBox(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    // The diameter of the resizable knob on each corner of the region box.
    val KNOB_DIAMETER = 8.dp
    // The width of the border stroke around the region box.
    val BORDER_STROKE_WIDTH = 4.dp

    // The box that contains the whole screen.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The box container for the region box and its knobs.
        Box(modifier = Modifier.size(width, height)) {
            // The main box for the region selection.
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .border(BORDER_STROKE_WIDTH, MaterialTheme.colorScheme.onSurfaceVariant)
            )

            // The offset is half of the knob diameter so that it is centered.
            val knobOffset = KNOB_DIAMETER / 2

            // Top left knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.TopStart).offset(x = -knobOffset, y = -knobOffset),
            )

            // Top right knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = knobOffset, y = -knobOffset),
            )

            // Bottom left knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.BottomStart).offset(x = -knobOffset, y = knobOffset),
            )

            // Bottom right knob
            Knob(
                diameter = KNOB_DIAMETER,
                modifier =
                    Modifier.align(Alignment.BottomEnd).offset(x = knobOffset, y = knobOffset),
            )
        }
    }
}

/**
 * The circular knob on each corner of the box used for dragging each corner.
 *
 * @param diameter The diameter of the knob.
 */
@Composable
private fun Knob(diameter: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(diameter)
                .background(color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
    )
}
