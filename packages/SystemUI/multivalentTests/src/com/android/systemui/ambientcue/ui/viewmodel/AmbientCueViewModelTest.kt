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

package com.android.systemui.ambientcue.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.domain.interactor.ambientCueInteractor
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val viewModel = kosmos.ambientCueViewModelFactory.create()

    @Before
    fun setUp() {
        viewModel.activateIn(kosmos.testScope)
    }

    @Test
    fun isVisible_timesOut() =
        kosmos.runTest {
            ambientCueInteractor.setIsVisible(true)
            runCurrent()
            assertThat(viewModel.isVisible).isTrue()

            // Times out when there's no interaction
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_SEC)
            runCurrent()
            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun isVisible_whenExpanded_doesntTimeOut() =
        kosmos.runTest {
            ambientCueInteractor.setIsVisible(true)
            runCurrent()
            assertThat(viewModel.isVisible).isTrue()

            // Doesn't time out when expanded
            viewModel.expand()
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_SEC)
            runCurrent()
            assertThat(viewModel.isVisible).isTrue()
        }
}
