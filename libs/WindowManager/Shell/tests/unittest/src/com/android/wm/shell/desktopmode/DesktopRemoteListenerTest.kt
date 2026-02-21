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

package com.android.wm.shell.desktopmode

import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.sysui.ShellController
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopRemoteListener]
 *
 * Usage: `atest WMShellUnitTests:DesktopRemoteListenerTest`
 */
@SmallTest
class DesktopRemoteListenerTest : ShellTestCase() {

    private val shellExecutor = mock<ShellExecutor>()
    private val currentRepository = mock<DesktopRepository>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val shellController = mock<ShellController>()
    private val transitionStateHolder = mock<TransitionStateHolder>()
    private val remoteListener =
        mock<SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>>()
    private val desktopTaskListener = mock<IDesktopTaskListener>()
    private val listener =
        DesktopRemoteListener(
            shellExecutor,
            userRepositories,
            shellController,
            transitionStateHolder,
        )

    @Before
    fun setUp() {
        whenever(userRepositories.current).thenReturn(currentRepository)
        // Set up the remote listener to execute the callback with the mock IDesktopTaskListener
        whenever(remoteListener.call(any())).thenAnswer {
            val callback =
                it.getArgument(0) as SingleInstanceRemoteListener.RemoteCall<IDesktopTaskListener>
            callback.accept(desktopTaskListener)
        }

        listener.register(remoteListener)
    }

    @Test
    fun testOnEnterDesktopModeTransitionStarted() {
        val transitionDuration = 100
        listener.onEnterDesktopModeTransitionStarted(transitionDuration)
        verify(desktopTaskListener).onEnterDesktopModeTransitionStarted(transitionDuration)
    }

    @Test
    fun testOnExitDesktopModeTransitionStarted() {
        val transitionDuration = 200
        val shouldEndUpAtHome = true
        listener.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        verify(desktopTaskListener)
            .onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
    }

    @Test
    fun onTaskbarCornerRoundingUpdate_callsRemoteListener() {
        listener.onTaskbarCornerRoundingUpdate(
            hasTasksRequiringTaskbarRounding = true,
            displayId = 2,
        )
        verify(desktopTaskListener)
            .onTaskbarCornerRoundingUpdate(
                /* hasTasksRequiringTaskbarRounding= */ true,
                /* displayId= */ 2,
            )
    }

    @Test
    fun unregister_callbacksCannotBeCalled() {
        listener.unregister()
        // After stopping, the remote listener should not be called.
        // We can't directly verify that the internal `remoteListener` is null,
        // but we can verify that no more calls are made to it.
        val transitionDuration = 100
        val shouldEndUpAtHome = true

        listener.onEnterDesktopModeTransitionStarted(transitionDuration)
        listener.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        listener.onTaskbarCornerRoundingUpdate(
            hasTasksRequiringTaskbarRounding = true,
            displayId = 2,
        )

        verify(desktopTaskListener, never()).onEnterDesktopModeTransitionStarted(any())
        verify(desktopTaskListener, never()).onExitDesktopModeTransitionStarted(any(), any())
        verify(desktopTaskListener, never()).onTaskbarCornerRoundingUpdate(any(), any())
    }
}
