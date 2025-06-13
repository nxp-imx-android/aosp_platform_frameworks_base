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

package com.android.systemui.screencapture.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow

enum class ScreenCaptureType {
    SCREENSHOT,
    SCREEN_RECORD,
}

enum class ScreenCaptureRegion {
    FULLSCREEN,
    PARTIAL,
    APP_WINDOW,
}

/** Models state for the Screen Capture UI */
class ScreenCaptureViewModel @AssistedInject constructor() : ExclusiveActivatable() {
    private val hydrator = Hydrator("ScreenCaptureViewModel.hydrator")

    private val captureTypeSource = MutableStateFlow(ScreenCaptureType.SCREENSHOT)
    private val captureRegionSource = MutableStateFlow(ScreenCaptureRegion.FULLSCREEN)

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureType by
        hydrator.hydratedStateOf(
            traceName = "captureType",
            initialValue = ScreenCaptureType.SCREENSHOT,
            source = captureTypeSource,
        )

    // TODO(b/423697394) Init default value to be user's previously selected option
    val captureRegion by
        hydrator.hydratedStateOf(
            traceName = "captureRegion",
            initialValue = ScreenCaptureRegion.FULLSCREEN,
            source = captureRegionSource,
        )

    fun updateCaptureType(selectedType: ScreenCaptureType) {
        captureTypeSource.value = selectedType
    }

    fun updateCaptureRegion(selectedRegion: ScreenCaptureRegion) {
        captureRegionSource.value = selectedRegion
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureViewModel
    }
}
