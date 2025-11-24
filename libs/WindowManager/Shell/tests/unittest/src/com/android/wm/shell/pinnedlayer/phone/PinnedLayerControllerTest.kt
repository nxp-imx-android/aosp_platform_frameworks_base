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

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.verifyNoInteractions

/**
 * Unit tests against [PinnedLayerController]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerControllerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerControllerTest : ShellTestCase() {
    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var presentationController: PinnedLayerPresentationController
    @Mock private lateinit var windowDragTransitionHandler: WindowDragTransitionHandler

    private lateinit var pinnedLayerController: PinnedLayerController

    @Before
    fun setup() {
        pinnedLayerController =
            PinnedLayerController(
                shellInit,
                transitions,
                presentationController,
                windowDragTransitionHandler,
            )
    }

    @Test
    fun dragEnded_notPinnedTask_doNothing() {
        val task = setupTask()

        pinnedLayerController.onDragEnded(task, EMPTY_RECT)

        verifyNoInteractions(transitions)
    }

    // TODO(b/449118417): Add more tests to verify pinned tasks

    private fun setupTask(
        displayId: Int = DEFAULT_DISPLAY,
        bounds: Rect = DEFAULT_TASK_BOUNDS,
        taskId: Int? = null,
    ): RunningTaskInfo {
        val task =
            DesktopTestHelpers.createFreeformTask(
                displayId = displayId,
                bounds = bounds,
                taskId = taskId,
            )
        return task
    }

    private companion object {
        private const val DEFAULT_DISPLAY = 0

        private val DEFAULT_TASK_BOUNDS = Rect(240, 240, 240, 240)
        private val EMPTY_RECT = Rect(0, 0, 0, 0)
    }
}
