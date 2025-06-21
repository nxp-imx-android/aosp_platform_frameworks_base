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

package com.android.systemui.screencapture.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    private val viewModel: ScreenCaptureViewModel by lazy { kosmos.screenCaptureViewModel }

    @Before
    fun setUp() {
        viewModel.activateIn(testScope)
    }

    @Test
    fun initialState() =
        testScope.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun updateCaptureType_updatesState() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREEN_RECORD)
        }

    @Test
    fun updateCaptureRegion_updatesState() =
        testScope.runTest {
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun updateCaptureType_updatesSelectedCaptureTypeButtonViewModel() =
        testScope.runTest {
            // Screenshot type is default selected
            val (screenRecordButton, screenshotButton) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton.isSelected).isFalse()
            assertThat(screenshotButton.isSelected).isTrue()

            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)

            val (screenRecordButton2, screenshotButton2) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton2.isSelected).isTrue()
            assertThat(screenshotButton2.isSelected).isFalse()
        }

    @Test
    fun updateCaptureRegion_updatesSelectedCaptureRegionButtonViewModel() =
        testScope.runTest {
            // Default region is fullscreen
            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton.isSelected).isFalse()
            assertThat(partialButton.isSelected).isFalse()
            assertThat(fullscreenButton.isSelected).isTrue()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val (appWindowButton2, partialButton2, fullscreenButton2) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton2.isSelected).isFalse()
            assertThat(partialButton2.isSelected).isTrue()
            assertThat(fullscreenButton2.isSelected).isFalse()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            val (appWindowButton3, partialButton3, fullscreenButton3) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton3.isSelected).isTrue()
            assertThat(partialButton3.isSelected).isFalse()
            assertThat(fullscreenButton3.isSelected).isFalse()
        }
}
