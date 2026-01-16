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

import android.graphics.Region
import android.media.projection.StopReason
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.core.graphics.toRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.record.camera.data.repository.fakeScreenRecordCameraRepository
import com.android.systemui.screencapture.record.camera.domain.interactor.screenCaptureCameraTransformationInteractor
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
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

    private val underTest by lazy {
        kosmos.screenCaptureCameraTransformationViewModel.apply { activateIn(kosmos.testScope) }
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
    fun isTransforming_isPropagated() =
        kosmos.runTest {
            underTest.onTransformationStarted()
            assertThat(screenCaptureCameraTransformationInteractor.isTransforming).isTrue()

            underTest.onTransformationEnded()
            assertThat(screenCaptureCameraTransformationInteractor.isTransforming).isFalse()
        }

    @Test
    fun recordingInProgress_useCameraBounds() =
        kosmos.runTest {
            val cameraScreenBounds = Rect(0f, 0f, 1080f, 1920f)
            screenRecordingServiceInteractor.startRecording(
                ScreenRecordingParameters(
                    captureTarget = null,
                    audioSource = ScreenRecordingAudioSource.NONE,
                    displayId = 0,
                    shouldShowTaps = false,
                )
            )
            fakeScreenRecordCameraRepository.setCameraRegion(Region(20, 20, 100, 100))
            underTest.onCameraScreenBoundsUpdated(cameraScreenBounds)
            underTest.state.transform {
                transformBy(
                    centroid = cameraScreenBounds.center,
                    zoomChange = 0.01f,
                    panChange = Offset(10f, 10f),
                    rotationChange = 10f,
                )
            }

            assertThat(underTest.transformableByTouchAnywhere).isFalse()
            val touchableRegion = Region().also(underTest::fillCameraInteractableRegion)
            assertThat(touchableRegion.bounds).isEqualTo(Region(546, 960, 547, 961).bounds)
        }

    @Test
    fun noRecordingInProgress_useGlobalBounds() =
        kosmos.runTest {
            val cameraScreenBounds = Rect(0f, 0f, 1080f, 1920f)
            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
            underTest.onCameraScreenBoundsUpdated(cameraScreenBounds)
            underTest.state.transform {
                transformBy(
                    centroid = cameraScreenBounds.center,
                    zoomChange = 10f,
                    panChange = Offset(10f, 10f),
                    rotationChange = 10f,
                )
            }

            assertThat(underTest.transformableByTouchAnywhere).isTrue()
            val touchableRegion = Region().also(underTest::fillCameraInteractableRegion)
            assertThat(touchableRegion.bounds)
                .isEqualTo(cameraScreenBounds.toAndroidRectF().toRect())
        }
}
