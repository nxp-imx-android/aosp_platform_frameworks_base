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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.ui.viewmodel.AppContentsViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.AudioSwitchViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DisplaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.TargetsViewModel
import com.android.systemui.screencapture.sharescreen.domain.interactor.ShareScreenUiInteractor
import com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class ScreenCaptureShareScreenViewModel
@AssistedInject
constructor(
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val shareScreenUiInteractor: ShareScreenUiInteractor,
    private val shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor,
    @Assisted("thumbnailWidthPx") private val thumbnailWidthPx: Int,
    @Assisted("thumbnailHeightPx") private val thumbnailHeightPx: Int,
    appContentsViewModelFactory: AppContentsViewModel.Factory,
    private val recentTasksViewModel: RecentTasksViewModel,
    private val displaysViewModel: DisplaysViewModel,
) :
    HydratedActivatable(enableEnqueuedActivations = true),
    DrawableLoaderViewModel by drawableLoaderViewModel {
    private val appContentsViewModel =
        appContentsViewModelFactory.create(thumbnailWidthPx, thumbnailHeightPx)

    var currentTargetsModel by mutableStateOf<TargetsViewModel>(appContentsViewModel)
        private set

    var isUiVisible by mutableStateOf(true)
        private set

    fun setTargetViewModel(type: ScreenCaptureTarget) {
        currentTargetsModel =
            when (type) {
                is ScreenCaptureTarget.App -> recentTasksViewModel
                is ScreenCaptureTarget.AppContent -> appContentsViewModel
                is ScreenCaptureTarget.Fullscreen -> displaysViewModel
                else ->
                    throw IllegalArgumentException("Unsupported ScreenCaptureTarget type: $type")
            }
        // Reset the audio state on the newly selected view model.
        (currentTargetsModel as AudioSwitchViewModel).setCaptureAudio(false)
    }

    fun onShareClicked() {
        // Hide the UI immediately for responsive feedback.
        isUiVisible = false

        when (val currentModel = currentTargetsModel) {
            is RecentTasksViewModel -> {
                currentModel.selectedTarget.value?.let {
                    enqueueOnActivatedScope {
                        shareScreenUiInteractor.onAppSharingApproved(it.model.taskId)
                    }
                }
            }
            is AppContentsViewModel -> {
                currentModel.selectedTarget.value?.let {
                    val callback = currentModel.projectionCallback.value?.get()
                    // The callback is retrieved from a [WeakReference] and may be null if it was
                    // garbage collected.
                    if (callback == null) {
                        Log.e(TAG, "Projection callback is not available")
                        return@let
                    }
                    shareScreenUiInteractor.onAppContentSharingApproved(
                        it.model.contentId,
                        callback,
                        currentModel.captureAudio.value,
                    )
                }
            }
            is DisplaysViewModel -> {
                currentModel.selectedTarget.value?.let {
                    shareScreenUiInteractor.onDisplaySharingApproved(it.model.displayId)
                }
            }
            else -> throw IllegalStateException("Unsupported TargetsViewModel type: $currentModel")
        }
        shareScreenPrivacyIndicatorInteractor.showChip()
    }

    fun onCloseClicked() {
        shareScreenUiInteractor.onClose()
    }

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("AppContentsViewModel") { appContentsViewModel.activate() }
            launchTraced("RecentTasksViewModel") { recentTasksViewModel.activate() }
            launchTraced("DisplaysViewModel") { displaysViewModel.activate() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("thumbnailWidthPx") thumbnailWidthPx: Int,
            @Assisted("thumbnailHeightPx") thumbnailHeightPx: Int,
        ): ScreenCaptureShareScreenViewModel
    }

    companion object {
        private const val TAG = "ScreenCaptureShareScreenViewModel"
    }
}
