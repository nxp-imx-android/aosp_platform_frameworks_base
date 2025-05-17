/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.domain.interactor

import com.android.systemui.ambientcue.data.repository.AmbientCueRepository
import com.android.systemui.ambientcue.shared.model.ActionModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class AmbientCueInteractor @Inject constructor(private val repository: AmbientCueRepository) {
    val isRootViewAttached: StateFlow<Boolean> = repository.isRootViewAttached
    val actions: StateFlow<List<ActionModel>> = repository.actions
    val isImeVisible: StateFlow<Boolean> = repository.isImeVisible

    fun setDeactivated(isDeactivated: Boolean) {
        repository.isDeactivated.update { isDeactivated }
    }

    fun setImeVisible(isVisible: Boolean) {
        repository.isImeVisible.update { isVisible }
    }
}
