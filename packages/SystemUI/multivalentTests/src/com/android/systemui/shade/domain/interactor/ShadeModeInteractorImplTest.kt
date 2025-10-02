/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.display.data.repository.displayStateRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeModeInteractorImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { shadeModeInteractor }

    @Test
    fun legacyShadeMode_narrowScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_narrowLargeScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            // This simulates the case of a tablet or certain unfolded foldables in portrait mode.
            enableSingleShade(wideLayout = false)
            displayStateRepository.setIsLargeScreen(true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_wideScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun legacyShadeMode_wideScreen_splitShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSplitShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun defaultShadeMode_splitShadeOverridden_dualShade() =
        kosmos.runTest {
            enableSplitShade()
            val shadeMode by collectLastValue(underTest.shadeMode)
            assertThat(shadeMode).isEqualTo(ShadeMode.Split)

            overrideResource(R.bool.config_useDualShadeSetting, false)
            overrideResource(R.bool.config_dualShadeEnabledByDefault, true)
            fakeConfigurationRepository.onConfigurationChange()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun defaultShadeMode_singleShadeOverridden_dualShade() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(underTest.shadeMode)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            overrideResource(R.bool.config_useDualShadeSetting, false)
            overrideResource(R.bool.config_dualShadeEnabledByDefault, true)
            fakeConfigurationRepository.onConfigurationChange()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_wideScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_narrowScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isDualShade_settingEnabledSceneContainerEnabled_returnsTrue() =
        kosmos.runTest {
            // TODO(b/391578667): Add a test case for user switching once the bug is fixed.
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isDualShade_settingDisabled_returnsFalse() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            disableDualShade()

            assertThat(shadeMode).isNotEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_largeScreenPortrait() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Large screen portrait
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_largeScreenLandscape() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Large screen landscape
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = true)

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_compactScreenPortrait() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Compact screen portrait
            setupScreenConfig(wideScreen = false, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isFullWidthShade_compactScreenLandscape() =
        kosmos.runTest {
            val isFullWidthShade by collectLastValue(underTest.isFullWidthShade)

            // Compact screen landscape
            setupScreenConfig(wideScreen = true, legacyUseSplitShade = false)

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = true, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isFalse()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = true)
            assertThat(isFullWidthShade).isTrue()

            setupShadeConfig(dualShadeSettingEnabled = false, dualShadeEnabledByDefault = false)
            assertThat(isFullWidthShade).isTrue()
        }

    @Test
    fun notificationStackHorizontalAlignment_singleShade_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSingleShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_splitShade_endAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSplitShade()

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_dualShadeNarrow_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = false)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_dualShadeWide_startAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_desktopWithTopEndConfig_endAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)

            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun notificationStackHorizontalAlignment_desktopWithoutTopEndConfig_startAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)

            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }

    private fun Kosmos.setupScreenConfig(wideScreen: Boolean, legacyUseSplitShade: Boolean) {
        overrideResource(R.bool.config_isFullWidthShade, !wideScreen)
        overrideResource(R.bool.config_use_split_notification_shade, legacyUseSplitShade)
        overrideResource(R.bool.config_use_large_screen_shade_header, legacyUseSplitShade)
        fakeConfigurationRepository.onConfigurationChange()
    }

    private fun Kosmos.setupShadeConfig(
        dualShadeSettingEnabled: Boolean,
        dualShadeEnabledByDefault: Boolean,
    ) = runBlocking {
        fakeSecureSettingsRepository.setBoolean(Settings.Secure.DUAL_SHADE, dualShadeSettingEnabled)
        overrideResource(R.bool.config_dualShadeEnabledByDefault, dualShadeEnabledByDefault)
        fakeConfigurationRepository.onConfigurationChange()
    }
}
