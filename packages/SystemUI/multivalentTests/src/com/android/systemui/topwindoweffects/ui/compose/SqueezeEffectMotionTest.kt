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

import android.platform.test.annotations.MotionTest
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyevent.data.repository.fakeKeyEventRepository
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl
import com.android.systemui.topwindoweffects.ui.viewmodel.squeezeEffectViewModelFactory
import com.android.wm.shell.appzoomout.appZoomOutOptional
import com.android.wm.shell.appzoomout.fakeAppZoomOut
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.asDataPoint

@RunWith(AndroidJUnit4::class)
@LargeTest
@MotionTest
class SqueezeEffectMotionTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos)

    @DrawableRes private val topResId = R.drawable.rounded_corner_top
    @DrawableRes private val bottomResId = R.drawable.rounded_corner_bottom

    @Composable
    private fun SqueezeEffectUnderTest(onEffectFinished: () -> Unit) {
        SqueezeEffect(
            viewModelFactory = kosmos.squeezeEffectViewModelFactory,
            topRoundedCornerResourceId = topResId,
            bottomRoundedCornerResourceId = bottomResId,
            physicalPixelDisplaySizeRatio = 1f,
            onEffectFinished = onEffectFinished,
            appZoomOutOptional = kosmos.appZoomOutOptional,
            interactionJankMonitor = kosmos.interactionJankMonitor,
            onEffectStarted = {},
        )
    }

    @Test
    fun testSqueezeEffectMotion() =
        motionTestRule.runTest(timeout = 30.seconds) {
            kosmos.fakeKeyEventRepository.setPowerButtonDown(true)
            kosmos.fakeKeyEventRepository.setPowerButtonLongPressed(true)
            var effectFinished = false
            val motion =
                recordMotion(
                    content = { play ->
                        if (play) {
                            SqueezeEffectUnderTest(onEffectFinished = { effectFinished = true })
                        }
                    },
                    ComposeRecordingSpec.until(checkDone = { effectFinished }) {
                        feature(
                            FeatureCapture("topLevelZoom") {
                                kosmos.fakeAppZoomOut.lastTopLevelScale.asDataPoint()
                            }
                        )
                        feature(MotionTestKeys.squeezeThickness, DataPointTypes.float)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }

    @Test
    fun testSqueezeEffectCancelMotion() =
        motionTestRule.runTest(timeout = 30.seconds) {
            kosmos.fakeKeyEventRepository.setPowerButtonDown(true)
            kosmos.fakeKeyEventRepository.setPowerButtonLongPressed(false)
            var effectFinished = false

            val motionControl = MotionControl {
                awaitDelay(SqueezeEffectRepositoryImpl.DEFAULT_INITIAL_DELAY_MILLIS.milliseconds)
                kosmos.fakeKeyEventRepository.setPowerButtonDown(false)
                awaitCondition { effectFinished }
            }

            val motion =
                recordMotion(
                    content = { play ->
                        if (play) {
                            SqueezeEffectUnderTest(onEffectFinished = { effectFinished = true })
                        }
                    },
                    ComposeRecordingSpec(motionControl = motionControl) {
                        feature(
                            FeatureCapture("topLevelZoom") {
                                kosmos.fakeAppZoomOut.lastTopLevelScale.asDataPoint()
                            }
                        )
                        feature(MotionTestKeys.squeezeThickness, DataPointTypes.float)
                    },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
}
