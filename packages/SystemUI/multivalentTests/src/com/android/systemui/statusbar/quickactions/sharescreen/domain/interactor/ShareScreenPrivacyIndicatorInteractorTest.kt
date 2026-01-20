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

package com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ShareScreenPrivacyIndicatorInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.shareScreenPrivacyIndicatorInteractor }

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_largeScreenPrivacyIndicator,
            true,
        )
        kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
            MediaProjectionState.NotProjecting
    }

    @Test
    fun isChipVisible_initiallyFalse() =
        kosmos.runTest { assertThat(currentValue(underTest.isChipVisible)).isFalse() }

    @Test
    fun isChipVisible_onMediaProjectionStarted_true() =
        kosmos.runTest {
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isFalse()

            // Simulate media projection starting.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            assertThat(isChipVisible).isTrue()
        }

    @Test
    fun isChipVisible_onMediaProjectionStopped_false() =
        kosmos.runTest {
            // Start with a projecting state
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isTrue()

            // Simulate media projection stopping.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun isChipVisible_largeScreenFlagFalse_alwaysFalse() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                R.bool.config_largeScreenPrivacyIndicator,
                false,
            )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isFalse()

            // Simulate media projection starting.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            // Chip should still be invisible.
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun stopShare_hidesChipAndStopsProjection() =
        kosmos.runTest {
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isTrue()

            underTest.stopShare()

            // Stopping projection is not synchronous, so we need to manually update the state
            // in the fake repository to simulate the projection ending.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting

            assertThat(kosmos.fakeMediaProjectionRepository.stopProjectingInvoked).isTrue()
            assertThat(isChipVisible).isFalse()
        }
}
