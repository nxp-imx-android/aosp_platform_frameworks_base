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

package com.android.systemui.topwindoweffects.ui.viewmodel

import android.os.VibrationEffect
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.topwindoweffects.qualifiers.TopLevelWindowEffectsThread
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SqueezeEffectHapticPlayer
@AssistedInject
constructor(
    private val vibratorHelper: VibratorHelper,
    @TopLevelWindowEffectsThread private val topLevelWindowEffectsScope: CoroutineScope,
) {

    private val primitiveDurations =
        vibratorHelper.getPrimitiveDurations(
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        )
    private val invocationHaptics =
        SqueezeEffectHapticsBuilder.createInvocationHaptics(
            lowTickDuration = primitiveDurations[0],
            quickRiseDuration = primitiveDurations[1],
            tickDuration = primitiveDurations[2],
        )

    private var vibrationJob: Job? = null

    fun start() {
        cancel()
        vibrationJob =
            topLevelWindowEffectsScope.launch {
                delay(invocationHaptics.initialDelay.toLong())
                vibratorHelper.vibrate(
                    invocationHaptics.vibration,
                    SqueezeEffectHapticsBuilder.VIBRATION_ATTRIBUTES,
                )
                vibrationJob = null
            }
    }

    fun cancel() {
        vibratorHelper.cancel()
        vibrationJob?.cancel()
        vibrationJob = null
    }

    @AssistedFactory
    interface Factory {
        fun create(): SqueezeEffectHapticPlayer
    }

    companion object {
        private const val TAG = "SqueezeEffectHapticPlayer"
    }
}
