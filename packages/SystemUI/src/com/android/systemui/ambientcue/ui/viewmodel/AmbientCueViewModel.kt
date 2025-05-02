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

package com.android.systemui.ambientcue.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class AmbientCueViewModel
@AssistedInject
constructor(private val ambientCueInteractor: AmbientCueInteractor) : ExclusiveActivatable() {
    private val hydrator = Hydrator("OverlayViewModel.hydrator")

    val isOverlayVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isOverlayVisible",
            initialValue = false,
            source = ambientCueInteractor.isVisible,
        )

    var isOverlayExpanded: Boolean by mutableStateOf(false)
        private set

    val actions: List<ActionViewModel> by
        hydrator.hydratedStateOf(
            traceName = "actions",
            initialValue = listOf(),
            source =
                ambientCueInteractor.actions.map { actions ->
                    actions.map { action ->
                        ActionViewModel(action.icon, action.label, action.attribution, {})
                    }
                },
        )

    fun show() {
        ambientCueInteractor.setIsVisible(true)
        isOverlayExpanded = false
    }

    fun expand() {
        isOverlayExpanded = true
    }

    fun collapse() {
        isOverlayExpanded = false
    }

    fun hide() {
        ambientCueInteractor.setIsVisible(false)
        isOverlayExpanded = false
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): AmbientCueViewModel
    }

    companion object {
        private const val TAG = "OverlayViewModel"
    }
}
