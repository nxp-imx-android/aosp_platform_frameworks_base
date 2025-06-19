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

package com.android.systemui.qs.ui.viewmodel

import com.android.systemui.qs.composefragment.SceneKeys
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.util.kotlin.sample
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EditModeSceneContentViewModel
@AssistedInject
constructor(
    val editModeViewModel: EditModeViewModel,
    private val sceneBackInteractor: SceneBackInteractor,
    private val sceneInteractor: SceneInteractor,
) : ExclusiveActivatable() {
    override suspend fun onActivated() {
        coroutineScope {
            editModeViewModel.isEditing
                .filterNot { it }
                .sample(sceneBackInteractor.backScene)
                .onEach { backScene ->
                    sceneInteractor.changeScene(
                        toScene = backScene ?: SceneKeys.EditMode,
                        loggingReason = "Exiting edit mode"
                    )
                }
                .launchIn(this)

            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): EditModeSceneContentViewModel
    }
}