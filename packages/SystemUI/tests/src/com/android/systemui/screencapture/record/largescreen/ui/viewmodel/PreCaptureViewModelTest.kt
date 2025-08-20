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

import android.graphics.Bitmap
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.screenCaptureUiRepository
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    @Mock private lateinit var mockBitmap: Bitmap
    private val displayId = 1234
    private val viewModel: PreCaptureViewModel by lazy {
        kosmos.preCaptureViewModelFactory.create(displayId)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel.activateIn(testScope)
    }

    @Test
    fun initialState() =
        testScope.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.isShowingUi).isTrue()
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
    fun updateCaptureType_usesCorrectIconWhenSelected() =
        testScope.runTest {
            val (screenRecordButton, screenshotButton) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton.icon).isEqualTo(viewModel.icons?.screenRecord)
            // Screenshot is selected by default.
            assertThat(screenshotButton.icon).isEqualTo(viewModel.icons?.screenshotToolbar)

            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)

            val (screenRecordButton2, screenshotButton2) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton2.icon).isEqualTo(viewModel.icons?.screenRecord)
            assertThat(screenshotButton2.icon)
                .isEqualTo(viewModel.icons?.screenshotToolbarUnselected)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun updateCaptureRegion_updatesSelectedCaptureRegionButton() =
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

    @Test
    fun updateRegionBox_updatesState() =
        testScope.runTest {
            // State is initially null.
            assertThat(viewModel.regionBox).isNull()

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBox(regionBox)

            assertThat(viewModel.regionBox).isEqualTo(regionBox)
        }

    @Test
    fun beginCapture_forFullscreenScreenshot_makesCorrectRequest() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }

    @Test
    fun beginCapture_forPartialScreenshot_makesCorrectRequest() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBox(regionBox)

            whenever(kosmos.mockImageCapture.captureDisplay(any(), eq(regionBox)))
                .thenReturn(mockBitmap)

            viewModel.beginCapture()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.boundsInScreen).isEqualTo(regionBox)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_excludesAppWindowWithFeatureDisabled() =
        testScope.runTest {
            // TODO(b/430364500) Once a11y label is available, use it for a more robust assertion.
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            assertThat(viewModel.captureRegionButtonViewModels.none { it.isSelected }).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_includesAppWindowWithFeatureEnabled() =
        testScope.runTest {
            // TODO(b/430364500) Once a11y label is available, use it for a more robust assertion.
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            assertThat(viewModel.captureRegionButtonViewModels.count { it.isSelected }).isEqualTo(1)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_hasScreenshotContentDescriptions_byDefault() =
        testScope.runTest {
            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels

            // Default capture type is SCREENSHOT.
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            assertThat(appWindowButton.contentDescription)
                .isEqualTo(
                    context.getString(
                        R.string.screen_capture_toolbar_app_window_button_screenshot_a11y
                    )
                )
            assertThat(partialButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_region_button_screenshot_a11y)
                )
            assertThat(fullscreenButton.contentDescription)
                .isEqualTo(
                    context.getString(
                        R.string.screen_capture_toolbar_fullscreen_button_screenshot_a11y
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_hasRecordContentDescriptions_whenCaptureTypeIsRecord() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)

            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels

            assertThat(appWindowButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_app_window_button_record_a11y)
                )
            assertThat(partialButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_region_button_record_a11y)
                )
            assertThat(fullscreenButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_fullscreen_button_record_a11y)
                )
        }

    @Test
    fun hideUi_updatesState() =
        testScope.runTest {
            viewModel.hideUi()

            assertThat(viewModel.isShowingUi).isFalse()
        }

    @Test
    fun closeUi_dismissesWindow() =
        testScope.runTest {
            val uiState by
                collectLastValue(
                    kosmos.screenCaptureUiRepository.uiState(
                        com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
                            .RECORD
                    )
                )

            viewModel.closeUi()

            assertThat(uiState).isEqualTo(ScreenCaptureUiState.Invisible)
        }
}
