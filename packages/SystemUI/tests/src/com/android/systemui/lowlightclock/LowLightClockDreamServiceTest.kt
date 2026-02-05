/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.lowlightclock

import android.animation.Animator
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.dream.lowlight.LowLightTransitionCoordinator
import com.android.systemui.SysuiTestCase
import java.util.Optional
import javax.inject.Provider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class LowLightClockDreamServiceTest : SysuiTestCase() {
    private val chargingStatusProvider: ChargingStatusProvider = mock()
    private val displayController: LowLightDisplayController = mock()
    private val animationProvider: LowLightClockAnimationProvider = mock()
    private val lowLightTransitionCoordinator: LowLightTransitionCoordinator = mock()
    private val animationInAnimator: Animator = mock()
    private val animationOutAnimator: Animator = mock()

    private lateinit var service: LowLightClockDreamService

    @Before
    fun setUp() {
        service =
            LowLightClockDreamService(
                chargingStatusProvider,
                animationProvider,
                lowLightTransitionCoordinator,
                Optional.of(Provider { displayController }),
            )

        whenever(animationProvider.provideAnimationIn(any(), any())).thenReturn(animationInAnimator)
        whenever(animationProvider.provideAnimationOut(any(), any()))
            .thenReturn(animationOutAnimator)
    }

    @Test
    fun testSetDbmStateWhenSupported() {
        whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(true)

        service.onDreamingStarted()

        verify(displayController).setDisplayBrightnessModeEnabled(true)
    }

    @Test
    fun testNotSetDbmStateWhenNotSupported() {
        whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(false)

        service.onDreamingStarted()

        verify(displayController, never()).setDisplayBrightnessModeEnabled(any())
    }

    @Test
    fun testClearDbmState() {
        whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(true)

        service.onDreamingStarted()
        clearInvocations(displayController)

        service.onDreamingStopped()

        verify(displayController).setDisplayBrightnessModeEnabled(false)
    }

    @Test
    fun testAnimationsStartedOnDreamingStarted() {
        service.onDreamingStarted()

        // Entry animation started.
        verify(animationInAnimator).start()
    }

    @Test
    fun testAnimationsStartedOnWakeUp() {
        // Start dreaming then wake up.
        service.onDreamingStarted()
        service.onWakeUp()

        // Entry animation started.
        verify(animationInAnimator).cancel()

        // Exit animation started.
        verify(animationOutAnimator).start()
    }

    @Test
    fun testAnimationsStartedBeforeExitingLowLight() {
        service.onBeforeExitLowLight()

        // Exit animation started.
        verify(animationOutAnimator).start()
    }

    @Test
    fun testWakeUpAnimationCancelledOnDetach() {
        service.onWakeUp()

        // Exit animation started.
        verify(animationOutAnimator).start()

        service.onDetachedFromWindow()

        verify(animationOutAnimator).cancel()
    }

    @Test
    fun testExitLowLightAnimationCancelledOnDetach() {
        service.onBeforeExitLowLight()

        // Exit animation started.
        verify(animationOutAnimator).start()

        service.onDetachedFromWindow()

        verify(animationOutAnimator).cancel()
    }
}
