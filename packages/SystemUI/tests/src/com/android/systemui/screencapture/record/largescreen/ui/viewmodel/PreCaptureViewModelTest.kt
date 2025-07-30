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
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.ui.mockScreenCaptureActivity
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
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
    private val viewModel: PreCaptureViewModel by lazy { kosmos.preCaptureViewModel }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel.activateIn(testScope)
    }

    @Test
    fun initialState() =
        testScope.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.isShowingUI).isTrue()
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun displayId_isDerivedFromActivity() =
        testScope.runTest {
            val displayId = 9001
            whenever(kosmos.mockScreenCaptureActivity.displayId).thenReturn(displayId)
            assertThat(viewModel.displayId).isEqualTo(displayId)
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
    fun takeFullscreenScreenshot_callsScreenshotInteractor_withCorrectRequest() =
        testScope.runTest {
            val displayId = 3
            whenever(kosmos.mockScreenCaptureActivity.displayId).thenReturn(displayId)

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.takeFullscreenScreenshot()

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
    fun takeFullscreenScreenshot_validatesCaptureType() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            assertFailsWith(IllegalArgumentException::class) {
                viewModel.takeFullscreenScreenshot()
            }
        }

    @Test
    fun takeFullscreenScreenshot_validatesCaptureRegion() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            assertFailsWith(IllegalArgumentException::class) {
                viewModel.takeFullscreenScreenshot()
            }
        }

    @Test
    fun takePartialScreenshot_callsScreenshotInteractor_withCorrectRequest() =
        testScope.runTest {
            val displayId = 3
            whenever(kosmos.mockScreenCaptureActivity.displayId).thenReturn(displayId)

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBox(regionBox)

            whenever(kosmos.mockImageCapture.captureDisplay(any(), eq(regionBox)))
                .thenReturn(mockBitmap)

            viewModel.takePartialScreenshot()

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
    fun takePartialScreenshot_validatesCaptureType() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREEN_RECORD)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            assertFailsWith(IllegalArgumentException::class) { viewModel.takePartialScreenshot() }
        }

    @Test
    fun takePartialScreenshot_validatesCaptureRegion() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            assertFailsWith(IllegalArgumentException::class) { viewModel.takePartialScreenshot() }
        }

    @Test
    fun takePartialScreenshot_validatesRegionBoxIsNotNull() =
        testScope.runTest {
            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            // viewModel.regionBox is null by default
            assertFailsWith(IllegalArgumentException::class) { viewModel.takePartialScreenshot() }
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
    fun hideUI_stopsShowingUI() =
        testScope.runTest {
            viewModel.hideUI()

            assertThat(viewModel.isShowingUI).isFalse()
        }

    @Test
    fun closeUI_finishesActivity() =
        testScope.runTest {
            viewModel.closeUI()

            verify(kosmos.mockScreenCaptureActivity, times(1)).finish()
        }
}
