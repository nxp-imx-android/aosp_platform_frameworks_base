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

package com.android.wm.shell.pinnedlayer.phone

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.ActivityManager.AppTask.WindowingLayer
import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_NONE
import android.window.TransitionRequestInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Unit tests against [PinnedLayerController]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerControllerTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerControllerTests : ShellTestCase() {

    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var startTransaction: SurfaceControl.Transaction
    @Mock private lateinit var finishTransaction: SurfaceControl.Transaction

    private lateinit var pinnedLayerController: PinnedLayerController

    @Before
    fun setup() {
        pinnedLayerController = PinnedLayerController(shellInit, transitions)
    }

    @Test
    fun testHandlePinRequest_noPinnedWindow_shouldPinWindow() {
        val transition = mock<IBinder>()
        val callback = mock<IRemoteCallback>()
        val requestInfo =
            setupWindowingLayerTransition(WINDOWING_LAYER_PINNED, callback, triggerTaskId = 0)
        val transitionInfo = TransitionInfo(TRANSIT_CHANGE, FLAG_NONE)

        pinnedLayerController.handleRequest(transition, requestInfo)
        pinnedLayerController.startAnimation(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction
        ) {}

        verifyCallbackResult(callback, RESULT_APPROVED)
        assertTrue(pinnedLayerController.isPinned(requestInfo.triggerTask!!.taskId))
    }

    private fun verifyCallbackResult(callback: IRemoteCallback, expected: Int) {
        val bundleCaptor = ArgumentCaptor.forClass(Bundle::class.java)
        verify(callback).sendResult(bundleCaptor.capture())

        val bundle = bundleCaptor.value
        assertNotNull(bundle, "Result callback should receive bundle, but it's null")
        assertEquals(expected, bundle.getInt(REMOTE_CALLBACK_RESULT_KEY))
    }

    private fun setupWindowingLayerTransition(
        @WindowingLayer layer: Int = WINDOWING_LAYER_NORMAL_APP,
        callback: IRemoteCallback,
        triggerTaskId: Int = 0,
    ): TransitionRequestInfo {
        val triggerTask =
            RunningTaskInfo().apply {
                token = MockToken().token()
                taskId = triggerTaskId
            }
        val windowingLayerChange = TransitionRequestInfo.WindowingLayerChange(layer, callback)
        return TransitionRequestInfo(
            TRANSIT_CHANGE,
            triggerTask,
            null,
            null,
            null,
            null,
            null,
            windowingLayerChange,
            0,
            0,
        )
    }
}
