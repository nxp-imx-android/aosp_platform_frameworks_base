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
package com.android.systemui.lowlightclock

import android.content.ComponentName
import android.content.packageManager
import android.content.res.mainResources
import android.provider.Settings
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dream.lowlight.LowLightDreamManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.domain.interactor.displayStateInteractor
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.dreams.domain.interactor.dreamSettingsInteractorKosmos
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.shared.condition.Condition
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.systemui.user.domain.interactor.userLockedInteractor
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class LowLightMonitorTest : SysuiTestCase() {
    val kosmos =
        testKosmos()
            .apply { mainResources = mContext.orCreateTestableResources.resources }
            .useUnconfinedTestDispatcher()

    private val ambientLightMode: MutableStateFlow<Int> =
        MutableStateFlow(LowLightDreamManager.AMBIENT_LIGHT_MODE_UNKNOWN)

    private val Kosmos.lowLightDreamManager: LowLightDreamManager by
        Kosmos.Fixture {
            mock<LowLightDreamManager> {
                on { setAmbientLightMode(any()) } doAnswer
                    { invocation ->
                        val mode = invocation.arguments[0] as Int
                        ambientLightMode.value = mode
                    }
            }
        }

    private val Kosmos.monitor: Monitor by Kosmos.Fixture { Monitor(testDispatcher.asExecutor()) }

    private val Kosmos.logger: LowLightLogger by
        Kosmos.Fixture { LowLightLogger(logcatLogBuffer()) }

    private val Kosmos.condition: FakeCondition by
        Kosmos.Fixture { FakeCondition(scope = applicationCoroutineScope, initialValue = false) }

    private val Kosmos.underTest: LowLightMonitor by
        Kosmos.Fixture {
            LowLightMonitor(
                lowLightDreamManager = { lowLightDreamManager },
                conditionsMonitor = monitor,
                lowLightConditions = { setOf(condition) },
                dreamSettingsInteractor = dreamSettingsInteractorKosmos,
                displayStateInteractor = displayStateInteractor,
                logger = logger,
                lowLightDreamService = dreamComponent,
                packageManager = packageManager,
                scope = backgroundScope,
                commandRegistry = commandRegistry,
                userLockedInteractor = userLockedInteractor,
                keyguardInteractor = keyguardInteractor,
                powerInteractor = powerInteractor,
            )
        }

    private var Kosmos.dreamComponent: ComponentName? by
        Kosmos.Fixture { ComponentName("test", "test.LowLightDream") }

    private val Kosmos.printWriter: PrintWriter by Kosmos.Fixture { PrintWriter(StringWriter()) }

    private fun Kosmos.setDisplayOn(screenOn: Boolean) {
        displayRepository.setDefaultDisplayOff(!screenOn)
    }

    private fun Kosmos.setDreamEnabled(enabled: Boolean) {
        fakeSettings.putBoolForUser(
            Settings.Secure.SCREENSAVER_ENABLED,
            enabled,
            selectedUserInteractor.getSelectedUserId(),
        )
    }

    private fun Kosmos.sendDebugCommand(enable: Boolean?) {
        val value: String =
            when (enable) {
                true -> "enable"
                false -> "disable"
                null -> "clear"
            }
        commandRegistry.onShellCommand(printWriter, arrayOf(LowLightMonitor.COMMAND_ROOT, value))
    }

    private fun Kosmos.setUserUnlocked(unlocked: Boolean) {
        fakeUserRepository.setUserUnlocked(selectedUserInteractor.getSelectedUserId(), unlocked)
    }

    @Before
    fun setUp() {
        kosmos.setDisplayOn(false)
        kosmos.setUserUnlocked(true)
        kosmos.powerInteractor.setAwakeForTest()
        kosmos.fakeKeyguardRepository.setKeyguardShowing(true)

        // Activate dreams on charge by default
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsEnabledByDefault,
            true,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault,
            true,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault,
            false,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault,
            false,
        )
    }

    @Test
    fun testSetAmbientLowLightWhenInLowLight() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            underTest.start()

            // Turn on screen
            setDisplayOn(true)

            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)

            // Set conditions to true
            condition.setValue(true)

            // Verify setting low light when condition is true
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)
        }

    @Test
    fun testExitAmbientLowLightWhenNotInLowLight() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            underTest.start()

            // Turn on screen
            setDisplayOn(true)

            // Set conditions to true then false
            condition.setValue(true)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)
            condition.setValue(false)

            // Verify ambient light toggles back to light mode regular
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenScreenTurnsOff() =
        kosmos.runTest {
            underTest.start()

            setDisplayOn(true)
            assertThat(condition.started).isTrue()

            // Verify removing subscription when screen turns off.
            setDisplayOn(false)
            assertThat(condition.started).isFalse()
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenDreamDisabled() =
        kosmos.runTest {
            underTest.start()

            setDisplayOn(true)
            setDreamEnabled(true)

            assertThat(condition.started).isTrue()

            setDreamEnabled(false)
            // Verify removing subscription when dream disabled.
            assertThat(condition.started).isFalse()
        }

    @Test
    fun testSubscribeIfScreenIsOnWhenStarting() =
        kosmos.runTest {
            setDisplayOn(true)

            underTest.start()
            assertThat(condition.started).isTrue()
        }

    @Test
    fun testDoNotEnterLowLightIfDeviceNotIdle() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            setDisplayOn(true)
            setUserUnlocked(true)
            condition.setValue(true)

            fakeKeyguardRepository.setKeyguardShowing(true)
            fakeKeyguardRepository.setDreaming(false)

            underTest.start()
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            fakeKeyguardRepository.setKeyguardShowing(false)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testDoNotEnterLowLightIfNotDreaming() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            setDisplayOn(true)
            setUserUnlocked(true)
            fakeKeyguardRepository.setKeyguardShowing(false)
            fakeKeyguardRepository.setDreaming(true)
            condition.setValue(true)

            underTest.start()
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            fakeKeyguardRepository.setDreaming(false)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testDoNotEnterLowLightWhenDozingAndAsleep() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            underTest.start()

            setDisplayOn(true)
            setUserUnlocked(true)
            fakeKeyguardRepository.setKeyguardShowing(false)
            fakeKeyguardRepository.setDreaming(true)
            condition.setValue(true)

            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            // Dozing started
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )

            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testNoSubscribeIfDreamNotPresent() =
        kosmos.runTest {
            setDisplayOn(true)
            dreamComponent = null

            underTest.start()
            assertThat(condition.started).isFalse()
        }

    @Test
    fun testForceLowlightToTrue() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            setDisplayOn(true)
            // low-light condition not met
            condition.setValue(false)

            underTest.start()
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)

            // force state to true
            sendDebugCommand(true)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            // clear forced state
            sendDebugCommand(null)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testForceLowlightToFalse() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            setDisplayOn(true)
            // low-light condition is met
            condition.setValue(true)

            underTest.start()
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            // force state to false
            sendDebugCommand(false)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)

            // clear forced state and ensure we go back to low-light
            sendDebugCommand(null)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)
        }

    @Test
    fun testLowlightForcedToTrueWhenUserLocked() =
        kosmos.runTest {
            val mode by collectLastValue(ambientLightMode)
            setDisplayOn(true)
            // low-light condition is false
            condition.setValue(false)

            underTest.start()
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)

            // locked user forces lowlight
            setUserUnlocked(false)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)

            // clear forced state and ensure we go back to regular mode
            setUserUnlocked(true)
            assertThat(mode).isEqualTo(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    private class FakeCondition(
        scope: CoroutineScope,
        initialValue: Boolean?,
        overriding: Boolean = false,
        @StartStrategy override val startStrategy: Int = START_EAGERLY,
    ) : Condition(scope, initialValue, overriding) {
        private var _started = false
        val started: Boolean
            get() = _started

        override suspend fun start() {
            _started = true
        }

        override fun stop() {
            _started = false
        }

        fun setValue(value: Boolean?) {
            value?.also { updateCondition(value) } ?: clearCondition()
        }
    }
}
