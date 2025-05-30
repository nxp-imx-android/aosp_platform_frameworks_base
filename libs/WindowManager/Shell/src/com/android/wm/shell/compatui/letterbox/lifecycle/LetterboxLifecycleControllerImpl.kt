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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.view.SurfaceControl
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.compatui.letterbox.LetterboxController
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy

/**
 * [LetterboxLifecycleController] default implementation.
 */
class LetterboxLifecycleControllerImpl(
    private val letterboxController: LetterboxController,
    private val transitionStateHolder: TransitionStateHolder,
    private val letterboxModeStrategy: LetterboxControllerStrategy
) : LetterboxLifecycleController {

    override fun onLetterboxLifecycleEvent(
        event: LetterboxLifecycleEvent,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction
    ) {
        val key = event.letterboxKey()
        with(letterboxController) {
            when (event.type) {
                LetterboxLifecycleEventType.CLOSE -> {
                    if (!transitionStateHolder.isRecentsTransitionRunning()) {
                        // For the other types of close we need to check recents.
                        destroyLetterboxSurface(key, finishTransaction)
                    }
                }
                else -> {
                    if (event.letterboxBounds != null) {
                        // In this case the top Activity is letterboxed.
                        letterboxModeStrategy.configureLetterboxMode()
                        event.leash?.let { leash ->
                            createLetterboxSurface(
                                key,
                                startTransaction,
                                leash,
                                event.containerToken
                            )
                        }
                        updateLetterboxSurfaceBounds(
                            key,
                            startTransaction,
                            event.taskBounds,
                            event.letterboxBounds
                        )
                    } else {
                        updateLetterboxSurfaceVisibility(
                            key,
                            startTransaction,
                            visible = false
                        )
                    }
                }
            }
        }
    }
}