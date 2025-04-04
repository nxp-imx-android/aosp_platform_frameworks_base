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

package com.android.systemui.actioncorner.domain.interactor

import com.android.systemui.LauncherProxyService
import com.android.systemui.actioncorner.data.repository.ActionCornerRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import javax.inject.Inject

@SysUISingleton
class ActionCornerInteractor
@Inject
constructor(
    private val repository: ActionCornerRepository,
    private val launcherProxyService: LauncherProxyService,
) : ExclusiveActivatable() {

    override suspend fun onActivated(): Nothing {
        repository.actionCornerState.collect {
            // TODO: call methods in LauncherProxyService to send action to launcher when the APIs
            // are ready
        }
    }
}
