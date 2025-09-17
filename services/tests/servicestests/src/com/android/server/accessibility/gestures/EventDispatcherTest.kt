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
import android.view.accessibility.AccessibilityEvent
import androidx.test.runner.AndroidJUnit4
import com.android.server.accessibility.AccessibilityManagerService
import com.android.server.accessibility.EventStreamTransformation
import junit.framework.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
class EventDispatcherTest {

    private val ams: AccessibilityManagerService = mock<AccessibilityManagerService>()
    private var dispatcher: EventDispatcher = EventDispatcher(
        mock<Context>(),
        0,
        ams,
        mock<EventStreamTransformation>(),
        mock<TouchState>()
    )

    @Test
    fun populateAccessibilityEvent_matchesParameters() {
        val windowId = 1
        val displayId = 2
        val type = AccessibilityEvent.TYPE_TOUCH_INTERACTION_END

        ams.stub { on { activeWindowId } doReturn windowId }
        dispatcher.mDisplayId = displayId
        val event = dispatcher.populateAccessibilityEvent(type)

        Assert.assertEquals(event.windowId, windowId)
        Assert.assertEquals(event.displayId, displayId)
        Assert.assertEquals(event.eventType, type)
    }
}
