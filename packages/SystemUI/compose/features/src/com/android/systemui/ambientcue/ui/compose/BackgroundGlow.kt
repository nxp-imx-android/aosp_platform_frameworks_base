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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.ambientcue.ui.shader.BackgroundGlowShader
import com.android.systemui.ambientcue.ui.utils.AiColorUtils.boostChroma

@Composable
fun BackgroundGlow(
    visible: Boolean,
    expanded: Boolean,
    collapsedOffset: IntOffset = IntOffset(0, 110),
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val turbulenceDisplacementPx = with(density) { Defaults.TURBULENCE_DISPLACEMENT_DP.dp.toPx() }
    val gradientRadiusPx = with(density) { Defaults.GRADIENT_RADIUS.dp.toPx() }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible

    val transition = rememberTransition(visibleState)
    val alpha by transition.animateFloat(transitionSpec = { tween(750) }) { if (it) 1f else 0f }
    val verticalOffset by
        animateIntOffsetAsState(if (expanded) IntOffset.Zero else collapsedOffset, tween(350))

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
                val offsetX = with(density) { verticalOffset.x.dp.toPx() }
                val offsetY = with(density) { verticalOffset.y.dp.toPx() }
                shader.setFloatUniform("alpha", alpha)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setColorUniform("color1", color1.toArgb())
                shader.setColorUniform("color2", color2.toArgb())
                shader.setColorUniform("color3", color3.toArgb())
                shader.setFloatUniform("origin", size.width / 2 + offsetX, size.height + offsetY)
                shader.setFloatUniform("radius", gradientRadiusPx)
                shader.setFloatUniform("turbulenceAmount", turbulenceDisplacementPx)
                shader.setFloatUniform("turbulencePhase", turbulencePhase)
                shader.setFloatUniform("turbulenceSize", Defaults.TURBULENCE_SIZE)
                drawRect(shaderBrush)
            }
        }
    )
}

private object Defaults {
    const val TURBULENCE_SIZE = 4.7f
    const val TURBULENCE_DISPLACEMENT_DP = 30
    const val GRADIENT_RADIUS = 200
    const val ONE_MINUTE_MS = 60 * 1000
}
