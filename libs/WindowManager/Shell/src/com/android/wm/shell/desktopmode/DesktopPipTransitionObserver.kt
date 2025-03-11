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
import android.os.IBinder
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/**
 * Observer of PiP in Desktop Mode transitions. At the moment, this is specifically tracking a PiP
 * transition for a task that is entering PiP via the minimize button on the caption bar.
 */
class DesktopPipTransitionObserver {
    private val pendingPipTransitions = mutableMapOf<IBinder, PendingPipTransition>()

    /** Adds a pending PiP transition to be tracked. */
    fun addPendingPipTransition(transition: PendingPipTransition) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue) return
        pendingPipTransitions[transition.token] = transition
    }

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue) return
        val pipTransition = pendingPipTransitions.remove(transition) ?: return

        logD("Desktop PiP transition ready: %s", transition)
        for (change in info.changes) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue
            }

            if (
                taskInfo.taskId == pipTransition.taskId &&
                    taskInfo.windowingMode == WINDOWING_MODE_PINNED
            ) {
                logD("Desktop PiP transition was successful")
                pipTransition.onSuccess()
                return
            }
        }
        logD("Change with PiP task not found in Desktop PiP transition; likely failed")
    }

    /**
     * Data tracked for a pending PiP transition.
     *
     * @property token the PiP transition that is started.
     * @property taskId task id of the task entering PiP.
     * @property onSuccess callback to be invoked if the PiP transition is successful.
     */
    data class PendingPipTransition(val token: IBinder, val taskId: Int, val onSuccess: () -> Unit)

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesktopPipTransitionObserver"
    }
}
