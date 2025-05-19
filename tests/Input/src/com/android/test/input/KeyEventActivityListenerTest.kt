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
package com.android.test.input

import android.Manifest
import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.UinputKeyboard
import com.android.hardware.input.Flags.FLAG_KEY_EVENT_ACTIVITY_DETECTION
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

@RequiresFlagsEnabled(FLAG_KEY_EVENT_ACTIVITY_DETECTION)
class KeyEventActivityListenerTest {
    private lateinit var inputManager: InputManager
    private lateinit var listener: InputManager.KeyEventActivityListener
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    @get:Rule
    val adoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.getUiAutomation(),
            Manifest.permission.LISTEN_FOR_KEY_ACTIVITY,
        )

    companion object {
        const val KEY_A = 30
    }

    @Before
    fun setUp() {
        lateinit var activity: CaptureEventActivity
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        listener = mock(InputManager.KeyEventActivityListener::class.java)
    }

    @After
    fun tearDown() {
        inputManager.unregisterKeyEventActivityListener(listener)
    }

    @Test
    fun testKeyActivityListener() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            val isRegistered = inputManager.registerKeyEventActivityListener(listener)
            assertTrue(isRegistered)
            val latch = CountDownLatch(1)
            doAnswer {
                    latch.countDown()
                    null
                }
                .`when`(listener)
                .onKeyEventActivity()
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            verify(listener, times(1)).onKeyEventActivity()
            val isUnregistered = inputManager.unregisterKeyEventActivityListener(listener)
            assertTrue(isUnregistered)
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            verifyNoMoreInteractions(listener)
        }
    }
}
