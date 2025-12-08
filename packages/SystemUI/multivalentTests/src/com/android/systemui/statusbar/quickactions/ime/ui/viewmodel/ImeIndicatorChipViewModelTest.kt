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

package com.android.systemui.statusbar.quickactions.ime.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class ImeIndicatorChipViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val fakeInputMethodRepository = kosmos.fakeInputMethodRepository
    private val Kosmos.underTest by
        Kosmos.Fixture { imeIndicatorChipViewModelFactory.create().apply { activateIn(testScope) } }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagDisabled_isHidden() =
        kosmos.runTest {
            assertThat(underTest.chip).isInstanceOf(QuickActionChipUiState.Hidden::class.java)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagEnabled_isShown() =
        kosmos.runTest {
            assertThat(underTest.chip).isInstanceOf(QuickActionChipUiState.PopupChip::class.java)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_subtypeSelected_showsSubtypeId() =
        kosmos.runTest {
            val subtype = InputMethodModel.Subtype(subtypeId = 123, isAuxiliary = false)
            fakeInputMethodRepository.selectedInputMethodSubtypes = listOf(subtype)
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            val chip = underTest.chip as QuickActionChipUiState.PopupChip

            // TODO(b/458557858): This should use the IME icon or subtype short label instead if
            // those are available.
            assertThat(chip.chipText).isEqualTo(subtype.subtypeId.toString())
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_showPopup_callsShowInputMethodPicker() =
        kosmos.runTest {
            val chip = underTest.chip
            assertThat(chip).isInstanceOf(QuickActionChipUiState.PopupChip::class.java)
            val shownChip = chip as QuickActionChipUiState.PopupChip

            shownChip.showPopup()
            testScope.runCurrent()

            // TODO(b/458557860): Should be shown on the display containing the chip.
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isEqualTo(Display.DEFAULT_DISPLAY)
        }
}
