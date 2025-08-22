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

import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubbleLogger.Event
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_STARTED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_STARTED
import com.android.wm.shell.dagger.Bubbles
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup
import javax.inject.Inject

/**
 * Keeps track of the current bubble session and logs when sessions start and end.
 *
 * Sessions are identified using an [InstanceId].
 */
@WMSingleton
class BubbleSessionTrackerImpl
@Inject
constructor(
    @param:Bubbles private val instanceIdSequence: InstanceIdSequence,
    private val logger: BubbleLogger
) : BubbleSessionTracker {

    private var currentSession: InstanceId? = null

    override fun startBubbleBar() {
        start(BUBBLE_BAR_SESSION_STARTED)
    }

    override fun startFloating() {
        start(BUBBLE_SESSION_STARTED)
    }

    private fun start(event: Event) {
        if (currentSession != null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: starting to track a new session. " +
                    "previous session still active"
            )
        }
        val sessionId = instanceIdSequence.newInstanceId()
        logger.logWithSessionId(event, sessionId)
        currentSession = sessionId
    }

    override fun stopBubbleBar() {
        stop(BUBBLE_BAR_SESSION_ENDED)
    }

    override fun stopFloating() {
        stop(BUBBLE_SESSION_ENDED)
    }

    fun stop(event: Event) {
        val sessionId = currentSession
        if (sessionId == null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: session tracking stopped but current session is null"
            )
            return
        }
        logger.logWithSessionId(event, sessionId)
        currentSession = null
    }
}
