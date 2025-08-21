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
class BubbleSessionTrackerImpl @Inject constructor(
    @Bubbles private val instanceIdSequence: InstanceIdSequence
) : BubbleSessionTracker {

    private var currentSession: InstanceId? = null

    override fun start() {
        if (currentSession != null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: starting to track a new session. "
                        + "previous session still active"
            )
        }
        currentSession = instanceIdSequence.newInstanceId()
    }

    override fun stop() {
        if (currentSession == null) {
            ProtoLog.d(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: session tracking stopped but current session is null"
            )
        }
        currentSession = null
    }
}
