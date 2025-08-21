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

import android.content.Context
import android.graphics.Rect
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.LargeScreenCaptureFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.ScreenshotInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class ScreenCaptureType {
    SCREENSHOT,
    SCREEN_RECORD,
}

enum class ScreenCaptureRegion {
    FULLSCREEN,
    PARTIAL,
    APP_WINDOW,
}

/** Models UI for the Screen Capture UI for large screen devices. */
class PreCaptureViewModel
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    @Application private val applicationContext: Context,
    @Background private val backgroundScope: CoroutineScope,
    private val iconProvider: ScreenCaptureIconProvider,
    private val screenshotInteractor: ScreenshotInteractor,
    private val featuresInteractor: LargeScreenCaptureFeaturesInteractor,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    private val isShowingUiFlow = MutableStateFlow(true)
    private val captureTypeSource = MutableStateFlow(ScreenCaptureType.SCREENSHOT)
    private val captureRegionSource = MutableStateFlow(ScreenCaptureRegion.FULLSCREEN)
    private val regionBoxSource = MutableStateFlow<Rect?>(null)

    val icons: ScreenCaptureIcons? by iconProvider.icons.hydratedStateOf()

    val isShowingUi: Boolean by isShowingUiFlow.hydratedStateOf()

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureType: ScreenCaptureType by captureTypeSource.hydratedStateOf()

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureRegion: ScreenCaptureRegion by captureRegionSource.hydratedStateOf()

    val regionBox: Rect? by regionBoxSource.hydratedStateOf()

    val screenRecordingSupported = featuresInteractor.screenRecordingSupported

    val captureTypeButtonViewModels: List<RadioButtonGroupItemViewModel> by
        combine(captureTypeSource, iconProvider.icons) { selectedType, icons ->
                generateCaptureTypeButtonViewModels(selectedType, icons)
            }
            .hydratedStateOf(
                initialValue = generateCaptureTypeButtonViewModels(captureTypeSource.value, null)
            )

    val captureRegionButtonViewModels: List<RadioButtonGroupItemViewModel> by
        combine(captureRegionSource, captureTypeSource, iconProvider.icons) {
                selectedRegion,
                selectedCaptureType,
                icons ->
                generateCaptureRegionButtonViewModels(selectedRegion, selectedCaptureType, icons)
            }
            .hydratedStateOf(
                initialValue =
                    generateCaptureRegionButtonViewModels(
                        captureRegionSource.value,
                        captureTypeSource.value,
                        null,
                    )
            )

    fun updateCaptureType(selectedType: ScreenCaptureType) {
        captureTypeSource.value = selectedType
    }

    fun updateCaptureRegion(selectedRegion: ScreenCaptureRegion) {
        captureRegionSource.value = selectedRegion
    }

    fun updateRegionBox(bounds: Rect) {
        regionBoxSource.value = bounds
    }

    /** Initiates capture of the screen depending on the currently chosen capture type. */
    fun beginCapture() {
        when (captureTypeSource.value) {
            ScreenCaptureType.SCREENSHOT -> takeScreenshot()
            ScreenCaptureType.SCREEN_RECORD -> {}
        }
    }

    private fun takeScreenshot() {
        when (captureRegionSource.value) {
            ScreenCaptureRegion.FULLSCREEN -> takeFullscreenScreenshot()
            ScreenCaptureRegion.PARTIAL -> takePartialScreenshot()
            ScreenCaptureRegion.APP_WINDOW -> {}
        }
    }

    private fun takeFullscreenScreenshot() {
        // Finishing the activity is not guaranteed to complete before the screenshot is taken.
        // Since the pre-capture UI should not be included in the screenshot, hide the UI first.
        hideUi()
        closeUi()

        backgroundScope.launch { screenshotInteractor.takeFullscreenScreenshot(displayId) }
    }

    private fun takePartialScreenshot() {
        val regionBoxRect = requireNotNull(regionBoxSource.value)

        // Finishing the activity is not guaranteed to complete before the screenshot is taken.
        // Since the pre-capture UI should not be included in the screenshot, hide the UI first.
        hideUi()
        closeUi()

        backgroundScope.launch {
            screenshotInteractor.takePartialScreenshot(regionBoxRect, displayId)
        }
    }

    /**
     * Simply hides all Composables from being visible, which avoids the parent window close
     * animation. This is useful to ensure the UI is not visible before a screenshot is taken. Note:
     * this does NOT close the parent window. See [closeUi] for closing the window.
     */
    fun hideUi() {
        isShowingUiFlow.value = false
    }

    /** Closes the UI by hiding the parent window. */
    fun closeUi() {
        screenCaptureUiInteractor.hide(
            com.android.systemui.screencapture.common.shared.model.ScreenCaptureType.RECORD
        )
    }

    override suspend fun onActivated() {
        coroutineScope { launch { iconProvider.collectIcons() } }
    }

    private fun generateCaptureTypeButtonViewModels(
        selectedType: ScreenCaptureType,
        icons: ScreenCaptureIcons?,
    ): List<RadioButtonGroupItemViewModel> {
        return listOf(
            RadioButtonGroupItemViewModel(
                icon = icons?.screenRecord,
                label = applicationContext.getString(R.string.screen_capture_toolbar_record_button),
                isSelected = selectedType == ScreenCaptureType.SCREEN_RECORD,
                onClick = { updateCaptureType(ScreenCaptureType.SCREEN_RECORD) },
            ),
            RadioButtonGroupItemViewModel(
                selectedIcon = icons?.screenshotToolbar,
                unselectedIcon = icons?.screenshotToolbarUnselected,
                label =
                    applicationContext.getString(R.string.screen_capture_toolbar_screenshot_button),
                isSelected = selectedType == ScreenCaptureType.SCREENSHOT,
                onClick = { updateCaptureType(ScreenCaptureType.SCREENSHOT) },
            ),
        )
    }

    private fun generateCaptureRegionButtonViewModels(
        selectedRegion: ScreenCaptureRegion,
        selectedCaptureType: ScreenCaptureType,
        icons: ScreenCaptureIcons?,
    ): List<RadioButtonGroupItemViewModel> {
        return buildList {
            if (featuresInteractor.appWindowRegionSupported) {
                add(
                    RadioButtonGroupItemViewModel(
                        icon = icons?.appWindow,
                        isSelected = (selectedRegion == ScreenCaptureRegion.APP_WINDOW),
                        onClick = { updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW) },
                        contentDescription =
                            applicationContext.getString(
                                when (selectedCaptureType) {
                                    ScreenCaptureType.SCREENSHOT ->
                                        R.string
                                            .screen_capture_toolbar_app_window_button_screenshot_a11y
                                    ScreenCaptureType.SCREEN_RECORD ->
                                        R.string
                                            .screen_capture_toolbar_app_window_button_record_a11y
                                }
                            ),
                    )
                )
            }

            add(
                RadioButtonGroupItemViewModel(
                    icon = icons?.region,
                    isSelected = (selectedRegion == ScreenCaptureRegion.PARTIAL),
                    onClick = { updateCaptureRegion(ScreenCaptureRegion.PARTIAL) },
                    contentDescription =
                        applicationContext.getString(
                            when (selectedCaptureType) {
                                ScreenCaptureType.SCREENSHOT ->
                                    R.string.screen_capture_toolbar_region_button_screenshot_a11y
                                ScreenCaptureType.SCREEN_RECORD ->
                                    R.string.screen_capture_toolbar_region_button_record_a11y
                            }
                        ),
                )
            )

            add(
                RadioButtonGroupItemViewModel(
                    icon = icons?.fullscreen,
                    isSelected = (selectedRegion == ScreenCaptureRegion.FULLSCREEN),
                    onClick = { updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN) },
                    contentDescription =
                        applicationContext.getString(
                            when (selectedCaptureType) {
                                ScreenCaptureType.SCREENSHOT ->
                                    R.string
                                        .screen_capture_toolbar_fullscreen_button_screenshot_a11y
                                ScreenCaptureType.SCREEN_RECORD ->
                                    R.string.screen_capture_toolbar_fullscreen_button_record_a11y
                            }
                        ),
                )
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(displayId: Int): PreCaptureViewModel
    }
}
