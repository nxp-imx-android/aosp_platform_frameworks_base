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

package com.android.systemui.screencapture.record.camera.ui.viewmodel

import android.media.projection.StopReason
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory.screenRecordingParameters
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureCameraTransformationViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val underTest by lazy { kosmos.screenCaptureCameraTransformationViewModel }

    @Test
    fun recordingStarted_transformableByTouchAnywhere_isFalse() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())

            assertThat(underTest.transformableByTouchAnywhere).isFalse()
        }

    @Test
    fun recordingStarting_transformableByTouchAnywhere_isFalse() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecordingDelayed(
                screenRecordingParameters(),
                100.days,
            )

            assertThat(underTest.transformableByTouchAnywhere).isFalse()
        }

    @Test
    fun recordingStopped_transformableByTouchAnywhere_isTrue() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)

            assertThat(underTest.transformableByTouchAnywhere).isTrue()
        }
}
