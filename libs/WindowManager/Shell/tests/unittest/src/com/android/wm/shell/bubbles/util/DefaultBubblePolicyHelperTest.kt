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

package com.android.wm.shell.bubbles.util

import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.widget.Toast
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

/**
 * Unit tests for [DefaultBubblePolicyHelper].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DefaultBubblePolicyHelperTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class DefaultBubblePolicyHelperTest : ShellTestCase() {

    @Mock private lateinit var errorToast: Toast
    @Mock private lateinit var taskOrganizer: ShellTaskOrganizer
    private lateinit var taskViewTaskController: TaskViewTaskController
    private lateinit var taskView: TaskView
    private lateinit var bubble: Bubble
    private lateinit var captionInsetsOwner: Binder
    private lateinit var taskToken: WindowContainerToken
    private lateinit var taskInfo: RunningTaskInfo

    @JvmField
    @Rule
    val extendedMockitoRule = ExtendedMockitoRule.Builder(this)
        .spyStatic(Toast::class.java)
        .setStrictness(Strictness.LENIENT)
        .build()!!

    @Before
    fun setUp() {
        taskViewTaskController = mock {
            on { taskOrganizer } doReturn taskOrganizer
        }

        captionInsetsOwner = Binder()
        taskView = mock {
            on { controller } doReturn taskViewTaskController
            on { captionInsetsOwner } doReturn captionInsetsOwner
        }

        bubble = mock {
            on { taskView } doReturn taskView
            on { key } doReturn "bubble_key"
        }

        taskToken = mock {
            on { asBinder() } doReturn Binder()
        }
        taskInfo = RunningTaskInfo().apply {
            taskId = 123
            token = taskToken
        }

        ExtendedMockito.doReturn(errorToast).`when` {
            Toast.makeText(any(), anyInt(), anyInt())
        }
    }

    @Test
    fun testMoveExistingTaskOutOfBubble() {
        DefaultBubblePolicyHelper.moveExistingTaskOutOfBubble(bubble, taskInfo)

        val wctCaptor = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(taskOrganizer).applyTransaction(wctCaptor.capture())
        verify(taskViewTaskController).notifyTaskRemovalStarted(taskInfo)
    }

    @Test
    fun testShowBubbleNotSupportedErrorToast() {
        DefaultBubblePolicyHelper.showBubbleNotSupportedErrorToast(mContext)

        verify(errorToast).show()
    }

    @Test
    fun testIsValidToBubble_whenSupportsMultiWindow_returnsTrue() {
        taskInfo.supportsMultiWindow = true

        assertThat(DefaultBubblePolicyHelper.isValidToBubble(taskInfo)).isTrue()
    }

    @Test
    fun testIsValidToBubble_whenNotSupportsMultiWindow_returnsFalse() {
        taskInfo.supportsMultiWindow = false

        assertThat(DefaultBubblePolicyHelper.isValidToBubble(taskInfo)).isFalse()
    }
}
