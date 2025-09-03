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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.record.ui.viewmodel.ScreenCaptureRecordParametersViewModel
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.Status
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map

class SmallScreenCaptureRecordViewModel
@AssistedInject
constructor(
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    recordDetailsAppSelectorViewModelFactory: RecordDetailsAppSelectorViewModel.Factory,
    screenCaptureRecordParametersViewModelFactory: ScreenCaptureRecordParametersViewModel.Factory,
    recordDetailsTargetViewModelFactory: RecordDetailsTargetViewModel.Factory,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    val recordDetailsAppSelectorViewModel: RecordDetailsAppSelectorViewModel =
        recordDetailsAppSelectorViewModelFactory.create()
    val recordDetailsParametersViewModel: ScreenCaptureRecordParametersViewModel =
        screenCaptureRecordParametersViewModelFactory.create()
    val recordDetailsTargetViewModel: RecordDetailsTargetViewModel =
        recordDetailsTargetViewModelFactory.create()

    var detailsPopup: RecordDetailsPopupType by mutableStateOf(RecordDetailsPopupType.Settings)
        private set

    var shouldShowDetails: Boolean by
        mutableStateOf(!screenRecordingServiceInteractor.status.value.isRecording)
        private set

    val shouldShowSettingsButton: Boolean by
        screenRecordingServiceInteractor.status
            .map { status ->
                if (status.isRecording) {
                    true
                } else {
                    shouldShowDetails = true
                    false
                }
            }
            .hydratedStateOf(
                traceName = "SmallScreenCaptureRecordViewModel#shouldShowSettingsButton",
                initialValue = !screenRecordingServiceInteractor.status.value.isRecording,
            )

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("SmallScreenCaptureRecordViewModel#recordDetailsAppSelectorViewModel") {
                recordDetailsAppSelectorViewModel.activate()
            }
            launchTraced(
                "ScreenCaptureRecordSmallScreenViewModel#recordDetailsParametersViewModel"
            ) {
                recordDetailsParametersViewModel.activate()
            }
            launchTraced("ScreenCaptureRecordSmallScreenViewModel#recordDetailsTargetViewModel") {
                recordDetailsTargetViewModel.activate()
            }
        }
    }

    fun showSettings() {
        detailsPopup = RecordDetailsPopupType.Settings
    }

    fun showAppSelector() {
        detailsPopup = RecordDetailsPopupType.AppSelector
    }

    fun showMarkupColorSelector() {
        detailsPopup = RecordDetailsPopupType.MarkupColorSelector
    }

    fun dismiss() {
        screenCaptureUiInteractor.hide(ScreenCaptureType.RECORD)
    }

    fun startRecording() {
        val shouldShowTaps = recordDetailsParametersViewModel.shouldShowTaps ?: return
        val audioSource = recordDetailsParametersViewModel.audioSource ?: return
        // TODO(b/428686600) pass actual parameters
        screenRecordingServiceInteractor.startRecording(
            ScreenRecordingParameters(
                captureTarget = null,
                displayId = 0,
                shouldShowTaps = shouldShowTaps,
                audioSource = audioSource,
            )
        )
        dismiss()
    }

    fun shouldShowSettings(visible: Boolean) {
        if (shouldShowSettingsButton) {
            shouldShowDetails = visible
        }
    }

    @AssistedFactory
    @ScreenCaptureUiScope
    interface Factory {
        fun create(): SmallScreenCaptureRecordViewModel
    }
}

private val Status.isRecording
    get() = this is Status.Started
