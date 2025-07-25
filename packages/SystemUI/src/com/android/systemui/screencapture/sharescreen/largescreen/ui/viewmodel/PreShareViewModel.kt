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

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow

// TODO(b/423708493): Add FULLSCREEN Sharing target.
enum class ScreenShareTarget {
    APP_WINDOW,
    TAB,
}

/** Models UI state for the Screen Share feature. */
class PreShareViewModel
@AssistedInject
constructor(private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl) :
    HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {
    // The private, mutable source of truth for the selected target.
    private val selectedScreenShareTargetSource = MutableStateFlow(ScreenShareTarget.APP_WINDOW)

    val selectedScreenShareTarget by
        selectedScreenShareTargetSource.hydratedStateOf(traceName = "selectedScreenShareTarget")

    // Called by the UI when a new sharing target is selected by the user.
    fun onTargetSelected(target: ScreenShareTarget) {
        selectedScreenShareTargetSource.value = target
    }

    @AssistedFactory
    interface Factory {
        fun create(): PreShareViewModel
    }
}
