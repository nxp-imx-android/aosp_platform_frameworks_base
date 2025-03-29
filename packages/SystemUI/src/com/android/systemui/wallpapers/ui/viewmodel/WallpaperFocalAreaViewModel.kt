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

package com.android.systemui.wallpapers.ui.viewmodel

import android.graphics.RectF
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest

class WallpaperFocalAreaViewModel
@Inject
constructor(
    private val wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor,
    val keyguardTransitionInteractor: KeyguardTransitionInteractor,
) {
    val hasFocalArea = wallpaperFocalAreaInteractor.hasFocalArea

    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaperFocalAreaBounds =
        hasFocalArea.flatMapLatest { hasFocalArea ->
            if (hasFocalArea) {
                combine(
                        keyguardTransitionInteractor.startedKeyguardTransitionStep,
                        // Emit bounds when finishing transition to LOCKSCREEN to avoid race
                        // condition with COMMAND_WAKING_UP
                        keyguardTransitionInteractor
                            .transition(
                                edge = Edge.create(to = Scenes.Lockscreen),
                                edgeWithoutSceneContainer =
                                    Edge.create(to = KeyguardState.LOCKSCREEN),
                            )
                            .filter { it.transitionState == TransitionState.FINISHED },
                        ::Pair,
                    )
                    .flatMapLatest { (startedStep, _) ->
                        // Subscribe to bounds within the period of transitioning to the lockscreen,
                        // prior to any transitions away.
                        if (
                            startedStep.to == KeyguardState.LOCKSCREEN &&
                                startedStep.from != KeyguardState.LOCKSCREEN
                        ) {
                            wallpaperFocalAreaInteractor.wallpaperFocalAreaBounds
                        } else {
                            emptyFlow()
                        }
                    }
            } else {
                emptyFlow()
            }
        }

    fun setFocalAreaBounds(bounds: RectF) {
        wallpaperFocalAreaInteractor.setFocalAreaBounds(bounds)
    }
}
