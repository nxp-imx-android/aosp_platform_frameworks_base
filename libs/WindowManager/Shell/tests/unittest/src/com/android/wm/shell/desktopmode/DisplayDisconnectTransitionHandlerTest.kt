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

package com.android.wm.shell.desktopmode

import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DisplayAreaInfo
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.never

/**
 * Test class for {@link DesktopTasksController}
 *
 * Usage: atest WMShellUnitTests:DisplayDisconnectTransitionHandlerTest
 */
@SmallTest
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
class DisplayDisconnectTransitionHandlerTest() : ShellTestCase() {
    private val transitions = mock(Transitions::class.java)
    private val shellInit = mock(ShellInit::class.java)
    private val desktopTasksController = mock(DesktopTasksController::class.java)
    private val displayController = mock(DisplayController::class.java)
    private val rootTaskDisplayAreaOrganizer = mock(RootTaskDisplayAreaOrganizer::class.java)

    private lateinit var disconnectTransitionHandler: DisplayDisconnectTransitionHandler

    @Before
    fun setUp() {
        disconnectTransitionHandler =
            DisplayDisconnectTransitionHandler(
                transitions,
                shellInit,
                Optional.of(desktopTasksController),
                displayController,
                rootTaskDisplayAreaOrganizer,
            )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun handleRequest_noReparentDisplay_doesNotPerformDisconnect() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = INVALID_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChange(displayChange) }
        val transition = Binder()

        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        verify(desktopTasksController, never())
            .onDisplayDisconnect(SECOND_DISPLAY, DEFAULT_DISPLAY, transition)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun handleRequest_validReparentDisplay_performsDisconnect() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChange(displayChange) }
        val transition = Binder()

        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that the disconnect is forwarded to the controller.
        verify(desktopTasksController)
            .onDisplayDisconnect(SECOND_DISPLAY, DEFAULT_DISPLAY, transition)

        // Verify that the transition is added to the pending list, so it can be animated.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertTrue(
            disconnectTransitionHandler.startAnimation(transition, info, startT, finishT, finishCallback),
            "Disconnect transition should be handled"
        )
    }

    @Test
    fun handleRequest_disconnectWithoutDesktopController_addsPendingTransition() {
        // Re-initialize handler without the optional controller for this test.
        disconnectTransitionHandler =
            DisplayDisconnectTransitionHandler(
                transitions,
                shellInit,
                Optional.empty(), // No DesktopTasksController
                displayController,
                rootTaskDisplayAreaOrganizer,
            )

        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChange(displayChange) }
        val transition = Binder()

        // Handle the request.
        val wct = disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that no WCT is returned as there is no controller to generate one.
        assertTrue(wct == null)

        // Verify that the transition is added to the pending list, so it can be animated.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertTrue(
            disconnectTransitionHandler.startAnimation(transition, info, startT, finishT, finishCallback),
            "Disconnect transition should be handled even without DesktopTasksController"
        )
    }

    @Test
    fun handleRequest_notADisconnect_isNotHandled() {
        val transitionRequestInfo =
            TransitionRequestInfo(
                TRANSIT_CHANGE,
                /* triggerTask = */ null,
                /* remoteTransition= */ null,
            )
        // No DisplayChange is set, so this is not a disconnect transition.
        val transition = Binder()

        // Handle the request.
        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that the transition is NOT added to the pending list.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertFalse(
            disconnectTransitionHandler.startAnimation(transition, info, startT, finishT, finishCallback),
            "Non-disconnect transition should not be handled"
        )
    }

    companion object {
        const val SECOND_DISPLAY = 2
    }
}
