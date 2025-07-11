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
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.largescreen.domain.interactor.ScreenCaptureRecordLargeScreenFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.ScreenshotInteractor
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
    @Application private val applicationContext: Context,
    @Background private val backgroundScope: CoroutineScope,
    private val iconProvider: ScreenCaptureIconProvider,
    private val screenshotInteractor: ScreenshotInteractor,
    private val featuresInteractor: ScreenCaptureRecordLargeScreenFeaturesInteractor,
) : HydratedActivatable() {
    private val captureTypeSource = MutableStateFlow(ScreenCaptureType.SCREENSHOT)
    private val captureRegionSource = MutableStateFlow(ScreenCaptureRegion.FULLSCREEN)

    val icons: ScreenCaptureIcons? by iconProvider.icons.hydratedStateOf()

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureType: ScreenCaptureType by captureTypeSource.hydratedStateOf()

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureRegion: ScreenCaptureRegion by captureRegionSource.hydratedStateOf()

    val screenRecordingSupported = featuresInteractor.screenRecordingSupported

    val captureTypeButtonViewModels: List<RadioButtonGroupItemViewModel> by
        combine(captureTypeSource, iconProvider.icons) { selectedType, icons ->
                generateCaptureTypeButtonViewModels(selectedType, icons)
            }
            .hydratedStateOf(
                initialValue = generateCaptureTypeButtonViewModels(captureTypeSource.value, null)
            )

    val captureRegionButtonViewModels: List<RadioButtonGroupItemViewModel> by
        combine(captureRegionSource, iconProvider.icons) { selectedRegion, icons ->
                generateCaptureRegionButtonViewModels(selectedRegion, icons)
            }
            .hydratedStateOf(
                initialValue =
                    generateCaptureRegionButtonViewModels(captureRegionSource.value, null)
            )

    fun updateCaptureType(selectedType: ScreenCaptureType) {
        captureTypeSource.value = selectedType
    }

    fun updateCaptureRegion(selectedRegion: ScreenCaptureRegion) {
        captureRegionSource.value = selectedRegion
    }

    fun takeFullscreenScreenshot() {
        require(captureTypeSource.value == ScreenCaptureType.SCREENSHOT)
        require(captureRegionSource.value == ScreenCaptureRegion.FULLSCREEN)

        backgroundScope.launch {
            // TODO(b/430361425) Pass in current display as argument.
            screenshotInteractor.takeFullscreenScreenshot()
        }

        // TODO(b/427500006) Close the window after requesting a fullscreen screenshot.
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
                icon = icons?.screenshotToolbar,
                label =
                    applicationContext.getString(R.string.screen_capture_toolbar_capture_button),
                isSelected = selectedType == ScreenCaptureType.SCREENSHOT,
                onClick = { updateCaptureType(ScreenCaptureType.SCREENSHOT) },
            ),
        )
    }

    private fun generateCaptureRegionButtonViewModels(
        selectedRegion: ScreenCaptureRegion,
        icons: ScreenCaptureIcons?,
    ): List<RadioButtonGroupItemViewModel> {
        return buildList {
            if (featuresInteractor.appWindowRegionSupported) {
                add(
                    RadioButtonGroupItemViewModel(
                        icon = icons?.appWindow,
                        isSelected = (selectedRegion == ScreenCaptureRegion.APP_WINDOW),
                        onClick = { updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW) },
                    )
                )
            }

            add(
                RadioButtonGroupItemViewModel(
                    icon = icons?.region,
                    isSelected = (selectedRegion == ScreenCaptureRegion.PARTIAL),
                    onClick = { updateCaptureRegion(ScreenCaptureRegion.PARTIAL) },
                )
            )

            add(
                RadioButtonGroupItemViewModel(
                    icon = icons?.fullscreen,
                    isSelected = (selectedRegion == ScreenCaptureRegion.FULLSCREEN),
                    onClick = { updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN) },
                )
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): PreCaptureViewModel
    }
}
