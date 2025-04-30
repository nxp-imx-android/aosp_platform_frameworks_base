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

import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyevent.data.repository.fakeKeyEventRepository
import com.android.systemui.keyevent.domain.interactor.KeyEventInteractor
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
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_INITIAL_DELAY_MILLIS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
import com.android.systemui.topwindoweffects.data.repository.fakeSqueezeEffectRepository
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.ui.compose.EffectsWindowRoot
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import java.util.Optional
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.verification.VerificationMode

@SmallTest
@RunWith(AndroidJUnit4::class)
class TopLevelWindowEffectsTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Mock private lateinit var windowManager: WindowManager

    @Mock private lateinit var viewModelFactory: SqueezeEffectViewModel.Factory

    private val Kosmos.underTest by
        Kosmos.Fixture {
            TopLevelWindowEffects(
                context = mContext,
                mainDispatcher = StandardTestDispatcher(testScope.testScheduler),
                topLevelWindowEffectsScope = testScope.backgroundScope,
                windowManager = windowManager,
                viewModelFactory = viewModelFactory,
                squeezeEffectInteractor =
                    SqueezeEffectInteractor(
                        squeezeEffectRepository = fakeSqueezeEffectRepository,
                        keyEventInteractor = KeyEventInteractor(fakeKeyEventRepository),
                        coroutineContext = testScope.testScheduler,
                    ),
                appZoomOutOptional = Optional.empty(),
                notificationShadeWindowController = kosmos.notificationShadeWindowController,
                topUiController = kosmos.mockTopUiController,
                interactionJankMonitor = kosmos.interactionJankMonitor,
            )
        }

    private fun Kosmos.advanceTime(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        doNothing().whenever(windowManager).addView(any<View>(), any<WindowManager.LayoutParams>())
        doNothing().whenever(windowManager).removeView(any<View>())
        doNothing().whenever(windowManager).removeView(any<EffectsWindowRoot>())
    }

    @Test
    fun noWindowWhenSqueezeEffectDisabled() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = false

            underTest.start()

            verifyAddViewAndTopUi(never())
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_withDelayMoreThan100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)

            verifyAddViewAndTopUi(times(1))
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_withDelayLessThan100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)

            verifyAddViewAndTopUi(never())
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_upEventReceivedBefore100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)

            fakeKeyEventRepository.setPowerButtonDown(false)

            runCurrent()

            verifyAddViewAndTopUi(never())
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_upEventReceivedAfter100Millis() =
        kosmos.runTest {
            val expectedDelay = 100L
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)

            fakeKeyEventRepository.setPowerButtonDown(false)

            runCurrent()

            verifyAddViewAndTopUi(times(1))
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_upEventReceivedAfterLpp_withIncreasedLppDuration_afterInitialDelay() =
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // add additional 1ms time to simulate initial delay duration has passed
            advanceTime((expectedDelay + 1).milliseconds)

            fakeKeyEventRepository.setPowerButtonDown(false)

            runCurrent()

            verifyAddViewAndTopUi(times(1))
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled_upEventReceivedAfterLpp_withIncreasedLppDuration_beforeInitialDelay() =
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = false

            underTest.start()

            // subtract 1ms time to simulate initial delay duration is yet not finished
            advanceTime((expectedDelay - 1).milliseconds)

            fakeKeyEventRepository.setPowerButtonDown(false)

            runCurrent()

            verifyAddViewAndTopUi(never())
        }

    @Test
    fun testNoWindowAddedIfPowerKeyInMultipleKeyCombination() {
        kosmos.runTest {
            val expectedDelay =
                DEFAULT_INITIAL_DELAY_MILLIS + 750 - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeSqueezeEffectRepository.invocationEffectInitialDelayMs = expectedDelay
            fakeKeyEventRepository.setPowerButtonDown(true)
            fakeSqueezeEffectRepository.isPowerButtonDownInKeyCombination.value = true

            underTest.start()

            advanceTime((expectedDelay + 1).milliseconds)

            verify(windowManager, never()).addView(any<View>(), any<WindowManager.LayoutParams>())
        }
    }

    private fun verifyAddViewAndTopUi(mode: VerificationMode) {
        verify(windowManager, mode).addView(any<View>(), any<WindowManager.LayoutParams>())
        if (TopUiControllerRefactor.isEnabled) {
            verify(kosmos.mockTopUiController, mode)
                .setRequestTopUi(true, TopLevelWindowEffects.TAG)
        } else {
            verify(kosmos.notificationShadeWindowController, mode)
                .setRequestTopUi(true, TopLevelWindowEffects.TAG)
        }
    }
}
