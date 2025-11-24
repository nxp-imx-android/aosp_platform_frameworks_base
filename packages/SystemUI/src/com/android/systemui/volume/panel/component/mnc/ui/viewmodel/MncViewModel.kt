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

package com.android.systemui.volume.panel.component.mnc.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.button.ui.viewmodel.ButtonViewModel
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Volume Panel mic noise cancellation UI model. */
@VolumePanelScope
class MncViewModel @Inject constructor() {
    // TODO(b/460536046): Implement the actual view model of the mic noise cancellation button.
    val buttonViewModel: StateFlow<ButtonViewModel?> =
        MutableStateFlow(
            ButtonViewModel(
                isActive = true,
                icon = Icon.Resource(R.drawable.ic_spatial_audio, null),
                label = "Mic Noise Cancellation",
            )
        )

    fun setIsMncEnabled(enabled: Boolean) {}
}
