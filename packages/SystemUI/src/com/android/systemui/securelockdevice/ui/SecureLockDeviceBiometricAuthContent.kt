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
package com.android.systemui.securelockdevice.ui

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.compose.modifiers.height
import com.android.compose.modifiers.width
import com.android.systemui.biometrics.BiometricAuthIconAssets
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val TO_BOUNCER_DURATION = 400.milliseconds
private val TO_GONE_DURATION = 500.milliseconds

@Composable
fun SecureLockDeviceContent(
    secureLockDeviceViewModel: SecureLockDeviceBiometricAuthContentViewModel,
    modifier: Modifier = Modifier,
) {
    val isVisible by secureLockDeviceViewModel.isVisible.collectAsStateWithLifecycle(false)
    val visibleState = remember { MutableTransitionState(isVisible) }

    // Feeds the isVisible value to the MutableTransitionState used by AnimatedVisibility below.
    LaunchedEffect(isVisible) {
        visibleState.targetState = isVisible
        if (isVisible) {
            // Start appear animation
            // TODO: start CUJ_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR
        }
    }

    // Watches the MutableTransitionState and calls onHideAnimationFinished when the authenticated
    // animation is finished. This way the window view is removed from the view hierarchy only after
    // the animation is complete.
    LaunchedEffect(visibleState.currentState, visibleState.targetState, visibleState.isIdle) {
        if (visibleState.currentState && !visibleState.targetState) { // Disappear animation started
            // TODO: start CUJ_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR
        } else if (visibleState.currentState && visibleState.isIdle) { // Appear animation complete
            // TODO: end CUJ_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR
        } else if (
            !visibleState.currentState && visibleState.isIdle
        ) { // Disappear animation complete
            // TODO: end CUJ_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter =
            fadeIn(tween(durationMillis = TO_BOUNCER_DURATION.toInt(DurationUnit.MILLISECONDS))),
        exit = fadeOut(tween(durationMillis = TO_GONE_DURATION.toInt(DurationUnit.MILLISECONDS))),
        modifier = modifier,
    ) {
        val iconSize by
            secureLockDeviceViewModel.iconViewModel.iconSize.collectAsStateWithLifecycle(Pair(0, 0))
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
    val iconAsset by iconViewModel.iconAsset.collectAsStateWithLifecycle(-1)
    val iconViewRotation by iconViewModel.iconViewRotation.collectAsStateWithLifecycle(0f)
    val iconContentDescription by iconViewModel.contentDescription.collectAsStateWithLifecycle("")
    val shouldAnimateIconView by
        iconViewModel.shouldAnimateIconView.collectAsStateWithLifecycle(false)
    val shouldLoopIconView by iconViewModel.shouldLoopIconView.collectAsStateWithLifecycle(false)
    val showingError by iconViewModel.showingError.collectAsStateWithLifecycle(false)

    val lottie by rememberLottieComposition(LottieCompositionSpec.RawRes(iconAsset))
    if (lottie == null) return

    val animatingFromSfpsAuthenticating =
        BiometricAuthIconAssets.animatingFromSfpsAuthenticating(iconAsset)
    val minFrame: Int =
        if (animatingFromSfpsAuthenticating) {
            // Skipping to error / success / unlock segment of animation
            158
        } else {
            0
        }
    // TODO: figure out lottie dynamic coloring

    val numIterations =
        if (shouldLoopIconView) {
            LottieConstants.IterateForever
        } else {
            1
        }

    LottieAnimation(
        composition = lottie,
        modifier =
            modifier
                .graphicsLayer { rotationZ = iconViewRotation }
                .semantics { contentDescription = iconContentDescription },
        isPlaying = shouldAnimateIconView,
        iterations = numIterations,
        clipSpec = LottieClipSpec.Frame(min = minFrame),
        contentScale = ContentScale.FillBounds,
    )

    SideEffect { iconViewModel.setPreviousIconWasError(showingError) }
}
