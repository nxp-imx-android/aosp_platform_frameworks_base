/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.keyguard.ui.composable.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutScope
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions as buildTransitions
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle

object LockscreenScopeUtils {
    @Composable
    fun ContentScope.NestedScenes(
        sceneKey: SceneKey,
        transitions: SceneTransitionsBuilder.() -> Unit,
        modifier: Modifier,
        builder: SceneTransitionLayoutScope<ContentScope>.() -> Unit,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val sceneState =
            rememberMutableSceneTransitionLayoutState(sceneKey, buildTransitions { transitions() })

        LaunchedEffectWithLifecycle(sceneState, sceneKey, coroutineScope) {
            sceneState.setTargetScene(sceneKey, coroutineScope)
        }

        NestedSceneTransitionLayout(sceneState, modifier) { builder() }
    }
}
