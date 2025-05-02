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

package com.android.systemui.topwindoweffects.ui.compose

import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectConfig
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import com.android.wm.shell.appzoomout.AppZoomOut
import java.util.Optional
import kotlin.math.max
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

private val SqueezeColor = Color.Black
private val SqueezeEffectMaxThickness = 16.dp

// Defines the amount the squeeze border overlaps the shrinking content on the shorter display edge.
// At full progress, the overlap is 4 dp on the shorter display edge. On the longer display edge, it
// will be more than 4 dp, depending on the display aspect ratio.
private val SqueezeEffectOverlapShortEdgeThickness = 4.dp

@Composable
fun SqueezeEffect(
    viewModelFactory: SqueezeEffectViewModel.Factory,
    @DrawableRes topRoundedCornerResourceId: Int,
    @DrawableRes bottomRoundedCornerResourceId: Int,
    physicalPixelDisplaySizeRatio: Float,
    onEffectStarted: suspend () -> Unit,
    onEffectFinished: suspend () -> Unit,
    appZoomOutOptional: Optional<AppZoomOut>,
    interactionJankMonitor: InteractionJankMonitor,
) {
    val viewModel = rememberViewModel(traceName = "SqueezeEffect") { viewModelFactory.create() }
    val view = LocalView.current

    val down = viewModel.isPowerButtonPressed
    val longPressed = viewModel.isPowerButtonLongPressed

    val top = rememberVectorPainter(ImageVector.vectorResource(topRoundedCornerResourceId))
    val bottom = rememberVectorPainter(ImageVector.vectorResource(bottomRoundedCornerResourceId))

    val squeezeProgress = remember { Animatable(0f) }

    // The squeeze animation has two states indicated by the value of this flag:
    // true - the main animation is running, animating the progress to 1 and then back to 0.
    // false - the main animation has been interrupted and we are animating back to 0. This happens
    //         if the user let's go of the power button before long press power has been detected.
    // In both the above cases, as soon as the squeeze effect finishes animating (progress value
    // becomes 0 again), we execute the "onEffectFinished" block which ensures that effects window
    // is removed.
    var isMainAnimationRunning by remember { mutableStateOf(false) }

    // The main animation is interruptible until power button long press has been detected. At this
    // point the default assistant is invoked, and since this invocation cannot be interrupted by
    // lifting the power button the animation shouldn't be interruptible either.
    var isAnimationInterruptible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { onEffectStarted() }

    LaunchedEffect(longPressed) {
        if (longPressed) {
            isAnimationInterruptible = false
        }
    }

    LaunchedEffect(down, isAnimationInterruptible) {
        isMainAnimationRunning = down || !isAnimationInterruptible
    }

    LaunchedEffect(isMainAnimationRunning) {
        if (isMainAnimationRunning) {
            interactionJankMonitor.begin(view, Cuj.CUJ_LPP_ASSIST_INVOCATION_EFFECT)
            squeezeProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = SqueezeEffectConfig.INWARD_EFFECT_DURATION),
            )
            squeezeProgress.animateTo(
                0f,
                animationSpec = tween(durationMillis = SqueezeEffectConfig.OUTWARD_EFFECT_DURATION),
            )
            if (squeezeProgress.value == 0f) {
                interactionJankMonitor.end(Cuj.CUJ_LPP_ASSIST_INVOCATION_EFFECT)
                viewModel.onSqueezeEffectEnd()
                onEffectFinished()
            }
            isAnimationInterruptible = true
        } else {
            if (squeezeProgress.value != 0f) {
                squeezeProgress.animateTo(
                    0f,
                    animationSpec =
                        tween(durationMillis = SqueezeEffectConfig.OUTWARD_EFFECT_DURATION),
                )
            }
            if (squeezeProgress.value == 0f) {
                interactionJankMonitor.cancel(Cuj.CUJ_LPP_ASSIST_INVOCATION_EFFECT)
                viewModel.onSqueezeEffectEnd()
                onEffectFinished()
            }
        }
    }

    val density = LocalDensity.current
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val screenHeightPx = LocalWindowInfo.current.containerSize.height
    val longEdgePx = max(screenHeightPx, screenWidthPx)
    val zoomPotentialPx =
        with(density) {
            (SqueezeEffectMaxThickness.toPx() - SqueezeEffectOverlapShortEdgeThickness.toPx()) * 2
        }
    val zoomOutScale = 1f - (longEdgePx - zoomPotentialPx) / longEdgePx

    LaunchedEffect(squeezeProgress.value) {
        appZoomOutOptional.ifPresent {
            it.setTopLevelScale(1f - squeezeProgress.value * zoomOutScale)
        }
    }

    val squeezeThickness =
        with(density) { SqueezeEffectMaxThickness.toPx() * squeezeProgress.value }

    Canvas(
        modifier =
            Modifier.fillMaxSize().motionTestValues {
                squeezeThickness exportAs MotionTestKeys.squeezeThickness
            }
    ) {
        if (squeezeProgress.value <= 0) {
            return@Canvas
        }

        drawRect(color = SqueezeColor, size = Size(size.width, squeezeThickness))

        drawRect(
            color = SqueezeColor,
            topLeft = Offset(0f, size.height - squeezeThickness),
            size = Size(size.width, squeezeThickness),
        )

        drawRect(color = SqueezeColor, size = Size(squeezeThickness, size.height))

        drawRect(
            color = SqueezeColor,
            topLeft = Offset(size.width - squeezeThickness, 0f),
            size = Size(squeezeThickness, size.height),
        )

        drawTransform(
            dx = squeezeThickness,
            dy = squeezeThickness,
            rotation = 0f,
            corner = top,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = size.width - squeezeThickness,
            dy = squeezeThickness,
            rotation = 90f,
            corner = top,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = squeezeThickness,
            dy = size.height - squeezeThickness,
            rotation = 270f,
            corner = bottom,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = size.width - squeezeThickness,
            dy = size.height - squeezeThickness,
            rotation = 180f,
            corner = bottom,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )
    }
}

private fun DrawScope.drawTransform(
    dx: Float,
    dy: Float,
    rotation: Float = 0f,
    corner: VectorPainter,
    displaySizeRatio: Float,
) {
    withTransform(
        transformBlock = {
            transform(
                matrix =
                    Matrix().apply {
                        translate(dx, dy)
                        if (rotation != 0f) {
                            rotateZ(rotation)
                        }
                    }
            )
        }
    ) {
        with(corner) {
            draw(
                size =
                    Size(
                        width = intrinsicSize.width * displaySizeRatio,
                        height = intrinsicSize.height * displaySizeRatio,
                    )
            )
        }
    }
}

@VisibleForTesting
object MotionTestKeys {
    val squeezeThickness = MotionTestValueKey<Float>("squeezeThickness")
}
