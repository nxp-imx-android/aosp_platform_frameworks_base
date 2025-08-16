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

package com.android.systemui.screencapture.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureActivityIntentParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureUiRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class ScreenCaptureUiInteractor
@Inject
constructor(private val repository: ScreenCaptureUiRepository) {

    fun uiState(type: ScreenCaptureType): Flow<ScreenCaptureUiState> = repository.uiState(type)

    fun show(parameters: ScreenCaptureActivityIntentParameters) {
        repository.updateStateForType(type = parameters.screenCaptureType) {
            if (it is ScreenCaptureUiState.Visible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Visible(parameters)
            }
        }
    }

    fun hide(type: ScreenCaptureType) {
        repository.updateStateForType(type) {
            if (it is ScreenCaptureUiState.Invisible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Invisible
            }
        }
    }
}
