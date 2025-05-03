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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.haptics.fakeVibratorHelper
import com.android.systemui.keyevent.data.repository.fakeKeyEventRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.fakeSqueezeEffectRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class SqueezeEffectHapticPlayerTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val initialDelay = 100L
    private val vibratorHelper = kosmos.fakeVibratorHelper
    private val squeezeEffectRepository = kosmos.fakeSqueezeEffectRepository
    private val keyEventRepository = kosmos.fakeKeyEventRepository
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
    private val underTest = kosmos.squeezeEffectHapticPlayerFactory.create()

    @Before
    fun setUp() {
        squeezeEffectRepository.isSqueezeEffectEnabled.value = true
        squeezeEffectRepository.invocationEffectInitialDelayMs = initialDelay
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun onDown_beforeLongPress_invocationHapticsPlay() =
        kosmos.testScope.runTest {
            val powerButtonLongPress by
                collectLastValue(keyEventRepository.isPowerButtonLongPressed)
            keyEventRepository.setPowerButtonDown(true)

            advanceTimeBy(initialDelay + 1)
            runCurrent()

            assertThat(powerButtonLongPress).isFalse()
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
        }

    @Test
    fun onRelease_beforeLongPress_invocationHapticsCancel() =
        kosmos.testScope.runTest {
            val powerButtonLongPress by
                collectLastValue(keyEventRepository.isPowerButtonLongPressed)
            keyEventRepository.setPowerButtonDown(true)

            advanceTimeBy(initialDelay + 1)
            runCurrent()

            keyEventRepository.setPowerButtonDown(false)
            runCurrent()

            assertThat(powerButtonLongPress).isFalse()
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
            // From the initial collection of the state flow and the interruption, two cancellations
            // are expected
            assertThat(vibratorHelper.timesCancelled).isEqualTo(2)
        }

    @Test
    fun onRelease_afterLongPress_invocationHapticsDoesNotCancel() =
        kosmos.testScope.runTest {
            val powerButtonLongPress by
                collectLastValue(keyEventRepository.isPowerButtonLongPressed)
            keyEventRepository.setPowerButtonDown(true)

            advanceTimeBy(initialDelay + 1)
            runCurrent()

            keyEventRepository.setPowerButtonLongPressed(true)
            runCurrent()

            keyEventRepository.setPowerButtonDown(false)
            runCurrent()

            assertThat(powerButtonLongPress).isTrue()
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
            // From the initial collection of the state flow and the interruption, only one
            // cancellation is expected
            assertThat(vibratorHelper.timesCancelled).isEqualTo(1)
        }
}
