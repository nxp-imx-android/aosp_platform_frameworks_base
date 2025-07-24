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

package com.android.systemui.qs.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.ui.viewmodel.EditModeSceneActionsViewModel
import com.android.systemui.qs.ui.viewmodel.EditModeSceneContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.shade.ui.composable.Shade
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class EditModeScene
@Inject
constructor(
    private val contentViewModelFactory: EditModeSceneContentViewModel.Factory,
    private val actionsViewModelFactory: EditModeSceneActionsViewModel.Factory,
) : ExclusiveActivatable(), Scene {
    override val key = Scenes.QSEditMode

    private val actionsViewModel: EditModeSceneActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override suspend fun onActivated() {
        actionsViewModel.activate()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = false

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val viewModel =
            rememberViewModel("edit_mode_scene_view_model") { contentViewModelFactory.create() }

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .element(Shade.Elements.BackgroundScrim)
                    .background(colorResource(R.color.shade_scrim_background_dark))
        )

        EditMode(viewModel.editModeViewModel, modifier.testTag("edit_mode_scene"))
    }
}
