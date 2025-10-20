/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.model.TargetModel
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.TargetsViewModel
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Provider
import kotlinx.coroutines.coroutineScope

/** Models UI state for the Screen Share feature. */
class PreShareToolbarViewModel
@AssistedInject
constructor(
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor,
    recentTasksViewModelProvider: Provider<RecentTasksViewModel>,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModel {
    var selectedScreenCaptureTarget: ScreenCaptureTarget by
        mutableStateOf(ScreenCaptureTarget.AppContent(contentId = 0))

    private val recentTasksViewModel = recentTasksViewModelProvider.get()

    private val _currentTargetsModel =
        mutableStateOf<TargetsViewModel<out TargetModel>>(recentTasksViewModel)
    val currentTargetsModel: State<TargetsViewModel<out TargetModel>> = _currentTargetsModel

    fun onCloseClicked() {
        screenCaptureUiInteractor.hide(ScreenCaptureType.SHARE_SCREEN)
    }

    fun onShareClicked() {
        if (selectedScreenCaptureTarget is ScreenCaptureTarget.App) {
            recentTasksViewModel.selectedTarget.value?.let {
                screenCaptureUiInteractor.onScreenSharingApproved(it.model.taskId)
            }
        }
        shareScreenPrivacyIndicatorInteractor.showChip()
    }

    override suspend fun onActivated() {
        coroutineScope { launchTraced("RecentTasksViewModel") { recentTasksViewModel.activate() } }
    }

    @AssistedFactory
    interface Factory {
        fun create(): PreShareToolbarViewModel
    }
}
