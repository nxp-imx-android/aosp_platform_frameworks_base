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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureToolbarViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private lateinit var viewModel: PreCaptureToolbarViewModel

    @Before
    fun setUp() {
        viewModel = kosmos.preCaptureToolbarViewModel
        viewModel.activateIn(kosmos.testScope)
    }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun appWindowRegionSupported_whenFeatureEnabled_isTrue() =
        kosmos.runTest { assertThat(viewModel.appWindowRegionSupported).isTrue() }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun appWindowRegionSupported_whenFeatureDisabled_isFalse() =
        kosmos.runTest { assertThat(viewModel.appWindowRegionSupported).isFalse() }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun screenRecordingSupported_whenFeatureEnabled_isTrue() =
        kosmos.runTest { assertThat(viewModel.screenRecordingSupported).isTrue() }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun screenRecordingSupported_whenFeatureDisabled_isFalse() =
        kosmos.runTest { assertThat(viewModel.screenRecordingSupported).isFalse() }

    @Test
    fun updateOpacityForRegionBox_isInteracting_opacityIsZero() =
        kosmos.runTest {
            viewModel.updateOpacityForRegionBox(isInteracting = true, regionBoxBounds = null)

            assertThat(viewModel.toolbarOpacity).isEqualTo(0f)
        }

    @Test
    fun updateOpacityForRegionBox_notInteracting_noOverlap_opacityIsOne() =
        kosmos.runTest {
            viewModel.setToolbarBounds(Rect(0, 0, 100, 100))

            viewModel.updateOpacityForRegionBox(
                isInteracting = false,
                regionBoxBounds = Rect(200, 200, 300, 300),
            )

            assertThat(viewModel.toolbarOpacity).isEqualTo(1f)
        }

    @Test
    fun updateOpacityForRegionBox_notInteracting_overlap_opacityIsPoint15() =
        kosmos.runTest {
            viewModel.setToolbarBounds(Rect(0, 0, 100, 100))

            viewModel.updateOpacityForRegionBox(
                isInteracting = false,
                regionBoxBounds = Rect(50, 50, 150, 150),
            )

            assertThat(viewModel.toolbarOpacity).isEqualTo(0.15f)
        }

    @Test
    fun recordClose_logsEvent() =
        kosmos.runTest {
            viewModel.recordClose()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_CLOSE_UI_WITHOUT_CAPTURE.id
                )
        }

    @Test
    fun customSaveLocationUriString_initialValue_isEmpty() =
        kosmos.runTest { assertThat(viewModel.customSaveLocationUriString).isEmpty() }

    @Test
    fun recordParametersViewModel_updatesAudioSourceState() =
        kosmos.runTest {
            assertThat(viewModel.recordParametersViewModel.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.NONE)

            viewModel.recordParametersViewModel.shouldRecordMicrophone = true

            assertThat(viewModel.recordParametersViewModel.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.MIC)

            viewModel.recordParametersViewModel.shouldRecordDevice = true

            assertThat(viewModel.recordParametersViewModel.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.MIC_AND_INTERNAL)

            viewModel.recordParametersViewModel.shouldRecordMicrophone = false

            assertThat(viewModel.recordParametersViewModel.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.INTERNAL)

            viewModel.recordParametersViewModel.shouldRecordDevice = false

            assertThat(viewModel.recordParametersViewModel.audioSource)
                .isEqualTo(ScreenRecordingAudioSource.NONE)
        }

    @Test
    fun recordParametersViewModel_updatesShowTapsState() =
        kosmos.runTest {
            assertThat(viewModel.recordParametersViewModel.shouldShowTaps).isFalse()

            viewModel.recordParametersViewModel.setShouldShowTaps(true)

            assertThat(viewModel.recordParametersViewModel.shouldShowTaps).isTrue()
        }
}
