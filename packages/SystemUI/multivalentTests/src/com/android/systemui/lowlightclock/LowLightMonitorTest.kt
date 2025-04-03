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
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dream.lowlight.LowLightDreamManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.domain.interactor.displayStateInteractor
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.shared.condition.Condition
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class LowLightMonitorTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.lowLightDreamManager: LowLightDreamManager by
        Kosmos.Fixture { mock<LowLightDreamManager>() }

    private val Kosmos.monitor: Monitor by Kosmos.Fixture { Monitor(testDispatcher.asExecutor()) }

    private val Kosmos.logger: LowLightLogger by
        Kosmos.Fixture { LowLightLogger(logcatLogBuffer()) }

    private val Kosmos.condition: FakeCondition by
        Kosmos.Fixture { FakeCondition(scope = applicationCoroutineScope, initialValue = null) }

    private val Kosmos.underTest: LowLightMonitor by
        Kosmos.Fixture {
            LowLightMonitor(
                { lowLightDreamManager },
                monitor,
                { setOf(condition) },
                displayStateInteractor,
                logger,
                dreamComponent,
                packageManager,
                backgroundScope,
            )
        }

    private var Kosmos.dreamComponent: ComponentName? by
        Kosmos.Fixture { ComponentName("test", "test.LowLightDream") }

    private fun Kosmos.setDisplayOn(screenOn: Boolean) {
        displayRepository.setDefaultDisplayOff(!screenOn)
    }

    @Before
    fun setUp() {
        kosmos.setDisplayOn(false)
    }

    @Test
    fun testSetAmbientLowLightWhenInLowLight() =
        kosmos.runTest {
            underTest.start()

            // Turn on screen
            setDisplayOn(true)

            // Set conditions to true
            condition.setValue(true)

            // Verify setting low light when condition is true
            verify(lowLightDreamManager)
                .setAmbientLightMode(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)
        }

    @Test
    fun testExitAmbientLowLightWhenNotInLowLight() =
        kosmos.runTest {
            underTest.start()

            // Turn on screen
            setDisplayOn(true)

            // Set conditions to true then false
            condition.setValue(true)
            clearInvocations(lowLightDreamManager)
            condition.setValue(false)

            // Verify ambient light toggles back to light mode regular
            verify(lowLightDreamManager)
                .setAmbientLightMode(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
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
    fun testSubscribeIfScreenIsOnWhenStarting() =
        kosmos.runTest {
            setDisplayOn(true)

            underTest.start()
            assertThat(condition.started).isTrue()
        }

    @Test
    fun testNoSubscribeIfDreamNotPresent() =
        kosmos.runTest {
            setDisplayOn(true)
            dreamComponent = null

            underTest.start()
            assertThat(condition.started).isFalse()
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
