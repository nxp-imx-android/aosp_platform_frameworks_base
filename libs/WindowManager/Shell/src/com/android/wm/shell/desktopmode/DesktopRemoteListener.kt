/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import javax.inject.Inject

/** Represents a remote-listener forwarding calls for [IDesktopTaskListener]. */
@WMSingleton
class DesktopRemoteListener @Inject constructor() {

    private var remoteListener:
        SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>? =
        null

    /**
     * Registers the remote listener to receive desktop mode transition callbacks.
     *
     * @param remoteDesktopTaskListener The listener wrapper that handles the IPC communication.
     */
    fun register(
        remoteDesktopTaskListener:
            SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>
    ) {
        remoteListener = remoteDesktopTaskListener
    }

    /** Unregisters the current remote listener and stops further callbacks. */
    fun unregister() {
        remoteListener = null
    }

    /**
     * Notifies the registered listener that a transition to enter desktop mode has started.
     *
     * @param transitionDuration The duration of the transition animation in milliseconds.
     */
    fun onEnterDesktopModeTransitionStarted(transitionDuration: Int) {
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "$TAG: onEnterDesktopModeTransitionStarted transitionTime=%d remoteListener running=%b",
            transitionDuration,
            remoteListener != null,
        )
        remoteListener?.call { l -> l.onEnterDesktopModeTransitionStarted(transitionDuration) }
    }

    /**
     * Notifies the registered listener that a transition to exit desktop mode has started.
     *
     * @param transitionDuration The duration of the transition animation in milliseconds.
     * @param shouldEndUpAtHome {@code true} if the transition should finalize showing the
     *   Home/Launcher screen; {@code false} if it returns to a specific application task.
     */
    fun onExitDesktopModeTransitionStarted(transitionDuration: Int, shouldEndUpAtHome: Boolean) {
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "$TAG: onExitDesktopModeTransitionStarted " +
                "transitionTime=%d shouldEndUpAtHome=%b  remoteListener running=%b",
            transitionDuration,
            shouldEndUpAtHome,
            remoteListener != null,
        )
        remoteListener?.call { l ->
            l.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
        }
    }

    private companion object {
        private val TAG = "DesktopRemoteListener"
    }
}
