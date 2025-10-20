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

package com.android.systemui.screencapture.record.domain.interactor

import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.data.repository.ScreenCaptureRecordParametersRepository
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.data.repository.ScreenRecordingServiceRepository
import javax.inject.Inject

@ScreenCaptureScope
class ScreenCaptureRecordParametersInteractor
@Inject
constructor(
    private val serviceRepository: ScreenRecordingServiceRepository,
    private val repository: ScreenCaptureRecordParametersRepository,
) {

    val parameters = repository.parameters

    fun setAudioSource(audioSource: ScreenRecordingAudioSource) {
        serviceRepository.updateAudioSource(audioSource)
        repository.updateParameters { it.copy(audioSource = audioSource) }
    }

    fun setShouldShowTaps(shouldShowTaps: Boolean) {
        serviceRepository.updateShouldShowTaps(shouldShowTaps)
        repository.updateParameters { it.copy(shouldShowTaps = shouldShowTaps) }
    }

    fun setShouldShowFrontCamera(shouldShowFrontCamera: Boolean) {
        repository.updateParameters { it.copy(shouldShowFrontCamera = shouldShowFrontCamera) }
    }
}
