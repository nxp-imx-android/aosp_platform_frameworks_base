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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class StudioLookButtonViewModel
@AssistedInject
constructor(
    desktopEffectInteractor: DesktopEffectInteractor,
    @Assisted private val setCurrentScreen: (Screen) -> Unit,
) : ButtonViewModel, HydratedActivatable() {
    override val state by
        desktopEffectInteractor.model
            .map {
                val activeEffectCount: Int =
                    if (it.faceRetouch) 1 else 0 + if (it.portraitRelight) 1 else 0
                ButtonUiState(
                    isEnabled = activeEffectCount > 0,
                    // TODO(b/436222258): Replace hardcoded strings with resource resolution
                    mainTitle = "Studio Look",
                    subTitle = "$activeEffectCount effects",
                    image = com.android.systemui.res.R.drawable.gs_face_retouch,
                )
            }
            .hydratedStateOf(initialValue = ButtonUiState())

    override suspend fun onClick() {
        setCurrentScreen(Screen.STUDIO_LOOK)
    }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentScreen: (Screen) -> Unit): StudioLookButtonViewModel
    }
}
