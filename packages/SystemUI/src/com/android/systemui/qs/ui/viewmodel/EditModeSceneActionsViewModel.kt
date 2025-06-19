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

import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.viewmodel.UserActionsViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation


class EditModeSceneActionsViewModel
@AssistedInject
constructor(
    private val sceneBackInteractor: SceneBackInteractor
) : UserActionsViewModel() {
    override suspend fun hydrateActions(setActions: (Map<UserAction, UserActionResult>) -> Unit) {
        sceneBackInteractor.backScene.collect {
            setActions(
                buildMap {
                    put(Swipe.Up, UserActionResult(Scenes.Gone))
                    if (it != null) {
                        put(Back, UserActionResult(it))
                    }
                }
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): EditModeSceneActionsViewModel
    }
}
