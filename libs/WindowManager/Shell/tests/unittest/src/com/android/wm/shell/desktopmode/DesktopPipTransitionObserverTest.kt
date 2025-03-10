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

import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.WindowManager.TRANSIT_PIP
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [DesktopPipTransitionObserver].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopPipTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopPipTransitionObserverTest : ShellTestCase() {

    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    private lateinit var observer: DesktopPipTransitionObserver

    private val transition = Binder()
    private var onSuccessInvokedCount = 0

    @Before
    fun setUp() {
        observer = DesktopPipTransitionObserver()

        onSuccessInvokedCount = 0
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP)
    fun onTransitionReady_taskInPinnedWindowingMode_onSuccessInvoked() {
        val taskId = 1
        val pipTransition = createPendingPipTransition(taskId)
        val successfulChange = createChange(taskId, WINDOWING_MODE_PINNED)
        observer.addPendingPipTransition(pipTransition)

        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(
                TRANSIT_PIP, /* flags= */
                0
            ).apply { addChange(successfulChange) },
        )

        assertThat(onSuccessInvokedCount).isEqualTo(1)
    }

    private fun createPendingPipTransition(
        taskId: Int
    ): DesktopPipTransitionObserver.PendingPipTransition {
        return DesktopPipTransitionObserver.PendingPipTransition(
            token = transition,
            taskId = taskId,
            onSuccess = { onSuccessInvokedCount += 1 },
        )
    }

    private fun createChange(taskId: Int, windowingMode: Int): TransitionInfo.Change {
        return TransitionInfo.Change(mock(), mock()).apply {
            taskInfo =
                TestRunningTaskInfoBuilder()
                    .setTaskId(taskId)
                    .setWindowingMode(windowingMode)
                    .build()
        }
    }
}
