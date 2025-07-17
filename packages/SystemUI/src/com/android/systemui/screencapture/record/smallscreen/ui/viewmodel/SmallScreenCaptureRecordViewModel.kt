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
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.ui.ScreenCaptureActivity
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class SmallScreenCaptureRecordViewModel
@AssistedInject
constructor(
    @ScreenCapture private val activity: ScreenCaptureActivity,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    recordDetailsAppSelectorViewModelFactory: RecordDetailsAppSelectorViewModel.Factory,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    val recordDetailsAppSelectorViewModel: RecordDetailsAppSelectorViewModel =
        recordDetailsAppSelectorViewModelFactory.create()

    var detailsPopup: RecordDetailsPopupType by mutableStateOf(RecordDetailsPopupType.Settings)
        private set

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("RecordDetailsAppSelectorViewModel#activate") {
                recordDetailsAppSelectorViewModel.activate()
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
        activity.finish()
    }

    fun startRecording() {
        // TODO(b/428686600) pass actual parameters
        screenRecordingServiceInteractor.startRecording(
            ScreenRecordingParameters(
                captureTarget = null,
                displayId = 0,
                shouldShowTaps = false,
                audioSource = ScreenRecordingAudioSource.NONE,
            )
        )
        dismiss()
    }

    @AssistedFactory
    interface Factory {
        fun create(): SmallScreenCaptureRecordViewModel
    }
}
