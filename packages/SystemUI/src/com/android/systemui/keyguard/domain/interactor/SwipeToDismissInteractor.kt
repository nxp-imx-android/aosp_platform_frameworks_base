/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.domain.interactor

import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.shade.data.repository.FlingInfo
import com.android.systemui.shade.data.repository.ShadeRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val TAG = "SwipeToDismissInteractor"

/**
 * Handles logic around the swipe to dismiss gesture, where the user swipes up on the dismissable
 * lockscreen to unlock the device.
 */
@SysUISingleton
class SwipeToDismissInteractor
@Inject
constructor(
    @Background backgroundScope: CoroutineScope,
    shadeRepository: ShadeRepository,
    private val transitionInteractor: KeyguardTransitionInteractor,
    private val keyguardInteractor: KeyguardInteractor,
) {
    /**
     * Emits a [FlingInfo] whenever a swipe to dismiss gesture has started a fling animation on the
     * lockscreen while it's dismissable.
     *
     * This value is collected by [FromLockscreenTransitionInteractor] to start a transition from
     * LOCKSCREEN -> GONE, and by [KeyguardSurfaceBehindInteractor] to match the surface remote
     * animation's velocity to the fling velocity, if applicable.
     */
    val dismissFling: StateFlow<FlingInfo?> =
        shadeRepository.currentFling
            .map { flingInfo ->
                val isDismiss =
                    flingInfo != null &&
                        !flingInfo.expand &&
                        keyguardInteractor.statusBarState.value != StatusBarState.SHADE_LOCKED &&
                        transitionInteractor.startedKeyguardTransitionStep.value.to ==
                            KeyguardState.LOCKSCREEN &&
                        keyguardInteractor.isKeyguardDismissible.value

                // Extra verbose logging to help debug a < 1% test flake that seemingly only repros
                // in presubmit. We can remove this after that's figured out.
                Log.d(
                    TAG,
                    "Received fling $flingInfo, isDismiss: $isDismiss." +
                        "expand: ${flingInfo?.expand}, " +
                        "statusBarState: ${keyguardInteractor.statusBarState.value}, " +
                        "startedKeyguardTransitionStep: " +
                        "${transitionInteractor.startedKeyguardTransitionStep.value.to ==
                        KeyguardState.LOCKSCREEN}, " +
                        "isKeyguardDismissible: ${keyguardInteractor.isKeyguardDismissible.value}",
                )

                if (isDismiss) {
                    flingInfo
                } else {
                    null
                }
            }
            .stateIn(backgroundScope, SharingStarted.Eagerly, null)
}
