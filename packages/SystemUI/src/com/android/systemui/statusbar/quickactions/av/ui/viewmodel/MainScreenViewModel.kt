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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class MainScreenViewModel
@AssistedInject
constructor(
    val sensorActivityViewModelFactory: SensorActivityViewModel.Factory,
    val cameraGlobalSwitchViewModelFactory: CameraGlobalSwitchViewModel.Factory,
    val microphoneGlobalSwitchViewModelFactory: MicrophoneGlobalSwitchViewModel.Factory,
    val studioLookButtonViewModelFactory: StudioLookButtonViewModel.Factory,
    val blurButtonViewModelFactory: BlurButtonViewModel.Factory,
    val studioMicViewModelFactory: StudioMicViewModel.Factory,
    val liveCaptionsViewModelFactory: LiveCaptionsViewModel.Factory,
    val cameraFramingViewModelFactory: CameraFramingViewModel.Factory,
    @Assisted val setCurrentScreen: (Screen) -> Unit,
) : HydratedActivatable() {

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentScreen: (Screen) -> Unit): MainScreenViewModel
    }
}
