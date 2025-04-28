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
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.keyevent.domain.interactor.KeyEventInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.VibratorHelper
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class SqueezeEffectHapticPlayer
@AssistedInject
constructor(keyEventInteractor: KeyEventInteractor, private val vibratorHelper: VibratorHelper) :
    ExclusiveActivatable() {

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
    private var invocationJob: Job? = null
    private var canInterruptHaptics = true

    private val powerButtonState =
        combine(
                keyEventInteractor.isPowerButtonDown,
                keyEventInteractor.isPowerButtonLongPressed,
            ) { down, longPressed ->
                PowerButtonState(down, longPressed)
            }
            .distinctUntilChanged()

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch(spanName = "$TAG#powerButtonState") {
                powerButtonState.collect { state ->
                    when {
                        !state.down && !state.longPressed -> interruptInvocationHaptics()
                        state.down && !state.longPressed -> beginInvocationHaptics()
                        state.down && state.longPressed -> canInterruptHaptics = false
                    }
                }
            }
            awaitCancellation()
        }
    }

    private suspend fun beginInvocationHaptics() {
        if (invocationJob != null && invocationJob?.isActive == true) return
        coroutineScope {
            invocationJob =
                launch(spanName = "$TAG#beginInvocationHaptics") {
                    if (invocationHaptics.initialDelay != 0) {
                        delay(invocationHaptics.initialDelay.toLong())
                    }
                    if (isActive) {
                        vibratorHelper.vibrate(
                            invocationHaptics.vibration,
                            SqueezeEffectHapticsBuilder.VIBRATION_ATTRIBUTES,
                        )
                    }
                }
        }
    }

    private fun interruptInvocationHaptics() {
        if (!canInterruptHaptics) return
        vibratorHelper.cancel()
        invocationJob?.cancel()
        invocationJob = null
    }

    fun onSqueezeEffectEnd() {
        canInterruptHaptics = true
    }

    private data class PowerButtonState(val down: Boolean, val longPressed: Boolean)

    @AssistedFactory
    interface Factory {
        fun create(): SqueezeEffectHapticPlayer
    }

    companion object {
        private const val TAG = "SqueezeEffectHapticPlayer"
    }
}
