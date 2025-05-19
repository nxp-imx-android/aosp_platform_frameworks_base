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

package com.android.systemui.topwindoweffects

import android.os.VibrationEffect
import android.testing.TestableLooper.RunWithLooper
import androidx.core.animation.AnimatorTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.haptics.fakeVibratorHelper
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.testKosmos
import com.android.systemui.topui.TopUiControllerRefactor
import com.android.systemui.topui.mockTopUiController
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_OUTWARD_EFFECT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_INITIAL_DELAY_MILLIS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_INWARD_EFFECT_DURATION_MILLIS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
import com.android.systemui.topwindoweffects.data.repository.fakeSqueezeEffectRepository
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectHapticsBuilder
import com.android.systemui.topwindoweffects.ui.viewmodel.squeezeEffectHapticPlayerFactory
import com.android.wm.shell.appzoomout.appZoomOutOptional
import com.android.wm.shell.appzoomout.fakeAppZoomOut
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class TopLevelWindowEffectsTest : SysuiTestCase() {

    @get:Rule val animatorTestRule = AnimatorTestRule()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val vibratorHelper = kosmos.fakeVibratorHelper
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
            totalEffectDuration =
                DEFAULT_OUTWARD_EFFECT_DURATION_MS.toInt() +
                    DEFAULT_INWARD_EFFECT_DURATION_MILLIS.toInt(),
        )

    private val Kosmos.underTest by
        Kosmos.Fixture {
            TopLevelWindowEffects(
                applicationScope = testScope.backgroundScope,
                squeezeEffectInteractor =
                    SqueezeEffectInteractor(squeezeEffectRepository = fakeSqueezeEffectRepository),
                appZoomOutOptional = appZoomOutOptional,
                squeezeEffectHapticPlayerFactory = squeezeEffectHapticPlayerFactory,
                notificationShadeWindowController = notificationShadeWindowController,
                topUiController = mockTopUiController,
                mainExecutor = fakeExecutor,
            )
        }

    private fun Kosmos.advanceTime(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testNoProgressWhenSqueezeEffectDisabled() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false

            underTest.start()

            assertEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
        }

    @Test
    fun testSqueezeEffectStarts_afterInitialDelay() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)

            assertNotEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
        }

    @Test
    fun testSqueezeEffectNotStarted_beforeInitialDelay() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)

            assertEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isFalse()
        }

    @Test
    fun testSqueezeEffectNotStarted_whenUpEventReceivedBefore100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)

            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false

            runCurrent()
            animatorTestRule.advanceTimeBy(1)

            assertEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isFalse()
        }

    @Test
    fun testSqueezeEffectStarted_whenUpEventReceivedAfter100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)
            val timesCancelledBefore = vibratorHelper.timesCancelled

            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent()
            animatorTestRule.advanceTimeBy(1)

            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore + 1)
        }

    @Test
    fun testSqueezeEffectCancelled_whenUpEventReceivedAfterLpp_withIncreasedLppDuration_afterInitialDelay() =
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)
            val timesCancelledBefore = vibratorHelper.timesCancelled

            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false

            runCurrent()
            animatorTestRule.advanceTimeBy(1)

            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore + 1)
        }

    @Test
    fun testSqueezeEffectNotStarted_whenUpEventReceivedAfterLpp_withIncreasedLppDuration_beforeInitialDelay() =
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)

            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false

            runCurrent()

            assertEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isFalse()
        }

    @Test
    fun testEffectNotStartedIfPowerKeyInMultipleKeyCombination() {
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false

            underTest.start()

            advanceTime((expectedDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(1)

            assertEquals(0f, kosmos.fakeAppZoomOut.lastTopLevelProgress)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isFalse()
        }
    }

    @Test
    fun animationContinuesAndCompletes_whenPowerButtonReleased_afterLongPressDetected() =
        kosmos.runTest {
            val initialDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            underTest.start()
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            runCurrent()

            // Advance time past initial delay to start the animation
            advanceTime((initialDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(10L)
            runCurrent()
            val timesCancelledBefore = vibratorHelper.timesCancelled
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isGreaterThan(0f)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()

            // Simulate power button long press
            fakeSqueezeEffectRepository.isPowerButtonLongPressed.value = true
            runCurrent() // Process collection of isPowerButtonLongPressed

            // Release power button
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent() // Triggers cancelSqueeze, but it should not interrupt

            // Animation should be non-interruptible, so haptics are not cancelled at this point
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore)

            // Animation continues: complete inward animation
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS - 10L)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(1f)

            // Animation continues: complete outward animation (triggered by inward animation's end)
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(0f)

            // Haptics never cancelled when animation completes
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore)
        }

    @Test
    fun hapticsNotPlayed_whenHapticsDisabledInRepository_butAnimationRuns() =
        kosmos.runTest {
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled =
                false // Haptics explicitly disabled
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            underTest.start()
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            runCurrent()

            advanceTime((initialDelay + 1).milliseconds)
            val totalVibrationsBefore = vibratorHelper.totalVibrations
            val timesCancelledBefore = vibratorHelper.timesCancelled

            // Complete inward animation
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(1f) // Animation proceeds

            // Assert no new haptics were played or cancelled
            assertThat(vibratorHelper.totalVibrations).isEqualTo(totalVibrationsBefore)
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore)

            // Complete outward animation
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(0f)

            assertThat(vibratorHelper.totalVibrations).isEqualTo(totalVibrationsBefore)
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore)

            // Release power button (should not change anything as animation is finished)
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent()
        }

    @Test
    fun fullAnimationCycle_completesSuccessfully_withoutInterruption() =
        kosmos.runTest {
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            underTest.start()
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            runCurrent()
            // Advance past initial delay
            advanceTime((initialDelay + 1).milliseconds)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()
            val timesCancelledBefore = vibratorHelper.timesCancelled

            // Complete inward animation
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(1f)

            // Outward animation is triggered by the end of the inward animation
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()
            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(0f)

            // Haptics are not cancelled when animation completes without interruption
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore)

            // Release power button (does not affect completed animation)
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent()
        }

    @Test
    fun animationInterruptsMidway_andHapticsAreCorrectlyCancelled() =
        kosmos.runTest {
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isSqueezeEffectHapticEnabled = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            underTest.start()
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            runCurrent()

            // Advance time past initial delay to start the animation
            advanceTime((initialDelay + 1).milliseconds)
            val timesCancelledBefore = vibratorHelper.timesCancelled
            // Progress half-way into inward animation
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS / 2)
            runCurrent()

            val progressBeforeCancel = fakeAppZoomOut.lastTopLevelProgress
            assertThat(progressBeforeCancel).isGreaterThan(0f)
            assertThat(progressBeforeCancel).isLessThan(1f)
            assertThat(vibratorHelper.hasVibratedWithEffects(invocationHaptics.vibration)).isTrue()

            // Release power button before long press is detected
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent() // Process button release, triggers cancelSqueeze

            // cancelSqueeze calls hapticPlayer.cancel()
            assertThat(vibratorHelper.timesCancelled).isEqualTo(timesCancelledBefore + 1)

            // Complete the cancellation (outward) animation
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()

            assertThat(fakeAppZoomOut.lastTopLevelProgress).isEqualTo(0f)
        }

    @Test
    fun topUiRequested_whenAnimationStarts() =
        kosmos.runTest {
            // Setup: Enable effect and trigger power button down
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            // Action: Start the effect and advance time past initial delay to start animation
            underTest.start()
            advanceTime((initialDelay + 1).milliseconds)
            animatorTestRule.advanceTimeBy(1L) // Ensure animator starts processing

            // Verification: setRequestTopUi(true) should be called
            verifySetRequestTopUi(true)
        }

    @Test
    fun topUiCleared_whenAnimationFinishesNormally() =
        kosmos.runTest {
            // Setup: Enable effect and trigger power button down
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            // Action: Start the effect
            underTest.start()
            advanceTime((initialDelay + 1).milliseconds) // Pass initial delay
            animatorTestRule.advanceTimeBy(1L) // Ensure animator starts

            // Verification: Ensure TopUI was requested initially
            verifySetRequestTopUi(true)
            // Reset for next verification
            reset(kosmos.mockTopUiController, kosmos.notificationShadeWindowController)

            // Action: Complete the full animation cycle (inward + outward)
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS - 1L)
            runCurrent()
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()

            // Verification: setRequestTopUi(false) should be called upon completion
            verifySetRequestTopUi(false)
        }

    @Test
    fun topUiCleared_whenAnimationIsCancelled() =
        kosmos.runTest {
            // Setup: Enable effect and trigger power button down
            val initialDelay = 50L
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = initialDelay

            // Action: Start the effect
            underTest.start()
            advanceTime((initialDelay + 1).milliseconds) // Pass initial delay
            // Progress animation part way
            animatorTestRule.advanceTimeBy(DEFAULT_INWARD_EFFECT_DURATION_MILLIS / 2)
            runCurrent()

            // Verification: Ensure TopUI was requested initially
            verifySetRequestTopUi(true)
            // Reset for next verification
            reset(kosmos.mockTopUiController, kosmos.notificationShadeWindowController)

            // Action: Release power button to cancel the animation
            fakeSqueezeEffectRepository.isEffectEnabledAndPowerButtonPressedAsSingleGesture.value =
                false
            runCurrent()
            // Allow cancellation animation to complete
            animatorTestRule.advanceTimeBy(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            runCurrent()

            // Verification: setRequestTopUi(false) should be called upon cancellation
            verifySetRequestTopUi(false)
        }

    private fun verifySetRequestTopUi(isRequested: Boolean) {
        if (TopUiControllerRefactor.isEnabled) {
            verify(kosmos.mockTopUiController, times(1))
                .setRequestTopUi(isRequested, TopLevelWindowEffects.TAG)
        } else {
            kosmos.fakeExecutor.runAllReady()
            verify(kosmos.notificationShadeWindowController, times(1))
                .setRequestTopUi(isRequested, TopLevelWindowEffects.TAG)
        }
    }
}
