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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.record.largescreen.domain.interactor.ScreenCaptureRecordLargeScreenFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.ScreenshotInteractor
import com.android.systemui.screencapture.ui.ScreenCaptureActivity
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
    @ScreenCapture private val activity: ScreenCaptureActivity,
    @Application private val applicationContext: Context,
    @Background private val backgroundScope: CoroutineScope,
    private val iconProvider: ScreenCaptureIconProvider,
    private val screenshotInteractor: ScreenshotInteractor,
    private val featuresInteractor: ScreenCaptureRecordLargeScreenFeaturesInteractor,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    private val isShowingUIFlow = MutableStateFlow(true)
    private val captureTypeSource = MutableStateFlow(ScreenCaptureType.SCREENSHOT)
    private val captureRegionSource = MutableStateFlow(ScreenCaptureRegion.FULLSCREEN)

    val icons: ScreenCaptureIcons? by iconProvider.icons.hydratedStateOf()

    val isShowingUI: Boolean by isShowingUIFlow.hydratedStateOf()

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

        // Finishing the activity is not guaranteed to complete before the screenshot is taken.
        // Since the pre-capture UI should not be included in the screenshot, hide the UI first.
        hideUI()
        closeUI()

        backgroundScope.launch {
            // TODO(b/430361425) Pass in current display as argument.
            screenshotInteractor.takeFullscreenScreenshot()
        }
    }

    fun onPartialRegionDragEnd(offset: Offset, width: Dp, height: Dp) {
        // TODO(b/427541309) Update region box position and size.
    }

    /**
     * Simply hides all Composables from being visible in the [ScreenCaptureActivity], but does NOT
     * close the activity. See [closeUI] for closing the activity.
     */
    fun hideUI() {
        isShowingUIFlow.value = false
    }

    /** Closes the UI by finishing the parent [ScreenCaptureActivity]. */
    fun closeUI() {
        activity.finish()
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
