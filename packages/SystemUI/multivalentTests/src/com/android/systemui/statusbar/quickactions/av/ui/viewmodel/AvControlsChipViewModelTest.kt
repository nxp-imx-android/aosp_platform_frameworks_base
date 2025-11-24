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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.statusbar.quickactions.ui.viewmodel.ChipIcon
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AvControlsChipViewModelTest() : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private lateinit var underTest: AvControlsChipViewModel
    private val cameraItem =
        PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("fakepackage", 0))
    private val microphoneItem =
        PrivacyItem(PrivacyType.TYPE_MICROPHONE, PrivacyApplication("fakepackage", 0))

    @Before
    fun setUp() {
        underTest = kosmos.avControlsChipViewModelFactory.create()
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_initialState_isHidden() = kosmos.runTest { underTest.chip.verifyHidden() }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingCamera_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            underTest.chip.verifyShown().verifyIsCameraOnlyChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingMicrophone_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            underTest.chip.verifyShown().verifyIsMicrophoneOnlyChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingCameraAndMicrophone_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))
            underTest.chip.verifyShown().verifyIsCameraAndMicrophoneChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_chipUpdates() =
        kosmos.runTest {
            underTest.chip.verifyHidden()

            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            underTest.chip.verifyShown().verifyIsCameraOnlyChip()

            fakePrivacyChipRepository.setPrivacyItems(listOf())
            underTest.chip.verifyHidden()

            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            underTest.chip.verifyShown().verifyIsMicrophoneOnlyChip()

            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem, cameraItem))
            underTest.chip.verifyShown().verifyIsCameraAndMicrophoneChip()
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_flagNotEnabled_isHidden() =
        kosmos.runTest {
            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf())

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem, cameraItem))

            underTest.chip.verifyHidden()
        }
}

private fun QuickActionChipUiState.verifyHidden(): QuickActionChipUiState.Hidden {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AvControlsIndicator)
    assertThat(this).isInstanceOf(QuickActionChipUiState.Hidden::class.java)
    return this as QuickActionChipUiState.Hidden
}

private fun QuickActionChipUiState.verifyShown(): QuickActionChipUiState.Shown {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AvControlsIndicator)
    assertThat(this).isInstanceOf(QuickActionChipUiState.Shown::class.java)
    return this as QuickActionChipUiState.Shown
}

private fun QuickActionChipUiState.Shown.verifyHasNoText() {
    assertThat(this.chipText).isEqualTo(null)
}

private fun QuickActionChipUiState.Shown.verifyHasIcon(res: Int) {
    assertThat(this.icons).contains(ChipIcon(Icon.Resource(resId = res, contentDescription = null)))
}

private fun QuickActionChipUiState.Shown.verifyNumberOfIcons(num: Int) {
    assertThat(this.icons.size).isEqualTo(num)
}

private fun QuickActionChipUiState.Shown.verifyIsCameraOnlyChip() {
    verifyNumberOfIcons(1)
    verifyHasIcon(AvControlsChipViewModel.CAMERA_DRAWABLE)
    verifyHasNoText()
}

private fun QuickActionChipUiState.Shown.verifyIsMicrophoneOnlyChip() {
    verifyNumberOfIcons(1)
    verifyHasIcon(AvControlsChipViewModel.MICROPHONE_DRAWABLE)
    verifyHasNoText()
}

private fun QuickActionChipUiState.Shown.verifyIsCameraAndMicrophoneChip() {
    verifyNumberOfIcons(2)
    verifyHasIcon(AvControlsChipViewModel.CAMERA_DRAWABLE)
    verifyHasIcon(AvControlsChipViewModel.MICROPHONE_DRAWABLE)
    verifyHasNoText()
}
