/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.unfold

import android.content.Context
import android.content.res.Resources
import android.hardware.devicestate.DeviceStateManager
import android.os.PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD
import android.os.PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.util.LatencyTracker
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_UNFOLD
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.ConfigurationRepositoryImpl
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractorImpl
import com.android.systemui.defaultDeviceState
import com.android.systemui.deviceStateManager
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.HALF_FOLDED
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.UNFOLDED
import com.android.systemui.display.data.repository.fakeDeviceStateRepository
import com.android.systemui.foldedDeviceStateList
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setScreenPowerState
import com.android.systemui.power.domain.interactor.PowerInteractorFactory
import com.android.systemui.power.shared.model.ScreenPowerState.SCREEN_OFF
import com.android.systemui.power.shared.model.ScreenPowerState.SCREEN_ON
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.statusbar.policy.FakeConfigurationController
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.COOL_DOWN_DURATION
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.FOLDABLE_DEVICE_STATE_CLOSED
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.FOLDABLE_DEVICE_STATE_HALF_OPEN
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.Companion.SCREEN_EVENT_TIMEOUT
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.DisplaySwitchLatencyEvent
import com.android.systemui.unfold.data.repository.UnfoldTransitionRepositoryImpl
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import com.android.systemui.unfoldedDeviceState
import com.android.systemui.util.animation.data.repository.fakeAnimationStatusRepository
import com.android.systemui.util.mockito.capture
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
@SmallTest
class DisplaySwitchLatencyTrackerTest : SysuiTestCase() {
    private lateinit var displaySwitchLatencyTracker: DisplaySwitchLatencyTracker
    @Captor private lateinit var loggerArgumentCaptor: ArgumentCaptor<DisplaySwitchLatencyEvent>

    private val kosmos = Kosmos()
    private val mockContext = mock<Context>()
    private val resources = mock<Resources>()
    private val foldStateRepository = kosmos.fakeDeviceStateRepository
    private val powerInteractor = PowerInteractorFactory.create().powerInteractor
    private val animationStatusRepository = kosmos.fakeAnimationStatusRepository
    private val keyguardInteractor = mock<KeyguardInteractor>()
    private val displaySwitchLatencyLogger = mock<DisplaySwitchLatencyLogger>()
    private val latencyTracker = mock<LatencyTracker>()

