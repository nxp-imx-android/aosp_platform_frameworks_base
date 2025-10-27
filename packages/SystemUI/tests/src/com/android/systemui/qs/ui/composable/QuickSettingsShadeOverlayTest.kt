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

package com.android.systemui.qs.ui.composable

import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.WithDesktopTest
import android.testing.TestableLooper
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags.FLAG_EXPANDED_AUDIO_DETAILED_VIEW
import com.android.systemui.Flags.FLAG_QS_TILE_DETAILED_VIEW
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.util.FixedActivitySizeComposeTestRule
import kotlin.test.Test
import org.junit.Ignore
import org.junit.Rule
import org.junit.runner.RunWith
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class QuickSettingsShadeOverlayTest : SysuiTestCase() {
    @get:Rule
    val rule = FixedActivitySizeComposeTestRule(
        DeviceEmulationSpec(
            // Use a large display size intentionally to verify the dimens tuned for large screens.
            Displays.External1080p120dpi,
            isLandscape = true,
        )
    )

    val composeTestRule = rule.composeTestRule

    private val kosmos = testKosmos().apply {
        useUnconfinedTestDispatcher()
        featureFlagsClassic.fake.apply { setDefault(FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS) }
    }

    @Test
    @WithDesktopTest
    fun testCommonTileSize() = kosmos.runTest {
        currentTilesInteractor.setTiles(
            listOf(
                TileSpec.create("airplane"),
            )
        )

        composeTestRule.setContent {
            PlatformTheme {
                WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                    with(quickSettingsShadeOverlay) {
                        TestContentScope { Content(Modifier) }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("element:airplane")
            .assertHeightIsEqualTo(72.dp)

        composeTestRule.onNodeWithTag(resIdToTestTag("qs_tile_icon"), useUnmergedTree = true)
            .assertHeightIsEqualTo(32.dp)
    }

    @Test
    @WithDesktopTest
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW, FLAG_EXPANDED_AUDIO_DETAILED_VIEW)
    fun testVolumeSlider() = kosmos.runTest {
        composeTestRule.setContent {
            PlatformTheme {
                WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                    with(quickSettingsShadeOverlay) {
                        TestContentScope { Content(Modifier) }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify the slider's height. "Media" is the tag of the volume slider.
        composeTestRule
            .onNodeWithTag(resIdToTestTag("Media"))
            .assertHeightIsEqualTo(52.dp)
    }
}
