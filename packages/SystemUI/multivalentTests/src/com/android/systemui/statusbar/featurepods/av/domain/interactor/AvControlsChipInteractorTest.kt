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

package com.android.systemui.statusbar.featurepods.av.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.statusbar.data.repository.fakeStatusBarModeRepository
import com.android.systemui.statusbar.featurepods.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.av.shared.model.SensorActivityModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AvControlsChipInteractorTest() : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { avControlsChipInteractorImpl }

    private val cameraItem =
        PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("fakepackage", 0))
    private val microphoneItem =
        PrivacyItem(PrivacyType.TYPE_MICROPHONE, PrivacyApplication("fakepackage", 0))

    private fun cameraModel() =
        AvControlsChipModel(SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA))

    private fun microphoneModel() =
        AvControlsChipModel(
            SensorActivityModel.Active(SensorActivityModel.Active.Sensors.MICROPHONE)
        )

    private fun cameraAndMicrophoneModel() =
        AvControlsChipModel(
            SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE)
        )

    private fun inactiveModel() = AvControlsChipModel(SensorActivityModel.Inactive)

    private fun Kosmos.lastModel(): AvControlsChipModel? =
        collectLastValue(underTest.model).invoke()

    @Before
    fun setUp() {
        kosmos.underTest.initialize()
    }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun flagOn_disabled() =
        kosmos.runTest { assertThat(underTest.isEnabled.value).isEqualTo(false) }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun flagOn_enabled() = kosmos.runTest { assertThat(underTest.isEnabled.value).isEqualTo(true) }

    @Test fun defaultModel() = kosmos.runTest { assertThat(lastModel()).isEqualTo(inactiveModel()) }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            assertThat(lastModel()).isEqualTo(cameraModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_flagOff_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun microphoneActiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            assertThat(lastModel()).isEqualTo(microphoneModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun microphoneActive_flagOff_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraAndMicrophoneActiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))
            assertThat(lastModel()).isEqualTo(cameraAndMicrophoneModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraAndMicrophoneActive_flagOff_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_noFullscreen_shouldSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = false
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(true)
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_fullscreen_shouldNotSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = true
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(false)
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_flagOff_noFullscreen_shouldNotSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = false
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(false)
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_flagOff_fullscreen_shouldNotSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = true
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(false)
        }
}
