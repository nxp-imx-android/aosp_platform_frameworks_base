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
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription

class MobileIconStateImpl(private val viewModel: MobileIconViewModelCommon) :
    MobileIconState, ExclusiveActivatable() {

    private val hydrator = Hydrator("MobileIconStateImpl[$subscriptionId].hydrator")

    private val subscriptionId: Int
        get() = viewModel.subscriptionId

    override val isVisible: Boolean by hydrator.hydratedStateOf("isVisible", viewModel.isVisible)
    override val icon: SignalIconModel by
        hydrator.hydratedStateOf("icon", SignalIconModel.DEFAULT, viewModel.icon)
    override val contentDescription: MobileContentDescription? by
        hydrator.hydratedStateOf("contentDescription", null, viewModel.contentDescription)
    override val roaming: Boolean by hydrator.hydratedStateOf("roaming", false, viewModel.roaming)
    override val networkTypeIcon: Icon.Resource? by
        hydrator.hydratedStateOf("networkTypeIcon", null, viewModel.networkTypeIcon)
    override val networkTypeBackground: Icon.Resource? by
        hydrator.hydratedStateOf("networkTypeBackground", viewModel.networkTypeBackground)
    override val activityInVisible: Boolean by
        hydrator.hydratedStateOf("activityInVisible", false, viewModel.activityInVisible)
    override val activityOutVisible: Boolean by
        hydrator.hydratedStateOf("activityOutVisible", false, viewModel.activityOutVisible)
    override val activityContainerVisible: Boolean by
        hydrator.hydratedStateOf(
            "activityContainerVisible",
            false,
            viewModel.activityContainerVisible,
        )

    override suspend fun onActivated() {
        hydrator.activate()
    }
}
