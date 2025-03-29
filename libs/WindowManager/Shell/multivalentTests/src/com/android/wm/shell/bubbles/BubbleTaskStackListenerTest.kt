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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.protolog.ProtoLog
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/** Unit tests for [BubbleTaskStackListener]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleTaskStackListenerTest {
    private val bubble = mock<Bubble>()
    private val bubbleController = mock<BubbleController>()
    private val bubbleData = mock<BubbleData>()
    private val bubbleTaskStackListener = BubbleTaskStackListener(
        bubbleController,
        bubbleData,
    )
    private val bubbleTaskId = 123
    private val task = ActivityManager.RunningTaskInfo().apply { taskId = bubbleTaskId }

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
    }

    @Test
    fun onActivityRestartAttempt_inStackAppBubbleRestart_selectsAndExpandsStack() {
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        verify(bubbleData).setSelectedBubbleAndExpandStack(bubble)
    }

    @Test
    fun onActivityRestartAttempt_overflowAppBubbleRestart_promotesFromOverflow() {
        bubbleData.stub {
            on { getOverflowBubbleWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        verify(bubbleController).promoteBubbleFromOverflow(bubble)
        verify(bubbleData).setExpanded(true)
    }
}
