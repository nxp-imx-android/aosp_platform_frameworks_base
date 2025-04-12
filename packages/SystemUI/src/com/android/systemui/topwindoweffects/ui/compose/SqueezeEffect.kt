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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel.Companion.ZOOM_OUT_SCALE
import com.android.wm.shell.appzoomout.AppZoomOut
import java.util.Optional

// Defines the amount the squeeze border overlaps the shrinking content.
// This is the difference between the total squeeze thickness and the thickness purely caused by the
// zoom effect. At full progress, this overlap is 8 dp.
private val SqueezeEffectOverlapMaxThickness = 8.dp
private val SqueezeColor = Color.Black

@Composable
fun SqueezeEffect(
    viewModelFactory: SqueezeEffectViewModel.Factory,
    @DrawableRes topRoundedCornerResourceId: Int,
    @DrawableRes bottomRoundedCornerResourceId: Int,
    physicalPixelDisplaySizeRatio: Float,
    onEffectFinished: () -> Unit,
    appZoomOutOptional: Optional<AppZoomOut>,
) {
    val viewModel = rememberViewModel(traceName = "SqueezeEffect") { viewModelFactory.create() }

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
            squeezeProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
            squeezeProgress.animateTo(0f, animationSpec = tween(durationMillis = 333))
            if (squeezeProgress.value == 0f) {
                onEffectFinished()
            }
            isAnimationInterruptible = true
        } else {
            if (squeezeProgress.value != 0f) {
                squeezeProgress.animateTo(0f, animationSpec = tween(durationMillis = 333))
            }
            if (squeezeProgress.value == 0f) {
                onEffectFinished()
            }
        }
    }

    LaunchedEffect(squeezeProgress.value) {
        appZoomOutOptional.ifPresent {
            it.setTopLevelScale(1f - squeezeProgress.value * ZOOM_OUT_SCALE)
        }
    }

    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (squeezeProgress.value <= 0) {
            return@Canvas
        }

        // Calculate the thickness of the squeeze effect borders.
        // The total thickness on each side is composed of two parts:
        // 1. Zoom Thickness: This accounts for the visual space created by the AppZoomOut
        //    effect scaling the content down. It's calculated as half the total reduction
        //    in screen dimension (width or height) caused by scaling (ZOOM_OUT_SCALE),
        //    proportional to the current squeezeProgress. We divide by 2 because the
        //    reduction happens on both sides (left/right or top/bottom).
        // 2. Overlap Thickness: An additional fixed thickness (converted from dp to px)
        //    scaled by the squeezeProgress, designed to make the border slightly overlap
        //    the scaled content for a better visual effect.
        val horizontalZoomThickness = screenWidth * ZOOM_OUT_SCALE * squeezeProgress.value / 2f
        val verticalZoomThickness = screenHeight * ZOOM_OUT_SCALE * squeezeProgress.value / 2f
        val overlapThickness = SqueezeEffectOverlapMaxThickness.toPx() * squeezeProgress.value

        val horizontalSqueezeThickness = horizontalZoomThickness + overlapThickness
        val verticalSqueezeThickness = verticalZoomThickness + overlapThickness

        drawRect(color = SqueezeColor, size = Size(size.width, verticalSqueezeThickness))

        drawRect(
            color = SqueezeColor,
            topLeft = Offset(0f, size.height - verticalSqueezeThickness),
            size = Size(size.width, verticalSqueezeThickness),
        )

        drawRect(color = SqueezeColor, size = Size(horizontalSqueezeThickness, size.height))

        drawRect(
            color = SqueezeColor,
            topLeft = Offset(size.width - horizontalSqueezeThickness, 0f),
            size = Size(horizontalSqueezeThickness, size.height),
        )

        drawTransform(
            dx = horizontalSqueezeThickness,
            dy = verticalSqueezeThickness,
            rotation = 0f,
            corner = top,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = size.width - horizontalSqueezeThickness,
            dy = verticalSqueezeThickness,
            rotation = 90f,
            corner = top,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = horizontalSqueezeThickness,
            dy = size.height - verticalSqueezeThickness,
            rotation = 270f,
            corner = bottom,
            displaySizeRatio = physicalPixelDisplaySizeRatio,
        )

        drawTransform(
            dx = size.width - horizontalSqueezeThickness,
            dy = size.height - verticalSqueezeThickness,
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
