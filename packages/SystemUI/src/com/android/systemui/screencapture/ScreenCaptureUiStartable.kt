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

package com.android.systemui.screencapture

import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.FocusedDisplayRepository
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.ui.ScreenCaptureUi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SysUISingleton
class ScreenCaptureUiStartable
@Inject
constructor(
    @Application private val appScope: CoroutineScope,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val screenCaptureUiFactory: ScreenCaptureUi.Factory,
    private val focusedDisplayRepository: FocusedDisplayRepository,
    private val displayRepository: DisplayRepository,
) : CoreStartable {

    override fun start() {
        ScreenCaptureType.entries.forEach { observeUiState(it) }
    }

    private fun observeUiState(type: ScreenCaptureType) {
        screenCaptureUiInteractor
            .uiState(type)
            .onEach { state ->
                if (state is ScreenCaptureUiState.Visible) {
                    val displayId = focusedDisplayRepository.focusedDisplayId.value
                    val display = displayRepository.getDisplay(displayId)

                    if (display == null) {
                        Log.e("ScreenCapture", "Couldn't find display for id=$displayId")
                        screenCaptureUiInteractor.hide(type)
                    } else {
                        screenCaptureUiFactory
                            .create(type = state.parameters.screenCaptureType, display = display)
                            .attachWindow()
                    }
                }
            }
            .launchIn(appScope)
    }
}
