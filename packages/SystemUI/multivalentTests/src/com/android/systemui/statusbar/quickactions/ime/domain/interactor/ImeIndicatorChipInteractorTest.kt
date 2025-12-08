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

package com.android.systemui.statusbar.quickactions.ime.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class ImeIndicatorChipInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val fakeInputMethodRepository = kosmos.fakeInputMethodRepository
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.imeIndicatorChipInteractor }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagDisabled_isNotVisible() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            assertThat(underTest.chipModel.value.isVisible).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagEnabled_isVisible() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            assertThat(underTest.chipModel.value.isVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_selectedSubtypeUpdated_updatesChipModel() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            assertThat(underTest.chipModel.value.selectedSubtype).isNull()

            val subtype = InputMethodModel.Subtype(subtypeId = 123, isAuxiliary = false)
            fakeInputMethodRepository.selectedInputMethodSubtypes = listOf(subtype)
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            assertThat(underTest.chipModel.value.selectedSubtype).isEqualTo(subtype)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun showInputMethodPicker_showsPicker() =
        kosmos.runTest {
            val displayId = 1
            underTest.showInputMethodPicker(displayId)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isEqualTo(displayId)
        }
}
