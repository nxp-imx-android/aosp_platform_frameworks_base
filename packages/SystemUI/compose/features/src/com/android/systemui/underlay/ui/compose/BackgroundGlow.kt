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

package com.android.systemui.underlay.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

@Composable
fun BackgroundGlow(visible: Boolean, modifier: Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, animationSpec = tween(750))
    val blurScale = 1.3f

    val primaryBoosted = Color(boostChroma(MaterialTheme.colorScheme.primary.toArgb()))
    val primaryFixedBoosted = Color(boostChroma(MaterialTheme.colorScheme.primary.toArgb()))
    val tertiaryBoosted = Color(boostChroma(MaterialTheme.colorScheme.tertiaryContainer.toArgb()))

    val gradient1Brush =
        Brush.radialGradient(
            listOf(primaryFixedBoosted.copy(alpha = 0.3f), primaryFixedBoosted.copy(alpha = 0f))
        )
    val gradient2Brush =
        Brush.radialGradient(
            listOf(primaryBoosted.copy(alpha = 0.4f), primaryBoosted.copy(alpha = 0f))
        )
    val gradient3Brush =
        Brush.radialGradient(
            listOf(tertiaryBoosted.copy(alpha = 0.3f), tertiaryBoosted.copy(alpha = 0f))
        )

    // The glow is made of 3 radial gradients.
    // All gradients are in the same box to make it simpler to move them around
    Box(
        modifier.size(372.dp, 68.dp).alpha(alpha).drawBehind {
            scale(2.12f * blurScale, 1f) {
                translate(0f, size.height * 0.8f) { drawCircle(gradient1Brush) }
            }
            scale(4.59f * blurScale, 1f) {
                translate(0f, size.height * 0.45f) { drawOval(gradient2Brush) }
            }
            scale(2.41f * blurScale, 1f) { drawOval(gradient3Brush) }
        }
    )
}

private fun boostChroma(color: Int): Int {
    val outColor = FloatArray(3)
    ColorUtils.colorToM3HCT(color, outColor)
    val chroma = outColor[1]
    if (chroma <= 5) {
        return color
    }
    return ColorUtils.M3HCTToColor(outColor[0], 120f, outColor[2])
}
