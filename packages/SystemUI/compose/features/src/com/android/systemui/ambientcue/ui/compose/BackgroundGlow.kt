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

package com.android.systemui.ambientcue.ui.compose

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.android.systemui.ambientcue.ui.shader.BackgroundGlowShader

@Composable
fun BackgroundGlow(visible: Boolean, expanded: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val turbulenceDisplacementPx = with(density) { Defaults.TURBULENCE_DISPLACEMENT_DP.dp.toPx() }
    val gradientRadiusPx = with(density) { Defaults.GRADIENT_RADIUS.dp.toPx() }

    val alpha by animateFloatAsState(if (visible) 1f else 0f, animationSpec = tween(750))
    val verticalOffset by
        animateDpAsState(if (expanded) 0.dp else Defaults.COLLAPSED_TRANSLATION_DP.dp, tween(350))
    val verticalOffsetPx = with(density) { verticalOffset.toPx() }

    // Infinite animation responsible for the "vapor" effect distorting the radial gradient
    val infiniteTransition = rememberInfiniteTransition(label = "backgroundGlow")
    val turbulencePhase by
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 10f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(Defaults.ONE_MINUTE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "turbulencePhase",
        )

    val color1 = Color(boostChroma(MaterialTheme.colorScheme.secondaryContainer.toArgb()))
    val color2 = Color(boostChroma(MaterialTheme.colorScheme.primary.toArgb()))
    val color3 = Color(boostChroma(MaterialTheme.colorScheme.tertiary.toArgb()))

    val shader = RuntimeShader(BackgroundGlowShader.FRAG_SHADER)
    val shaderBrush = ShaderBrush(shader)

    Box(
        modifier.size(400.dp, 200.dp).alpha(alpha).drawWithCache {
            onDrawWithContent {
                shader.setFloatUniform("alpha", alpha)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setColorUniform("color1", color1.toArgb())
                shader.setColorUniform("color2", color2.toArgb())
                shader.setColorUniform("color3", color3.toArgb())
                shader.setFloatUniform("origin", size.width / 2, size.height + verticalOffsetPx)
                shader.setFloatUniform("radius", gradientRadiusPx)
                shader.setFloatUniform("turbulenceAmount", turbulenceDisplacementPx)
                shader.setFloatUniform("turbulencePhase", turbulencePhase)
                shader.setFloatUniform("turbulenceSize", Defaults.TURBULENCE_SIZE)
                drawRect(shaderBrush)
            }
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

private object Defaults {
    const val COLLAPSED_TRANSLATION_DP = 110
    const val TURBULENCE_SIZE = 4.7f
    const val TURBULENCE_DISPLACEMENT_DP = 30
    const val GRADIENT_RADIUS = 200
    const val ONE_MINUTE_MS = 60 * 1000
}
