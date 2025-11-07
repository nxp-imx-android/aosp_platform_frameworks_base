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

import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.NormalAppLayerHandler.Companion.logV
import com.android.wm.shell.desktopmode.NormalAppLayerHandler.Companion.logW
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.LAYER_SWITCH

/**
 * A class responsible for managing
 * [android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP].
 *
 * Normal layer is a layer where apps' windows are launched by default and it really depends in
 * which mode the display is in, e.g. when display is in desktop windowing mode than normal layer is
 * a [com.android.wm.shell.desktopmode.data.Desk] or when the display is touch-first - normal layer
 * is a TDA that contains fullscreen tasks.
 */
class NormalAppLayerController(
    private val userRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController?,
) {

    /**
     * Schedules a given task to be moved to a normal app layer.
     *
     * It's expected that a caller uses this method in scope of the existing transition, preferably
     * in scope of [com.android.wm.shell.transition.Transitions.TransitionHandler.handleRequest].
     *
     * @param transition a transition token in which scope moving should be done.
     * @param taskInfo a target task to be moved to the normal layer.
     * @param callback a callback to notify when the task has been moved to normal layer.
     * @return [WindowContainerTransaction] that contains hierarchy operations to set normal layer
     *   properties on the given task. Non-null, but can be empty in case the operation is not
     *   supported.
     * @see DesktopTasksController
     */
    fun moveTaskToNormalLayer(
        transition: IBinder,
        taskInfo: RunningTaskInfo,
        callback: IRemoteCallback? = null,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        val desktopRepository = userRepositories.getProfile(taskInfo.userId)

        // TODO(b/449681882): Add isPinned and a log for pinned + DW as unsupported state.
        if (desktopTasksController != null) {
            val displayId = taskInfo.displayId
            val deskId = desktopRepository.getActiveDeskId(displayId)
            if (deskId != null) {
                logV("Normal layer is an active desk=%s", deskId)
                desktopTasksController.moveTaskToDesk(
                    deskId = deskId,
                    taskId = taskInfo.taskId,
                    userId = taskInfo.userId,
                    wct = wct,
                    transitionSource = LAYER_SWITCH,
                    targetTransition = transition,
                )
            } else {
                logV(
                    "Couldn't find an active desk on displayId=%s for the normal layer " +
                        "request. Trying to move to the default desk as a fallback.",
                    displayId,
                )
                desktopTasksController.moveTaskToDefaultDeskAndActivate(
                    taskId = taskInfo.taskId,
                    wct = wct,
                    transitionSource = LAYER_SWITCH,
                    targetTransition = transition,
                )
            }
        }

        // TODO(b/449681882): Save windowing layer request callback to notify about the result.
        // TODO(b/449681882): Check isNotPinned (== isNormalLayer) and approve
        if (callback != null) {
            sendWindowingLayerResult(RESULT_APPROVED, callback)
        }

        return wct
    }

    private fun sendWindowingLayerResult(result: Int, callback: IRemoteCallback) {
        val bundle = Bundle()
        bundle.putInt(REMOTE_CALLBACK_RESULT_KEY, result)
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            logW("Failed to invoke callback", e)
        }
    }
}
