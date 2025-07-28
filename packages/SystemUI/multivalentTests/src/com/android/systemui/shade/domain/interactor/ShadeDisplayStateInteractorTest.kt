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

package com.android.systemui.shade.domain.interactor

import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.FakeDisplayStateRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.createFakeDisplaySubcomponent
import com.android.systemui.display.data.repository.displayStateRepository
import com.android.systemui.display.data.repository.displaySubcomponentPerDisplayRepository
import com.android.systemui.display.domain.interactor.createDisplayStateInteractor
import com.android.systemui.display.domain.interactor.displayStateInteractor
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
class ShadeDisplayStateInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val secondaryDisplayStateRepository = FakeDisplayStateRepository()
    private val secondaryDisplayStateInteractor =
        kosmos.createDisplayStateInteractor(secondaryDisplayStateRepository)
    private val defaultDisplayStateRepository = FakeDisplayStateRepository()
    private val defaultDisplayStateInteractor =
        kosmos.createDisplayStateInteractor(defaultDisplayStateRepository)

    private val underTest: ShadeDisplayStateInteractor by lazy {
        kosmos.shadeDisplayStateInteractor
    }

    @Before
    fun setUp() {
        kosmos.displaySubcomponentPerDisplayRepository.apply {
            add(
                DEFAULT_DISPLAY,
                kosmos.createFakeDisplaySubcomponent(
                    displayStateRepository = defaultDisplayStateRepository,
                    displayStateInteractor = defaultDisplayStateInteractor,
                ),
            )
            add(
                SECONDARY_DISPLAY,
                kosmos.createFakeDisplaySubcomponent(
                    displayStateRepository = secondaryDisplayStateRepository,
                    displayStateInteractor = secondaryDisplayStateInteractor,
                ),
            )
        }
    }

    @Test
    fun isWideScreen_afterDisplayChange_returnsCorrectValue() =
        kosmos.runTest {
            defaultDisplayStateRepository.setIsWideScreen(false)
            secondaryDisplayStateRepository.setIsWideScreen(true)

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            val isWideScreen by collectLastValue(underTest.isWideScreen)

            assertThat(isWideScreen).isFalse()

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)

            assertThat(isWideScreen).isTrue()

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            assertThat(isWideScreen).isFalse()
        }

    @Test
    fun isLargeScreen_afterDisplayChange_returnsCorrectValue() =
        kosmos.runTest {
            defaultDisplayStateRepository.setIsLargeScreen(false)
            secondaryDisplayStateRepository.setIsLargeScreen(true)

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            val isLargeScreen by collectLastValue(underTest.isLargeScreen)

            assertThat(isLargeScreen).isFalse()

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)

            assertThat(isLargeScreen).isTrue()

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            assertThat(isLargeScreen).isFalse()
        }

    private companion object {
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
        const val SECONDARY_DISPLAY = DEFAULT_DISPLAY + 1
    }
}
