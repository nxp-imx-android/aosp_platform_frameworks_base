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

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.util.Log

object SqueezeEffectHapticsBuilder {

    private const val TAG = "SqueezeEffectHapticsBuilder"
    private const val RISE_TO_TICK_DELAY = 50 // in milliseconds
    private const val LOW_TICK_SCALE = 0.09f
    private const val QUICK_RISE_SCALE = 0.25f
    private const val TICK_SCALE = 1f

    val VIBRATION_ATTRIBUTES =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build()

    fun createInvocationHaptics(
        lowTickDuration: Int,
        quickRiseDuration: Int,
        tickDuration: Int,
    ): SqueezeEffectHaptics {
        val totalEffectDuration =
            SqueezeEffectConfig.INWARD_EFFECT_DURATION + SqueezeEffectConfig.OUTWARD_EFFECT_DURATION
        // If a primitive is not supported, the duration will be 0
        val isInvocationEffectSupported =
            lowTickDuration != 0 && quickRiseDuration != 0 && tickDuration != 0

        if (!isInvocationEffectSupported) {
            Log.d(
                TAG,
                """
                    The LOW_TICK, TICK and/or QUICK_RISE primitives are not supported.
                    Using EFFECT_HEAVY_CLICK as a fallback."
                """
                    .trimIndent(),
            )
            // We use the full invocation duration as a delay so that we play the
            // HEAVY_CLICK fallback in sync with the end of the squeeze effect
            return SqueezeEffectHaptics(
                initialDelay = totalEffectDuration,
                vibration = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK),
            )
        }

        val riseEffectDuration = quickRiseDuration + RISE_TO_TICK_DELAY + tickDuration
        val warmUpTime = totalEffectDuration - riseEffectDuration
        val nLowTicks = warmUpTime / lowTickDuration

        val composition =
            VibrationEffect.startComposition().apply {
                // Warmup low ticks
                repeat(nLowTicks) {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, LOW_TICK_SCALE, 0)
                }
                // Quick rise and tick
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, QUICK_RISE_SCALE, 0)
                addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    TICK_SCALE,
                    RISE_TO_TICK_DELAY,
                )
            }

        return SqueezeEffectHaptics(initialDelay = 0, vibration = composition.compose())
    }

    data class SqueezeEffectHaptics(val initialDelay: Int, val vibration: VibrationEffect)
}
