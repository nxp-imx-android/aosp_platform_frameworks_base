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

import android.content.Context
import android.os.UserHandle
import android.widget.Toast
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.data.repository.ScreenCaptureUiRepository
import com.android.systemui.user.data.repository.UserRepository
import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@SysUISingleton
class ScreenCaptureUiInteractor
@Inject
constructor(
    @Application private val context: Context,
    deviceStateRepository: ScreenCaptureDeviceStateRepository,
    private val repository: ScreenCaptureUiRepository,
    private val userRepository: UserRepository,
    private val devicePolicyResolver: Lazy<ScreenCaptureDevicePolicyResolver>,
    @Main private val mainExecutor: Executor,
) {

    val isLargeScreen: Flow<Boolean?> = deviceStateRepository.isLargeScreen

    fun uiState(type: ScreenCaptureType): StateFlow<ScreenCaptureUiState> = repository.uiState(type)

    fun show(parameters: ScreenCaptureUiParameters) {
        if (
            devicePolicyResolver
                .get()
                .isScreenCaptureCompletelyDisabled(
                    UserHandle.of(userRepository.getSelectedUserInfo().id)
                )
        ) {
            mainExecutor.execute {
                Toast.makeText(
                        context,
                        R.string.screen_capture_blocked_by_admin,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
            return
        }

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