    private val deviceStateManager = kosmos.deviceStateManager
    private val closedDeviceState = kosmos.foldedDeviceStateList.first()
    private val openDeviceState = kosmos.unfoldedDeviceState
    private val defaultDeviceState = kosmos.defaultDeviceState
    private val nonEmptyClosedDeviceStatesArray: IntArray =
        IntArray(2) { closedDeviceState.identifier }

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val testScope: TestScope = TestScope(testDispatcher)
    private val isAodAvailable = MutableStateFlow(false)
    private val systemClock = FakeSystemClock()
    private val configurationController = FakeConfigurationController()
    private val configurationRepository =
        ConfigurationRepositoryImpl(
            configurationController,
            context,
            testScope.backgroundScope,
            mock(),
        )
    private val configurationInteractor = ConfigurationInteractorImpl(configurationRepository)
    private val unfoldTransitionProgressProvider = FakeUnfoldTransitionProvider()
    private val unfoldTransitionRepository =
        UnfoldTransitionRepositoryImpl(Optional.of(unfoldTransitionProgressProvider))
    private val unfoldTransitionInteractor =
        UnfoldTransitionInteractor(unfoldTransitionRepository, configurationInteractor)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(mockContext.resources).thenReturn(resources)
        whenever(mockContext.getSystemService(DeviceStateManager::class.java))
            .thenReturn(deviceStateManager)
        whenever(deviceStateManager.supportedDeviceStates)
            .thenReturn(listOf(closedDeviceState, openDeviceState))
        whenever(resources.getIntArray(R.array.config_foldedDeviceStates))
            .thenReturn(nonEmptyClosedDeviceStatesArray)
        whenever(keyguardInteractor.isAodAvailable).thenReturn(isAodAvailable)
        animationStatusRepository.onAnimationStatusChanged(true)
        powerInteractor.setAwakeForTest()
        powerInteractor.setScreenPowerState(SCREEN_ON)
        displaySwitchLatencyTracker =
            DisplaySwitchLatencyTracker(
                mockContext,
                foldStateRepository,
                powerInteractor,
                unfoldTransitionInteractor,
                animationStatusRepository,
                keyguardInteractor,
                testDispatcher.asExecutor(),
                testScope.backgroundScope,
                displaySwitchLatencyLogger,
                systemClock,
                deviceStateManager,
                latencyTracker,
            )
    }

    @Test
    fun unfold_logsLatencyTillTransitionStarted() {
        testScope.runTest {
            displaySwitchLatencyTracker.start()
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(HALF_FOLDED)
            runCurrent()
            systemClock.advanceTime(50)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            systemClock.advanceTime(200)
            unfoldTransitionProgressProvider.onTransitionStarted()
            runCurrent()
            setDeviceState(UNFOLDED)

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 250,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun unfold_progressUnavailable_logsLatencyTillScreenTurnedOn() {
        testScope.runTest {
            val unfoldTransitionInteractorWithEmptyProgressProvider =
                UnfoldTransitionInteractor(
                    UnfoldTransitionRepositoryImpl(Optional.empty()),
                    configurationInteractor,
                )
            displaySwitchLatencyTracker =
                DisplaySwitchLatencyTracker(
                    mockContext,
                    foldStateRepository,
                    powerInteractor,
                    unfoldTransitionInteractorWithEmptyProgressProvider,
                    animationStatusRepository,
                    keyguardInteractor,
                    testDispatcher.asExecutor(),
                    testScope.backgroundScope,
                    displaySwitchLatencyLogger,
                    systemClock,
                    deviceStateManager,
                    latencyTracker,
                )

            displaySwitchLatencyTracker.start()
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            systemClock.advanceTime(50)
            runCurrent()
            systemClock.advanceTime(200)
            unfoldTransitionProgressProvider.onTransitionStarted()
            runCurrent()
            setDeviceState(UNFOLDED)

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 50,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun unfold_animationDisabled_logsLatencyTillScreenTurnedOn() {
        testScope.runTest {
            animationStatusRepository.onAnimationStatusChanged(false)

            displaySwitchLatencyTracker.start()
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            systemClock.advanceTime(50)
            runCurrent()
            unfoldTransitionProgressProvider.onTransitionStarted()
            systemClock.advanceTime(200)
            runCurrent()
            setDeviceState(UNFOLDED)

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 50,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun foldWhileStayingAwake_logsLatency() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            powerInteractor.setScreenPowerState(SCREEN_ON)

            displaySwitchLatencyTracker.start()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            runCurrent()
            systemClock.advanceTime(200)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 200,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun foldToAod_capturesToStateAsAod() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            isAodAvailable.emit(true)

            displaySwitchLatencyTracker.start()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(FOLDED)
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            runCurrent()
            systemClock.advanceTime(200)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 200,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toState = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__AOD,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun fold_notAFoldable_shouldNotLogLatency() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            whenever(resources.getIntArray(R.array.config_foldedDeviceStates))
                .thenReturn(IntArray(0))
            whenever(deviceStateManager.supportedDeviceStates)
                .thenReturn(listOf(defaultDeviceState))

            displaySwitchLatencyTracker.start()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            runCurrent()
            systemClock.advanceTime(200)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(displaySwitchLatencyLogger, never()).log(any())
        }
    }

    @Test
    fun foldToScreenOff_capturesToStateAsScreenOff() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            isAodAvailable.emit(false)

            displaySwitchLatencyTracker.start()
            setDeviceState(HALF_FOLDED)
            systemClock.advanceTime(50)
            runCurrent()
            setDeviceState(FOLDED)
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_DEVICE_FOLD)
            powerInteractor.setScreenPowerState(SCREEN_OFF)
            runCurrent()

            verify(displaySwitchLatencyLogger).log(capture(loggerArgumentCaptor))
            val loggedEvent = loggerArgumentCaptor.value
            val expectedLoggedEvent =
                DisplaySwitchLatencyEvent(
                    latencyMs = 0,
                    fromFoldableDeviceState = FOLDABLE_DEVICE_STATE_HALF_OPEN,
                    toFoldableDeviceState = FOLDABLE_DEVICE_STATE_CLOSED,
                    toState = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF,
                )
            assertThat(loggedEvent).isEqualTo(expectedLoggedEvent)
        }
    }

    @Test
    fun unfoldingDevice_startsLatencyTracking() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()

            verify(latencyTracker).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldingDevice_doesntTrackLatency() {
        testScope.runTest {
            setDeviceState(UNFOLDED)
            displaySwitchLatencyTracker.start()
            runCurrent()

            startFolding()

            verify(latencyTracker, never()).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldedState_doesntStartTrackingOnScreenOn() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(latencyTracker, never()).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun unfoldingDevice_endsLatencyTrackingWhenTransitionStarts() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            unfoldTransitionProgressProvider.onTransitionStarted()
            runCurrent()

            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun unfoldingDevice_animationsDisabled_endsLatencyTrackingWhenScreenOn() {
        testScope.runTest {
            animationStatusRepository.onAnimationStatusChanged(enabled = false)
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun unfoldingDevice_doesntEndLatencyTrackingWhenScreenOn() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun unfoldingDevice_animationsDisabled_endsLatencyTrackingWhenDeviceGoesToSleep() {
        testScope.runTest {
            animationStatusRepository.onAnimationStatusChanged(enabled = false)
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            powerInteractor.setAsleepForTest(sleepReason = GO_TO_SLEEP_REASON_POWER_BUTTON)
            runCurrent()

            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_cancelsTrackingWhenNewDeviceStateEmitted() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            finishFolding()

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_cancelsTrackingForManyStateChanges() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            startUnfolding()
            startFolding()
            startUnfolding()
            finishUnfolding()

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_startsOneTrackingForManyStateChanges() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            startUnfolding()
            startFolding()
            startUnfolding()

            verify(latencyTracker, times(1)).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_inCoolDownPeriod_trackingDisabled() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            finishFolding()

            advanceTimeBy(COOL_DOWN_DURATION.minus(10.milliseconds))
            startUnfolding()
            finishUnfolding()

            verify(latencyTracker, times(1)).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun interruptedDisplaySwitchFinished_coolDownPassed_trackingWorksAsUsual() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            finishFolding()

            advanceTimeBy(COOL_DOWN_DURATION.plus(10.milliseconds))
            startUnfolding()
            finishUnfolding()

            verify(latencyTracker, times(2)).onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
            verify(latencyTracker).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_coolDownExtendedByStartEvents() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            advanceTimeBy(COOL_DOWN_DURATION.minus(10.milliseconds))
            startUnfolding()
            advanceTimeBy(20.milliseconds)

            startFolding()
            finishUnfolding()

            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchInterrupted_coolDownExtendedByAnyEndEvent() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            startFolding()
            startUnfolding()
            advanceTimeBy(COOL_DOWN_DURATION - 10.milliseconds)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            advanceTimeBy(20.milliseconds)

            startFolding()
            finishUnfolding()

            verify(latencyTracker, never()).onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun displaySwitchTimedOut_trackingCancelled() {
        testScope.runTest {
            startInFoldedState(displaySwitchLatencyTracker)

            startUnfolding()
            advanceTimeBy(SCREEN_EVENT_TIMEOUT + 10.milliseconds)
            finishUnfolding()

            verify(latencyTracker).onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
        }
    }

    @Test
    fun foldingStarted_screenStillOn_eventSentOnlyAfterScreenSwitches() {
        // can happen for both folding and unfolding (with animations off) but it's more likely to
        // happen when folding as waiting for screen on is the default case then
        testScope.runTest {
            startInUnfoldedState(displaySwitchLatencyTracker)
            setDeviceState(FOLDED)
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verifyNoMoreInteractions(displaySwitchLatencyLogger)

            powerInteractor.setScreenPowerState(SCREEN_OFF)
            runCurrent()
            powerInteractor.setScreenPowerState(SCREEN_ON)
            runCurrent()

            verify(displaySwitchLatencyLogger).log(any())
        }
    }

    private suspend fun TestScope.startInFoldedState(tracker: DisplaySwitchLatencyTracker) {
        setDeviceState(FOLDED)
        tracker.start()
        runCurrent()
    }

    private suspend fun TestScope.startInUnfoldedState(tracker: DisplaySwitchLatencyTracker) {
        setDeviceState(UNFOLDED)
        tracker.start()
        runCurrent()
    }

    private suspend fun TestScope.startUnfolding() {
        setDeviceState(HALF_FOLDED)
        powerInteractor.setScreenPowerState(SCREEN_OFF)
        runCurrent()
    }

    private suspend fun TestScope.startFolding() {
        setDeviceState(FOLDED)
        powerInteractor.setScreenPowerState(SCREEN_OFF)
        runCurrent()
    }

    private fun TestScope.finishFolding() {
        powerInteractor.setScreenPowerState(SCREEN_ON)
        runCurrent()
    }

    private fun TestScope.finishUnfolding() {
        unfoldTransitionProgressProvider.onTransitionStarted()
        runCurrent()
    }

    private suspend fun setDeviceState(state: DeviceState) {
        foldStateRepository.emit(state)
    }
}
