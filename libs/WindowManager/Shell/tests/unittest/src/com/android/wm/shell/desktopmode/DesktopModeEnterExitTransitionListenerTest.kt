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
import com.android.wm.shell.common.SingleInstanceRemoteListener
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopModeEnterExitTransitionListener] Usage: atest
 * WMShellUnitTests:DesktopModeEnterExitTransitionListenerTest
 */
@SmallTest
class DesktopModeEnterExitTransitionListenerTest : ShellTestCase() {

    private lateinit var remoteListener:
        SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>

    private lateinit var desktopTaskListener: IDesktopTaskListener

    private lateinit var listener: DesktopModeEnterExitTransitionListener

    @Before
    fun setUp() {
        remoteListener =
            mock<SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>>()
        desktopTaskListener = mock<IDesktopTaskListener>()
        listener = DesktopModeEnterExitTransitionListener()
        listener.register(remoteListener)
        // Set up the remote listener to execute the callback with the mock IDesktopTaskListener
        whenever(remoteListener.call(any())).thenAnswer {
            val callback =
                it.getArgument(0) as SingleInstanceRemoteListener.RemoteCall<IDesktopTaskListener>
            callback.accept(desktopTaskListener)
        }
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
    fun testUnregister() {
        listener.unregister()
        // After stopping, the remote listener should not be called.
        // We can't directly verify that the internal `remoteListener` is null,
        // but we can verify that no more calls are made to it.
        val transitionDuration = 100
        val shouldEndUpAtHome = true

        listener.onEnterDesktopModeTransitionStarted(transitionDuration)
        listener.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)

        verify(desktopTaskListener, never()).onEnterDesktopModeTransitionStarted(any())
        verify(desktopTaskListener, never()).onExitDesktopModeTransitionStarted(any(), any())
    }
}
