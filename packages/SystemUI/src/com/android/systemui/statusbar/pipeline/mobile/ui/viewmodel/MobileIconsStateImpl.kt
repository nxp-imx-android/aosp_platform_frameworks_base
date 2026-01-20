/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class MobileIconsStateImpl @AssistedInject constructor(viewModel: MobileIconsViewModel) :
    MobileIconsState, ExclusiveActivatable() {

    init {
        StatusBarMobileIconKairos.assertInLegacyMode()
    }

    private val hydrator = Hydrator("MobileIconsStateImpl.hydrator")

    override val isStackable: Boolean by
        hydrator.hydratedStateOf("isStackable", viewModel.isStackable)

    override val mobileSubViewModels: List<MobileIconState.Factory> by
        hydrator.hydratedStateOf(
            "mobileSubViewModels",
            emptyList(),
            viewModel.mobileSubViewModels.map { vms ->
                vms.map { iconVm ->
                    object : MobileIconState.Factory {
                        override val subscriptionId: Int
                            get() = iconVm.subscriptionId

                        override fun create(): MobileIconState = MobileIconStateImpl(iconVm)
                    }
                }
            },
        )

    override suspend fun onActivated() {
        hydrator.activate()
    }

    @AssistedFactory
    fun interface Factory : MobileIconsState.Factory {
        override fun create(): MobileIconsStateImpl
    }
}
