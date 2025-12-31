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

package com.android.systemui.screencapture.record.domain.interactor

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.data.repository.screenCaptureRecordParametersRepository
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.map
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ScreenCaptureRecordParametersInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val underTest by lazy { kosmos.screenCaptureRecordParametersInteractor }

    @Test
    fun changingAudioSource() =
        kosmos.runTest {
            val newAudioSource = ScreenRecordingAudioSource.MIC_AND_INTERNAL
            val audioSource by collectLastValue(underTest.parameters.map { it.audioSource })
            assertThat(audioSource).isNotEqualTo(newAudioSource)

            underTest.setAudioSource(newAudioSource)

            assertThat(audioSource).isEqualTo(newAudioSource)
        }

    @Test
    fun changingAudioSource_recordingIsOngoing_doesntChange() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(
                ScreenRecordingParameters(
                    captureTarget = null,
                    displayId = Display.DEFAULT_DISPLAY,
                    audioSource = ScreenRecordingAudioSource.NONE,
                    shouldShowTaps = false,
                )
            )
            val newAudioSource = ScreenRecordingAudioSource.MIC_AND_INTERNAL
            val audioSource by collectLastValue(underTest.parameters.map { it.audioSource })
            assertThat(audioSource).isNotEqualTo(newAudioSource)

            underTest.setAudioSource(newAudioSource)

            assertThat(audioSource).isNotEqualTo(newAudioSource)
        }

    @Test
    fun changingShouldShowTaps() =
        kosmos.runTest {
            val newShouldShowTaps = true
            val shouldShowTaps by collectLastValue(underTest.parameters.map { it.shouldShowTaps })
            assertThat(shouldShowTaps).isNotEqualTo(newShouldShowTaps)

            underTest.setShouldShowTaps(newShouldShowTaps)

            assertThat(shouldShowTaps).isEqualTo(newShouldShowTaps)
        }

    @Test
    fun changingShouldShowFrontCamera() =
        kosmos.runTest {
            val newShouldShowFrontCamera = true
            val shouldShowFrontCamera by
                collectLastValue(underTest.parameters.map { it.shouldShowFrontCamera })
            assertThat(shouldShowFrontCamera).isNotEqualTo(newShouldShowFrontCamera)

            underTest.setShouldShowFrontCamera(newShouldShowFrontCamera)

            assertThat(shouldShowFrontCamera).isEqualTo(newShouldShowFrontCamera)
        }

    @Test
    fun setShouldShowFrontCamera_nowNone_enablesMic() =
        kosmos.runTest {
            val parameters by collectLastValue(screenCaptureRecordParametersRepository.parameters)
            underTest.setAudioSource(ScreenRecordingAudioSource.NONE)

            underTest.setShouldShowFrontCamera(true)

            assertThat(parameters!!.shouldShowFrontCamera).isTrue()
            assertThat(parameters!!.audioSource).isEqualTo(ScreenRecordingAudioSource.MIC)
        }

    @Test
    fun setShouldShowFrontCamera_nowInternal_enablesMic() =
        kosmos.runTest {
            val parameters by collectLastValue(screenCaptureRecordParametersRepository.parameters)
            underTest.setAudioSource(ScreenRecordingAudioSource.INTERNAL)

            underTest.setShouldShowFrontCamera(true)

            assertThat(parameters!!.shouldShowFrontCamera).isTrue()
            assertThat(parameters!!.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.MIC_AND_INTERNAL)
        }

    @Test
    fun setShouldNotShowFrontCamera_doesntChangeMic() =
        kosmos.runTest {
            ScreenRecordingAudioSource.entries.forEach { source ->
                val parameters by
                    collectLastValue(screenCaptureRecordParametersRepository.parameters)
                underTest.setAudioSource(source)

                underTest.setShouldShowFrontCamera(false)

                assertThat(parameters!!.shouldShowFrontCamera).isFalse()
                assertThat(parameters!!.audioSource).isEqualTo(source)
            }
        }
}
