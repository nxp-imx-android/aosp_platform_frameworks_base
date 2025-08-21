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

package com.android.wm.shell.bubbles.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_STARTED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_STARTED
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleSessionTrackerImpl]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleSessionTrackerImplTest {

    private val instanceIdSequence = FakeInstanceIdSequence()
    private val uiEventLoggerFake = UiEventLoggerFake()
    private val bubbleLogger = BubbleLogger(uiEventLoggerFake)
    private val bubbleSessionTracker = BubbleSessionTrackerImpl(instanceIdSequence, bubbleLogger)

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
    }

    @Test
    fun startSession_logsNewSessionId() {
        bubbleSessionTracker.startBubbleBar()
        bubbleSessionTracker.stopBubbleBar()
        bubbleSessionTracker.startBubbleBar()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(3)
        val firstSessionStart = uiEventLoggerFake.logs.first()
        val secondSessionStart = uiEventLoggerFake.logs.last()
        assertThat(firstSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(secondSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(firstSessionStart.instanceId).isNotEqualTo(secondSessionStart.instanceId)
    }

    @Test
    fun endSession_logsSameSessionId() {
        bubbleSessionTracker.startBubbleBar()
        bubbleSessionTracker.stopBubbleBar()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        val sessionStart = uiEventLoggerFake.logs.first()
        val sessionEnd = uiEventLoggerFake.logs.last()
        assertThat(sessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(sessionEnd.eventId).isEqualTo(BUBBLE_BAR_SESSION_ENDED.id)
        assertThat(sessionStart.instanceId).isEqualTo(sessionEnd.instanceId)
    }

    @Test
    fun logCorrectEventId() {
        bubbleSessionTracker.startBubbleBar()
        bubbleSessionTracker.stopBubbleBar()
        bubbleSessionTracker.startFloating()
        bubbleSessionTracker.stopFloating()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(4)
        assertThat(uiEventLoggerFake.logs.map { it.eventId })
            .containsExactly(
                BUBBLE_BAR_SESSION_STARTED.id,
                BUBBLE_BAR_SESSION_ENDED.id,
                BUBBLE_SESSION_STARTED.id,
                BUBBLE_SESSION_ENDED.id
            )
            .inOrder()
    }

    @Test
    fun stopSession_noActiveSession_shouldNotLog() {
        bubbleSessionTracker.stopBubbleBar()

        assertThat(uiEventLoggerFake.logs).isEmpty()
    }

    class FakeInstanceIdSequence : InstanceIdSequence(/* instanceIdMax= */ 10) {

        var id = -1
            private set

        override fun newInstanceId(): InstanceId {
            id = if (id == -1 || id == 10) 1 else id + 1
            return InstanceId.fakeInstanceId(id)
        }
    }
}
