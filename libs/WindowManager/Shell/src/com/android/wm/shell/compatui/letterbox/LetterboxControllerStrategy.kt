/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.MULTIPLE_SURFACES
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.SINGLE_SURFACE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEvent
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Encapsulate the logic related to the use of a single or multiple surfaces when
 * implementing letterbox in shell.
 */
@WMSingleton
class LetterboxControllerStrategy @Inject constructor(
    private val letterboxConfiguration: LetterboxConfiguration
) {

    // Different letterbox implementation modes.
    enum class LetterboxMode { SINGLE_SURFACE, MULTIPLE_SURFACES }

    @Volatile
    private var currentMode: LetterboxMode = SINGLE_SURFACE

    @Volatile
    private var supportsInputSurface: Boolean = false

    fun configureLetterboxMode(event: LetterboxLifecycleEvent) {
        // Decides whether to use a single surface or multiple surfaces for the letterbox.
        // The primary trade-off is memory usage versus rendering performance.
        //
        // A single surface is used for letterboxes with rounded corners on opaque activities
        // and always for Bubble. In all other cases, such as for letterboxes with straight corners
        // or for those with rounded corners on a translucent activity, we use multiple surfaces to
        // ensure better performance.
        currentMode = when {
            event.isBubble -> SINGLE_SURFACE
            event.isTranslucent -> MULTIPLE_SURFACES
            letterboxConfiguration.isLetterboxActivityCornersRounded() -> SINGLE_SURFACE
            else -> MULTIPLE_SURFACES
        }
        supportsInputSurface = event.supportsInput
    }

    /**
     * @return The specific mode to use for implementing letterboxing for the given [request].
     */
    fun getLetterboxImplementationMode(): LetterboxMode = currentMode

    /**
     * Tells if the input surface should be created or not. This enabled reachability.
     */
    fun shouldSupportInputSurface(): Boolean = supportsInputSurface
}
