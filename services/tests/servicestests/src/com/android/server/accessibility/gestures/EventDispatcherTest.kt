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
package com.android.server.accessibility.gestures

import android.content.Context
import android.os.SystemClock
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_UP
import android.view.accessibility.AccessibilityEvent
import com.android.server.accessibility.AccessibilityManagerService
import com.android.server.accessibility.EventStreamTransformation
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidTestingRunner::class)
class EventDispatcherTest {

    private val ams: AccessibilityManagerService = mock<AccessibilityManagerService>()
    private val receiver: EventStreamTransformation = mock<EventStreamTransformation>()

    private lateinit var state: TouchState
    private lateinit var dispatcher: EventDispatcher

    @Before
    fun setUp() {
        state = TouchState(DEFAULT_DISPLAY, ams)
        dispatcher = EventDispatcher(
            mock<Context>(),
            DEFAULT_DISPLAY,
            ams,
            receiver,
            state
        )
    }

    @Test
    fun populateAccessibilityEvent_returnsEventWithCorrectProperties() {
        val windowId = 1
        val displayId = 2
        val type = AccessibilityEvent.TYPE_TOUCH_INTERACTION_END

        ams.stub { on { activeWindowId } doReturn windowId }
        dispatcher.mDisplayId = displayId
        val event = dispatcher.populateAccessibilityEvent(type)

        assertThat(event.windowId).isEqualTo(windowId)
        assertThat(event.displayId).isEqualTo(displayId)
        assertThat(event.eventType).isEqualTo(type)
    }

    @Test
    fun sendMotionEvent_withDifferentAction_dispatchesEventWithNewAction() {
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        sendMotionEvent(downEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.firstValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
    }

    @Test
    fun sendMotionEvent_withDownAction_preservesOriginalDownTime() {
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        sendMotionEvent(downEvent, ACTION_DOWN)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.firstValue
        assertThat(captured.action).isEqualTo(ACTION_DOWN)
        assertThat(captured.downTime).isEqualTo(downTime)
    }

    @Test
    fun sendMotionEvent_forHoverEventAfterTouch_reusesPreviousDownTime() {
        // cache a downtime
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        val upEvent = createMotionEvent(ACTION_UP, downTime, downTime + 10)
        sendMotionEvent(downEvent, ACTION_DOWN)
        sendMotionEvent(upEvent, ACTION_UP)

        // use a different downtime for hover event
        val hoverEvent = createMotionEvent(ACTION_HOVER_ENTER, downTime + 20, downTime + 20)
        sendMotionEvent(hoverEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver, times(3)).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.lastValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
        assertThat(captured.downTime).isEqualTo(downTime)
    }

    @Test
    fun sendMotionEvent_forHoverEventWithNoPriorTouch_setsDownTimeToEventTime() {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = downTime + 10
        val hoverEvent = createMotionEvent(ACTION_HOVER_ENTER, downTime, eventTime)
        sendMotionEvent(hoverEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.lastValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
        assertThat(captured.downTime).isEqualTo(eventTime)
    }

    // Only work for 1 pointer events
    private fun sendMotionEvent(event: MotionEvent, action: Int) {
        val pointerId = event.getPointerId(event.actionIndex)
        val pointerIdBits = (1 shl pointerId)
        dispatcher.sendMotionEvent(event, action, event, pointerIdBits, 0)
    }

    private fun createMotionEvent(action: Int, downTime: Long, eventTime: Long): MotionEvent {
        val x = 1f
        val y = 2f
        val pressure = 3f
        val size = 1f
        val metaState = 0
        val xPrecision = 0f
        val yPrecision = 0f
        val edgeFlags = 0
        val deviceId = 0
        val source = SOURCE_TOUCHSCREEN
        val displayId = DEFAULT_DISPLAY
        return MotionEvent.obtain(
            downTime, eventTime, action, x, y, pressure, size, metaState,
            xPrecision, yPrecision, deviceId, edgeFlags, source, displayId
        )
    }
}
