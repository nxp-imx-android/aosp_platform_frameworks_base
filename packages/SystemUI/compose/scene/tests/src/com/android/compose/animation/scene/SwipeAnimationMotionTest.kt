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

import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffectFactory
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.RecordedMotion
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlFn
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.feature
import platform.test.motion.compose.hasMotionTestValue
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.asDataPoint
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
class SwipeAnimationMotionTest {

    private val goldenPaths =
        createGoldenPathManager("frameworks/base/packages/SystemUI/compose/scene/tests/goldens")

    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths)
    private val composeRule = motionRule.toolkit.composeContentTestRule

    @Test
    fun dragGesture_lowVelocity_animatesWithSpring() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(
                    initialScene = SceneA,
                    transitions = transitions { from(SceneA, to = SceneB) },
                ) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(endX = centerX, durationMillis = 250)
                    }
                }

            motionRule.assertThat(motion).timeSeriesMatchesGolden()
        }

    @Test
    @Ignore("Need to understand the immediate dispatch issue in test")
    fun dragGesture_overdrag_animatesBack() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(
                    initialScene = SceneA,
                    transitions = transitions { from(SceneA, to = SceneB) },
                ) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeRight(startX = 10f, endX = width * 1.5f, durationMillis = 250)
                    }
                }

            motionRule.assertThat(motion).timeSeriesMatchesGolden()
        }

    @Test
    fun flingGesture_highVelocity_overshoots() =
        motionRule.runTest {
            val motion =
                recordSceneTransition(
                    initialScene = SceneA,
                    transitions = transitions { from(SceneA, to = SceneB) },
                ) {
                    performTouchInputAsync(onNodeWithTag("stl")) {
                        swipeWithVelocity(
                            start = centerLeft,
                            end = Offset(width * .8f, centerY),
                            durationMillis = 60,
                            endVelocity = 2000.dp.toPx(),
                        )
                    }
                }

            motionRule.assertThat(motion).timeSeriesMatchesGolden()
        }

    val overscrollDistanceKey = MotionTestValueKey<Float>("overscrollDistance")

    @Composable
    private fun ContentScope.TestScene(alignment: Alignment) {
        Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
            Box(
                Modifier.overscroll(horizontalOverscrollEffect)
                    .element(TestElements.Foo)
                    .motionTestValues {
                        val overscrollDistance =
                            (horizontalOverscrollEffect as OffsetOverscrollEffect)
                                .overscrollDistance
                        overscrollDistance exportAs overscrollDistanceKey
                    }
                    .size(10.dp)
                    .align(alignment)
                    .background(Color.Red)
            )
        }
    }

    val floatArrayType = DataPointTypes.listOf(DataPointTypes.float)

    private fun recordSceneTransition(
        initialScene: SceneKey,
        transitions: SceneTransitions,
        recording: MotionControlFn,
    ): RecordedMotion {
        lateinit var state: MutableSceneTransitionLayoutState
        return motionRule.recordMotion(
            content = {
                state = rememberMutableSceneTransitionLayoutState(initialScene, transitions)

                val factory = rememberOffsetOverscrollEffectFactory()
                CompositionLocalProvider(LocalOverscrollFactory provides factory) {
                    Box(modifier = Modifier.padding(20.dp)) {
                        SceneTransitionLayout(
                            state = state,
                            modifier =
                                Modifier.size(100.dp).testTag("stl").background(Color.Yellow),
                            implicitTestTags = true,
                        ) {
                            scene(TestScenes.SceneA, userActions = mapOf(Swipe.Right to SceneB)) {
                                TestScene(Alignment.TopStart)
                            }
                            scene(TestScenes.SceneB) { TestScene(Alignment.TopEnd) }
                        }
                    }
                }
            },
            ComposeRecordingSpec(
                MotionControl {
                    recording()
                    awaitCondition { !state.isTransitioning() }
                },
                recordBefore = false,
                recordAfter = false,
                captureScreenshots = true,
                timeSeriesCapture = {
                    on({
                        it.onAllNodes(isElement(TestElements.Foo))
                            .fetchSemanticsNodes()
                            .filter { it.layoutInfo.isPlaced }
                            .firstOrNull()
                    }) {
                        feature(FeatureCaptures.elementOffset)
                        feature(ComposeFeatureCaptures.positionInRoot)
                    }

                    feature(
                        overscrollDistanceKey,
                        DataPointTypes.float,
                        matcher =
                            hasMotionTestValue(overscrollDistanceKey) and
                                SemanticsMatcher.inContent(SceneB),
                    )

                    on({ state.currentTransition }) {
                        feature("progress") { it.progress.asDataPoint() }
                        feature("dragOffset") {
                            checkNotNull(it.gestureContext).dragOffset.asDataPoint()
                        }
                    }
                },
            ),
        )
    }
}
