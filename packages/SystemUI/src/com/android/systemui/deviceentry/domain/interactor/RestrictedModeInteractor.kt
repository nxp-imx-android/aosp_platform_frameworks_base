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

package com.android.systemui.deviceentry.domain.interactor

import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Helper interactor that is used to filter user actions when device's SIM is locked and requires a
 * PIN or PUK for it to be unlocked.
 */
@SysUISingleton
class RestrictedModeInteractor
@Inject
constructor(private val simBouncerInteractor: Lazy<SimBouncerInteractor>) {
    /**
     * Filter user actions that
     * 1. hide the bouncer
     * 2. show non-bouncer overlays
     * 2. change to a scene other than lockscreen or occluded scenes..
     */
    fun filteredUserActions(
        unfiltered: Flow<Map<UserAction, UserActionResult>>
    ): Flow<Map<UserAction, UserActionResult>> {
        return combine(simBouncerInteractor.get().isAnySimSecure, unfiltered) {
            isAnySimSecure,
            unfilteredUserActions ->
            if (isAnySimSecure) {
                unfilteredUserActions.filterValues { actionResult ->
                    when (actionResult) {
                        is UserActionResult.HideOverlay -> {
                            actionResult.overlay != Overlays.Bouncer
                        }
                        is UserActionResult.ChangeScene -> {
                            isSceneChangeAllowed(toScene = actionResult.toScene)
                        }
                        is UserActionResult.ShowOverlay -> {
                            isOverlayChangeAllowed(toOverlayKey = actionResult.overlay)
                        }
                        is UserActionResult.ReplaceByOverlay -> {
                            isOverlayChangeAllowed(toOverlayKey = actionResult.overlay)
                        }
                    }
                }
            } else {
                unfilteredUserActions
            }
        }
    }

    /** Is scene change allowed based on whether the device is in restricted mode or not */
    fun isSceneChangeAllowed(toScene: SceneKey): Boolean {
        return if (simBouncerInteractor.get().isAnySimSecure.value) {
            toScene == Scenes.Lockscreen || toScene == Scenes.Occluded || toScene == Scenes.Dream
        } else {
            true
        }
    }

    /** Is overlay change allowed based on whether the device is in restricted mode or not */
    fun isOverlayChangeAllowed(toOverlayKey: OverlayKey?): Boolean {
        return if (simBouncerInteractor.get().isAnySimSecure.value) {
            toOverlayKey == null || toOverlayKey == Overlays.Bouncer
        } else {
            true
        }
    }
}
