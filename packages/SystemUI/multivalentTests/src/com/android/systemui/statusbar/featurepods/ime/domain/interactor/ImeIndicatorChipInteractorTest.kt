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

package com.android.systemui.statusbar.featurepods.ime.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class ImeIndicatorChipInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.imeIndicatorChipInteractor }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagDisabled_isNotVisible() =
        kosmos.runTest {
            backgroundScope.launch { underTest.isChipVisible.collect {} }
            assertThat(underTest.isChipVisible.value).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagEnabled_isVisible() =
        kosmos.runTest {
            backgroundScope.launch { underTest.isChipVisible.collect {} }
            assertThat(underTest.isChipVisible.value).isTrue()
        }
}
