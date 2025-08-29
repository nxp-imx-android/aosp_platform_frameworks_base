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
package com.android.systemui.securelockdevice.ui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.compose.modifiers.height
import com.android.compose.modifiers.width
import com.android.systemui.Flags.bpColors
import com.android.systemui.biometrics.BiometricAuthIconAssets
import com.android.systemui.lifecycle.rememberActivated
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.util.ui.compose.LottieColorUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val TO_BOUNCER_DURATION = 400.milliseconds
private val TO_GONE_DURATION = 500.milliseconds

@Composable
fun SecureLockDeviceContent(
    secureLockDeviceViewModelFactory: SecureLockDeviceBiometricAuthContentViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val secureLockDeviceViewModel =
        rememberActivated(traceName = "SecureLockDeviceBiometricAuthContentViewModel") {
            secureLockDeviceViewModelFactory.create()
        }

    val isVisible = secureLockDeviceViewModel.isVisible
    val visibleState = remember { MutableTransitionState(isVisible) }

    /** This effect is run when the composable enters the composition */
    LaunchedEffect(Unit) { secureLockDeviceViewModel.startAppearAnimation() }

    /**
     * Updates the [visibleState] that drives the [AnimatedVisibility] animation.
     *
     * When [SecureLockDeviceBiometricAuthContentViewModel.isVisible] changes, this effect updates
     * the [MutableTransitionState.targetState] of the [visibleState], which triggers the animation.
     */
    LaunchedEffect(isVisible) { visibleState.targetState = isVisible }

    /**
     * Watches [visibleState] to track jank for the appear and disappear animations.
     *
     * When the disappear animation is complete, this calls
     * [SecureLockDeviceBiometricAuthContentViewModel.onDisappearAnimationFinished] to allow the
     * legacy keyguard to delay dismissal of the biometric auth composable until the animations on
     * the UI have finished playing on the UI.
     */
    LaunchedEffect(visibleState.currentState, visibleState.targetState, visibleState.isIdle) {
        // TODO(b/436359935) report interaction jank metrics
    }

    /** Animates the biometric auth content in and out of view. */
    AnimatedVisibility(
        visibleState = visibleState,
        enter =
            fadeIn(tween(durationMillis = TO_BOUNCER_DURATION.toInt(DurationUnit.MILLISECONDS))),
        exit = fadeOut(tween(durationMillis = TO_GONE_DURATION.toInt(DurationUnit.MILLISECONDS))),
        modifier = modifier,
    ) {
        val iconSize: Pair<Int, Int> = secureLockDeviceViewModel.iconViewModel.iconSizeState
        val iconBottomPadding =
            dimensionResource(R.dimen.biometric_prompt_portrait_medium_bottom_padding)

        Box(modifier = Modifier.fillMaxSize()) {
            BiometricIconLottie(
                viewModel = secureLockDeviceViewModel,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = iconBottomPadding)
                        .width { iconSize.first }
                        .height { iconSize.second },
            )
        }
    }
}

@Composable
private fun BiometricIconLottie(
    viewModel: SecureLockDeviceBiometricAuthContentViewModel,
    modifier: Modifier = Modifier,
) {
    val iconViewModel = viewModel.iconViewModel
    val iconState = viewModel.iconViewModel.hydratedIconState
    val iconContentDescription = stringResource(iconState.contentDescriptionId)
    val showingError = iconViewModel.showingErrorState

    val lottie by
        rememberLottieComposition(
            spec = LottieCompositionSpec.RawRes(iconState.asset),
            cacheKey = iconState.asset.toString(),
        )

    val animatingFromSfpsAuthenticating =
        BiometricAuthIconAssets.animatingFromSfpsAuthenticating(iconState.asset)
    val minFrame: Int =
        if (animatingFromSfpsAuthenticating) {
            // Skipping to error / success / unlock segment of animation
            158
        } else {
            0
        }

    val numIterations =
        if (iconState.shouldLoop) {
            LottieConstants.IterateForever
        } else {
            1
        }

    LottieAnimation(
        composition = lottie,
        dynamicProperties = LottieColorUtils.getDynamicProperties(bpColors()),
        modifier =
            modifier
                .graphicsLayer { rotationZ = iconState.rotation }
                .semantics { contentDescription = iconContentDescription },
        isPlaying = iconState.shouldAnimate,
        iterations = numIterations,
        clipSpec = LottieClipSpec.Frame(min = minFrame),
        contentScale = ContentScale.FillBounds,
    )

    SideEffect { iconViewModel.setPreviousIconWasError(showingError) }
}
