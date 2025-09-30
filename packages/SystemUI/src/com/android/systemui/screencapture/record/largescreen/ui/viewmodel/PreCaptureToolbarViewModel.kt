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
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.record.largescreen.domain.interactor.LargeScreenCaptureFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.LargeScreenCaptureParametersInteractor
import com.android.systemui.screencapture.record.ui.viewmodel.ScreenCaptureRecordParametersViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Models the UI for the large-screen pre-capture toolbar. */
class PreCaptureToolbarViewModel
@AssistedInject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val uiEventLogger: UiEventLogger,
    private val iconProvider: ScreenCaptureIconProvider,
    featuresInteractor: LargeScreenCaptureFeaturesInteractor,
    recordParametersViewModelFactory: ScreenCaptureRecordParametersViewModel.Factory,
    private val largeScreenCaptureParametersInteractor: LargeScreenCaptureParametersInteractor,
) : HydratedActivatable() {
    private val toolbarBoundsSource = MutableStateFlow(Rect())
    private val toolbarOpacitySource = MutableStateFlow(1f)

    val recordParametersViewModel = recordParametersViewModelFactory.create()

    val icons: ScreenCaptureIcons? by iconProvider.icons.hydratedStateOf()

    val appWindowRegionSupported = featuresInteractor.appWindowRegionSupported

    val screenRecordingSupported = featuresInteractor.screenRecordingSupported

    val customSaveLocationSupported = featuresInteractor.customSaveLocationSupported

    val customSaveLocationUriString: String by
        largeScreenCaptureParametersInteractor.customSaveLocationUriString.hydratedStateOf(
            initialValue = ""
        )

    val toolbarOpacity: Float by toolbarOpacitySource.hydratedStateOf()

    fun setToolbarBounds(bounds: Rect) {
        toolbarBoundsSource.value = bounds
    }

    /**
     * Updates the toolbar opacity based on whether the region box selection intersects with the
     * toolbar, and whether the region box is resized or moved.
     *
     * @param isInteracting Whether the region box is currently resized or moved.
     * @param regionBoxBounds The current bounds of the region box.
     */
    fun updateOpacityForRegionBox(isInteracting: Boolean, regionBoxBounds: Rect?) {
        if (isInteracting) {
            toolbarOpacitySource.value = 0f
            return
        }

        // When interaction stops, or a region is selected, calculate the final opacity.
        if (regionBoxBounds == null) {
            toolbarOpacitySource.value = 1f
            return
        }

        val toolbarBounds = toolbarBoundsSource.value
        if (!toolbarBounds.isEmpty && Rect.intersects(regionBoxBounds, toolbarBounds)) {
            toolbarOpacitySource.value = 0.15f
        } else {
            toolbarOpacitySource.value = 1f
        }
    }

    fun updateCustomSaveLocationUriString(uriString: String) {
        backgroundScope.launch {
            largeScreenCaptureParametersInteractor.setCustomSaveLocation(uriString)
        }
    }

    fun recordClose() {
        uiEventLogger.log(ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_CLOSE_UI_WITHOUT_CAPTURE)
    }

    override suspend fun onActivated() {
        coroutineScope {
            launch { iconProvider.collectIcons() }
            launch { recordParametersViewModel.activate() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): PreCaptureToolbarViewModel
    }
}
