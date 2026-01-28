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

package com.android.wm.shell.desktopai.core

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopai.api.ITriggerManager
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.desktopai.api.config.TriggerStrategy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [DesktopAiOrchestrator].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopAiOrchestratorTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopAiOrchestratorTest : ShellTestCase() {

    private val triggerManager = mock<ITriggerManager>()
    private lateinit var orchestrator: DesktopAiOrchestrator

    @Before
    fun setUp() {
        orchestrator = DesktopAiOrchestrator(triggerManager)
    }

    @Test
    fun registerCuj_callsTriggerManager() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val config = CujConfiguration("TEST_CUJ", strategy)

        orchestrator.registerCuj(config)

        verify(triggerManager).registerTrigger(eq(strategy), any())
    }

    @Test
    fun onTriggerReceived_executesWithoutError() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val config = CujConfiguration("TEST_CUJ", strategy)
        val callbackCaptor = argumentCaptor<(TriggerEvent) -> Unit>()

        orchestrator.registerCuj(config)
        verify(triggerManager).registerTrigger(eq(strategy), callbackCaptor.capture())

        val event = TriggerEvent(TriggerEventType.SYSTEM, "TEST_EVENT")
        // Invoking the captured callback to ensure it processes the event (currently just logs)
        callbackCaptor.firstValue.invoke(event)
    }
}
