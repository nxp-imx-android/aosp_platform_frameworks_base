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
}
