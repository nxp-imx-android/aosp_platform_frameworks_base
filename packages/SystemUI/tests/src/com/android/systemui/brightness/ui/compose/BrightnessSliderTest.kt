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

package com.android.systemui.brightness.ui.compose

import android.content.res.Configuration
import android.platform.test.annotations.WithDesktopTest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.brightness.data.repository.fakeScreenBrightnessRepository
import com.android.systemui.brightness.shared.model.LinearBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.brightnessSliderViewModelFactory
import com.android.systemui.brightness.ui.viewmodel.create
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.haptics.slider.sliderHapticsViewModelFactory
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.utils.PolicyRestriction
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BrightnessSliderTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    private val kosmos = testKosmos()

    private val englishLocaleConfiguration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.US) }

    private val franceLocaleConfiguration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }

    private var localeConfiguration by mutableStateOf(englishLocaleConfiguration)

    @Test
    fun stateDescription_hasPercentage() {
        val value = 25
        val range = 0..200
        composeRule.setContent {
            PlatformTheme {
                CompositionLocalProvider(LocalConfiguration provides localeConfiguration) {
                    BrightnessSlider(
                        gammaValue = value,
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        valueRange = range,
                        iconResProvider = BrightnessSliderViewModel::getIconForPercentage,
                        imageLoader = { resId, context ->
                            context.getDrawable(resId)!!.asIcon(null)
                        },
                        restriction = PolicyRestriction.NoRestriction,
                        onRestrictedClick = {},
                        onDrag = {},
                        onStop = {},
                        overriddenByAppState = false,
                        hapticsViewModelFactory = kosmos.sliderHapticsViewModelFactory,
                    )
                }
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_brightness))
            .assert(hasStateDescription("12%"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithDesktopTest
    fun verifySize() = with(kosmos) {
        testScope.runTest {
            val viewModel = brightnessSliderViewModelFactory.create()
            viewModel.activateIn(this)

            // Set the brightness value so that the brightness slider container will be drawn.
            with(fakeScreenBrightnessRepository) {
                setBrightness(LinearBrightness(25f))
                setMinMaxBrightness(LinearBrightness(0f), LinearBrightness(100f))
            }

            runCurrent()
            composeRule.setContent {
                PlatformTheme {
                    BrightnessSliderContainer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth(),
                        containerColors = ContainerColors(
                            Color.Transparent,
                            ContainerColors.defaultContainerColor
                        )
                    )
                }
            }

            // Verify the brightness slider's height.
            composeRule
                .onNodeWithTag(resIdToTestTag("slider")).assertHeightIsEqualTo(52.dp)

            // Verify the brightness slider's vertical padding.
            composeRule
                .onNodeWithTag(resIdToTestTag("brightness_slider"))
                .assertTopPositionInRootIsEqualTo(6.dp)
        }
    }

    @Test
    fun stateDescription_updatesWithValue() {
        var value by mutableIntStateOf(25)
        val range = 0..200
        composeRule.setContent {
            PlatformTheme {
                CompositionLocalProvider(LocalConfiguration provides localeConfiguration) {
                    BrightnessSlider(
                        gammaValue = value,
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        valueRange = range,
                        iconResProvider = BrightnessSliderViewModel::getIconForPercentage,
                        imageLoader = { resId, context ->
                            context.getDrawable(resId)!!.asIcon(null)
                        },
                        restriction = PolicyRestriction.NoRestriction,
                        onRestrictedClick = {},
                        onDrag = {},
                        onStop = {},
                        overriddenByAppState = false,
                        hapticsViewModelFactory = kosmos.sliderHapticsViewModelFactory,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        value = 150
        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_brightness))
            .assert(hasStateDescription("75%"))
    }

    @Test
    fun stateDescription_updatesWithConfiguration() {
        val value = 25
        val range = 0..200
        composeRule.setContent {
            PlatformTheme {
                CompositionLocalProvider(LocalConfiguration provides localeConfiguration) {
                    BrightnessSlider(
                        gammaValue = value,
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        valueRange = range,
                        iconResProvider = BrightnessSliderViewModel::getIconForPercentage,
                        imageLoader = { resId, context ->
                            context.getDrawable(resId)!!.asIcon(null)
                        },
                        restriction = PolicyRestriction.NoRestriction,
                        onRestrictedClick = {},
                        onDrag = {},
                        onStop = {},
                        overriddenByAppState = false,
                        hapticsViewModelFactory = kosmos.sliderHapticsViewModelFactory,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        localeConfiguration = franceLocaleConfiguration
        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_brightness))
            .assert(hasStateDescription("12 %")) // extra NBSP
    }
}
