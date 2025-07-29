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

package com.android.systemui.keyguard.ui.composable.blueprint

import androidx.compose.animation.core.tween
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.transitions
import com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition.ClockFaceInTransition.Companion.CLOCK_IN_MILLIS
import com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition.ClockFaceInTransition.Companion.CLOCK_IN_START_DELAY_MILLIS
import com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition.ClockFaceOutTransition.Companion.CLOCK_OUT_MILLIS
import com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition.SmartspaceMoveTransition.Companion.STATUS_AREA_MOVE_DOWN_MILLIS
import com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition.SmartspaceMoveTransition.Companion.STATUS_AREA_MOVE_UP_MILLIS

object ClockTransition {
    // TODO(b/432451019): Rebuild to be dependent on new lockscreen clock element list.
    // Possibly this makes sense to delegate fully to the clock plugins.
    val clockTransitions = transitions {}

    data class ElementKeys(
        val smallClock: List<ElementKey>,
        val largeClock: List<ElementKey>,
        val smartspace: List<ElementKey>,
    )

    private fun TransitionBuilder.transitioningToLargeClock(keys: ElementKeys) {
        spec = tween(durationMillis = STATUS_AREA_MOVE_UP_MILLIS.toInt())
        timestampRange(
            startMillis = CLOCK_IN_START_DELAY_MILLIS.toInt(),
            endMillis = (CLOCK_IN_START_DELAY_MILLIS + CLOCK_IN_MILLIS).toInt(),
        ) {
            keys.largeClock.forEach { fade(it) }
        }

        timestampRange(endMillis = CLOCK_OUT_MILLIS.toInt()) {
            keys.smallClock.forEach { fade(it) }
            keys.smartspace.forEach { fade(it) }
        }
    }

    private fun TransitionBuilder.transitioningToSmallClock(keys: ElementKeys) {
        spec = tween(durationMillis = STATUS_AREA_MOVE_DOWN_MILLIS.toInt())
        timestampRange(
            startMillis = CLOCK_IN_START_DELAY_MILLIS.toInt(),
            endMillis = (CLOCK_IN_START_DELAY_MILLIS + CLOCK_IN_MILLIS).toInt(),
        ) {
            keys.smallClock.forEach { fade(it) }
            keys.smartspace.forEach { fade(it) }
        }

        timestampRange(endMillis = CLOCK_OUT_MILLIS.toInt()) {
            keys.largeClock.forEach { fade(it) }
        }
    }
}
