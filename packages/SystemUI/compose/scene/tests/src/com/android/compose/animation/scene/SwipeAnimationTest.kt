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

package com.android.compose.animation.scene

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.mechanics.ProvidedGestureContext
import com.android.mechanics.spec.InputDirection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.runMonotonicClockTest

@RunWith(AndroidJUnit4::class)
class SwipeAnimationTest {
    @Test
    fun animationSlowerThanDecay() {
        assertThat(
                willDecayFasterThanAnimating(
                    // High animation duration.
                    animationSpec = tween(durationMillis = 1_000),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 0f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isTrue()
    }

    @Test
    fun animationFasterThanDecay() {
        assertThat(
                willDecayFasterThanAnimating(
                    // Low animation duration.
                    animationSpec = tween(durationMillis = 1),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 0f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isFalse()
    }

    @Test
    fun sameInitialAndTargetOffset() {
        assertThat(
                willDecayFasterThanAnimating(
                    // Low animation duration.
                    animationSpec = tween(durationMillis = 1),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 1_000f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isTrue()
    }

    @Test
    // Regression test for b/439715141.
    fun smallDeltaToTarget() {
        val density = Density(1f, 1f)

        // The exact same values as in b/439715141.
        val initialOffset = 0.049804688f
        val targetOffset = 0f
        val initialVelocity = -8528.837f

        val decaySpec =
            SplineBasedFloatDecayAnimationSpec(density).generateDecayAnimationSpec<Float>()
        val animationSpec = spring<Float>()

        willDecayFasterThanAnimating(
            animationSpec,
            decaySpec,
            initialOffset,
            targetOffset,
            initialVelocity,
        )
    }

    @Test
    // Regression test for b/462102178.
    fun animateOffsetCalledOnce() = runMonotonicClockTest {
        val swipeAnimation =
            SwipeAnimation(
                    layoutState = MutableSceneTransitionLayoutStateForTests(SceneA),
                    fromContent = SceneA,
                    toContent = SceneB,
                    isUpOrLeft = false,
                    requiresFullDistanceSwipe = false,
                    distance = { 100f },
                    gestureContext =
                        ProvidedGestureContext(dragOffset = 100f, direction = InputDirection.Max),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(Density(1f)).generateDecayAnimationSpec(),
                )
                .apply {
                    val swipeAnimation = this
                    contentTransition =
                        object : TransitionState.Transition.ChangeScene(SceneA, SceneB) {
                            override val progress: Float = 0f
                            override val progressVelocity: Float = 0f
                            override val isInitiatedByUserInput: Boolean = true
                            override val isUserInputOngoing: Boolean = true
                            override val currentScene: SceneKey = SceneA

                            override suspend fun run() {
                                swipeAnimation.run()
                            }

                            override fun freezeAndAnimateToCurrentState() {
                                swipeAnimation.freezeAndAnimateToCurrentState()
                            }
                        }
                }

        launch(start = CoroutineStart.UNDISPATCHED) {
            swipeAnimation.contentTransition.runInternal()
        }

        swipeAnimation.freezeAndAnimateToCurrentState()
        if (!swipeAnimation.isAnimatingOffset()) {
            swipeAnimation.animateOffset(initialVelocity = 0f, targetContent = SceneB)
        }
    }
}
