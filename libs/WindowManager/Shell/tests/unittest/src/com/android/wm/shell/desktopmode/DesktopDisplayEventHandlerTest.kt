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
import com.android.server.display.feature.flags.Flags as DisplayFlags
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopDisplayEventHandler]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayEventHandlerTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopDisplayEventHandlerTest : ShellTestCase() {
    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var displayController: DisplayController
    @Mock private lateinit var mockShellController: ShellController
    @Mock private lateinit var mockRootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var mockDesksOrganizer: DesksOrganizer
    @Mock private lateinit var mockDesktopUserRepositories: DesktopUserRepositories
    @Mock private lateinit var mockDesktopRepository: DesktopRepository
    @Mock private lateinit var mockDesktopTasksController: DesktopTasksController
    @Mock private lateinit var desktopDisplayModeController: DesktopDisplayModeController
    @Mock private lateinit var mockDesksTransitionObserver: DesksTransitionObserver
    private val desktopRepositoryInitializer = FakeDesktopRepositoryInitializer()
    private val testScope = TestScope()
    private val desktopState = FakeDesktopState()

    private lateinit var shellInit: ShellInit
    private lateinit var handler: DesktopDisplayEventHandler

    private val onDisplaysChangedListenerCaptor = argumentCaptor<OnDisplaysChangedListener>()
    private val externalDisplayId = 100

    @Before
    fun setUp() {
        shellInit = spy(ShellInit(testExecutor))
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockDesktopRepository.userId).thenReturn(PRIMARY_USER_ID)
        handler =
            DesktopDisplayEventHandler(
                shellInit,
                testScope.backgroundScope,
                mockShellController,
                displayController,
                mockRootTaskDisplayAreaOrganizer,
                mockDesksOrganizer,
                desktopRepositoryInitializer,
                mockDesktopUserRepositories,
                mockDesktopTasksController,
                desktopDisplayModeController,
                mockDesksTransitionObserver,
                desktopState,
            )
        shellInit.init()
        verify(displayController)
            .addDisplayWindowListener(onDisplaysChangedListenerCaptor.capture())
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_supportsDesks_desktopRepositoryInitialized_desktopFirst_createsDesk() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(SECOND_DISPLAY)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(eq(SECOND_DISPLAY), eq(PRIMARY_USER_ID), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_supportsDesks_desktopRepositoryInitialized_touchFirst_warmsUpDesk() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesksOrganizer)
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
    )
    fun testDisplayAdded_desktopFirst_supportsDesks_desktopRepositoryInitialized_createsAndActivatesDesk() =
        testScope.runTest {
            val desktopFirstDisplay = 2
            desktopState.overrideDesktopModeSupportPerDisplay[desktopFirstDisplay] = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(desktopFirstDisplay)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(desktopFirstDisplay),
                    userId = eq(PRIMARY_USER_ID),
                    activateDesk = eq(true),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_supportsDesks_desktopRepositoryNotInitialized_doesNotCreateDesk() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_desktopRepositoryInitializedTwice_desktopFirst_createsDeskOnce() =
        testScope.runTest {
            desktopState.canEnterDesktopMode = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(SECOND_DISPLAY)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController, times(1))
                .createDesk(eq(SECOND_DISPLAY), eq(PRIMARY_USER_ID), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_desktopRepositoryInitializedTwice_touchFirst_warmsUpDeskOnce() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesksOrganizer, times(1))
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_desktopRepositoryInitialized_deskExists_doesNotCreateDeskOrWarmsUp() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(1)

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any())
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_cannotEnterDesktopMode_doesNotCreateDeskOrWarmsUp() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(DEFAULT_DISPLAY)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any())
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_noDesksRemain_desktopFirst_createsDesk() =
        testScope.runTest {
            desktopState.canEnterDesktopMode = true
            whenever(mockDesktopRepository.getNumberOfDesks(SECOND_DISPLAY)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(SECOND_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    eq(SECOND_DISPLAY),
                    eq(PRIMARY_USER_ID),
                    activateDesk = any(),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_noDesksRemain_touchFirst_warmsUpDesk() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesksOrganizer)
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
    )
    fun testDeskRemoved_noDesksRemain_desktopFirstDisplay_createsAndActivatesDesk() =
        testScope.runTest {
            val desktopFirstDisplay = 2
            desktopState.overrideDesktopModeSupportPerDisplay[desktopFirstDisplay] = true
            whenever(mockDesktopRepository.getNumberOfDesks(desktopFirstDisplay)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(desktopFirstDisplay, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(desktopFirstDisplay),
                    userId = eq(PRIMARY_USER_ID),
                    activateDesk = eq(true),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_desksRemain_doesNotCreateDeskOrWarmsUpDesk() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(1)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController, never()).createDesk(DEFAULT_DISPLAY)
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testUserChanged_createsOrWarmsUpDeskWhenNeeded() =
        testScope.runTest {
            val userId = 11
            desktopState.overrideDesktopModeSupportPerDisplay[2] = true
            desktopState.overrideDesktopModeSupportPerDisplay[3] = true
            desktopState.overrideDesktopModeSupportPerDisplay[4] = true
            val userChangeListenerCaptor = argumentCaptor<UserChangeListener>()
            verify(mockShellController).addUserChangeListener(userChangeListenerCaptor.capture())
            val mockRepository = mock<DesktopRepository>()
            whenever(mockRepository.userId).thenReturn(userId)
            whenever(mockDesktopUserRepositories.getProfile(userId)).thenReturn(mockRepository)
            whenever(mockRepository.getNumberOfDesks(displayId = DEFAULT_DISPLAY)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = SECOND_DISPLAY)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = 3)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = 4)).thenReturn(1)
            whenever(mockRootTaskDisplayAreaOrganizer.displayIds)
                .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY, 3, 4))
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            handler.onDisplayAdded(displayId = DEFAULT_DISPLAY)
            handler.onDisplayAdded(displayId = SECOND_DISPLAY)
            handler.onDisplayAdded(displayId = 3)
            handler.onDisplayAdded(displayId = 4)
            runCurrent()

            clearInvocations(mockDesktopTasksController)
            userChangeListenerCaptor.lastValue.onUserChanged(userId, context)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(2),
                    userId = eq(userId),
                    activateDesk = any(),
                    onResult = any(),
                )
            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(3),
                    userId = eq(userId),
                    activateDesk = any(),
                    onResult = any(),
                )
            verify(mockDesktopTasksController, never())
                .createDesk(
                    displayId = eq(4),
                    userId = any(),
                    activateDesk = any(),
                    onResult = any(),
                )
        }

    @Test
    fun testConnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(externalDisplayId)
        verify(desktopDisplayModeController).updateExternalDisplayWindowingMode(externalDisplayId)
        verify(desktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    @Test
    fun testDisconnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(externalDisplayId)
        verify(desktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    @Test
    @EnableFlags(DisplayFlags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    fun testDesktopModeEligibleChanged() {
        onDisplaysChangedListenerCaptor.lastValue.onDesktopModeEligibleChanged(externalDisplayId)
        verify(desktopDisplayModeController).updateExternalDisplayWindowingMode(externalDisplayId)
        verify(desktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    private class FakeDesktopRepositoryInitializer : DesktopRepositoryInitializer {
        override var deskRecreationFactory: DesktopRepositoryInitializer.DeskRecreationFactory =
            DesktopRepositoryInitializer.DeskRecreationFactory { _, _, deskId -> deskId }

        override val isInitialized: MutableStateFlow<Boolean> = MutableStateFlow(false)

        override fun initialize(userRepositories: DesktopUserRepositories) {
            isInitialized.value = true
        }
    }

    companion object {
        private const val SECOND_DISPLAY = 2
        private const val PRIMARY_USER_ID = 10
    }
}
