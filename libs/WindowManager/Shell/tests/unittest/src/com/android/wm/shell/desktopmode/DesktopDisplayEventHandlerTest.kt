/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.sysui.ShellInit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Test class for [DesktopDisplayEventHandler]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayEventHandlerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopDisplayEventHandlerTest : ShellTestCase() {
    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var displayController: DisplayController
    @Mock private lateinit var mockDesktopUserRepositories: DesktopUserRepositories
    @Mock private lateinit var mockDesktopRepository: DesktopRepository
    @Mock private lateinit var mockDesktopTasksController: DesktopTasksController
    @Mock private lateinit var desktopDisplayModeController: DesktopDisplayModeController

    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var shellInit: ShellInit
    private lateinit var handler: DesktopDisplayEventHandler

    private val onDisplaysChangedListenerCaptor = argumentCaptor<OnDisplaysChangedListener>()
    private val externalDisplayId = 100

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(DesktopModeStatus::class.java)
                .startMocking()

        shellInit = spy(ShellInit(testExecutor))
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        handler =
            DesktopDisplayEventHandler(
                context,
                shellInit,
                displayController,
                mockDesktopUserRepositories,
                mockDesktopTasksController,
                desktopDisplayModeController,
            )
        shellInit.init()
        verify(displayController)
            .addDisplayWindowListener(onDisplaysChangedListenerCaptor.capture())
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun testDisplayAdded_supportsDesks_createsDesk() {
        whenever(DesktopModeStatus.canEnterDesktopMode(context)).thenReturn(true)

        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)

        verify(mockDesktopTasksController).createDesk(DEFAULT_DISPLAY)
    }

    @Test
    fun testDisplayAdded_cannotEnterDesktopMode_doesNotCreateDesk() {
        whenever(DesktopModeStatus.canEnterDesktopMode(context)).thenReturn(false)

        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)

        verify(mockDesktopTasksController, never()).createDesk(DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_noDesksRemain_createsDesk() {
        whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(0)

        handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)

        verify(mockDesktopTasksController).createDesk(DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_desksRemain_doesNotCreateDesk() {
        whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(1)

        handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)

        verify(mockDesktopTasksController, never()).createDesk(DEFAULT_DISPLAY)
    }

    @Test
    fun testConnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(externalDisplayId)
        verify(desktopDisplayModeController).refreshDisplayWindowingMode()
    }

    @Test
    fun testDisconnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(externalDisplayId)
        verify(desktopDisplayModeController).refreshDisplayWindowingMode()
    }
}
