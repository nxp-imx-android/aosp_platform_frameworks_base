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

package com.android.systemui.screencapture.record.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenCaptureCameraHintInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordParametersInteractor
import com.android.systemui.screencapture.record.smallscreen.domain.interactor.RecordDetailsTargetInteractor
import com.android.systemui.screencapture.record.smallscreen.shared.model.currentTargetModel
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ScreenCaptureRecordParametersViewModel
@AssistedInject
constructor(
    private val interactor: ScreenCaptureRecordParametersInteractor,
    screenRecordCameraInteractor: ScreenRecordCameraInteractor,
    screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
    recordDetailsTargetInteractor: RecordDetailsTargetInteractor,
    private val screenCaptureCameraHintInteractor: ScreenCaptureCameraHintInteractor,
) : HydratedActivatable() {

    val audioSource: ScreenRecordingAudioSource by interactor::audioSource
    val canChangeAudioSource: Boolean by
        interactor.canChangeAudioSource.hydratedStateOf(
            "ScreenCaptureAudioSourceViewModel#canChangeAudioSource"
        )

    var shouldShowTaps: Boolean by interactor::shouldShowTaps
    var shouldShowFrontCamera: Boolean by interactor::shouldShowFrontCamera

    var shouldRecordDevice: Boolean
        get() =
            with(interactor) {
                audioSource == ScreenRecordingAudioSource.MIC_AND_INTERNAL ||
                    audioSource == ScreenRecordingAudioSource.INTERNAL
            }
        set(value) {
            interactor.audioSource =
                if (value) {
                    if (shouldRecordMicrophone) {
                        ScreenRecordingAudioSource.MIC_AND_INTERNAL
                    } else {
                        ScreenRecordingAudioSource.INTERNAL
                    }
                } else {
                    if (shouldRecordMicrophone) {
                        ScreenRecordingAudioSource.MIC
                    } else {
                        ScreenRecordingAudioSource.NONE
                    }
                }
        }

    var shouldRecordMicrophone: Boolean
        get() =
            with(interactor) {
                audioSource == ScreenRecordingAudioSource.MIC_AND_INTERNAL ||
                    audioSource == ScreenRecordingAudioSource.MIC
            }
        set(value) {
            interactor.audioSource =
                if (value) {
                    if (shouldRecordDevice) {
                        ScreenRecordingAudioSource.MIC_AND_INTERNAL
                    } else {
                        ScreenRecordingAudioSource.MIC
                    }
                } else {
                    if (shouldRecordDevice) {
                        ScreenRecordingAudioSource.INTERNAL
                    } else {
                        ScreenRecordingAudioSource.NONE
                    }
                }
        }

    val canUseFrontCamera: Boolean by
        if (screenCaptureRecordFeaturesInteractor.isSelfieAvailable) {
                combine(
                    recordDetailsTargetInteractor.model.map { it.currentTargetModel.canUseCamera },
                    screenRecordCameraInteractor.isCameraSupported,
                ) { canUseCameraForTarget, isCameraSupported ->
                    canUseCameraForTarget && isCameraSupported
                }
            } else {
                flowOf(false)
            }
            .hydratedStateOf("ScreenCaptureAudioSourceViewModel#canUseFrontCamera", true)

    val shouldShowHint: Boolean by derivedStateOf {
        screenCaptureCameraHintInteractor.shouldShowHint
    }

    suspend fun onCameraHintShown() {
        screenCaptureCameraHintInteractor.onHintShown()
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureRecordParametersViewModel
    }
}
