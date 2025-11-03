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

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.util.Slog
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerTransaction
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.LAYER_SWITCH
import com.android.wm.shell.transition.Transitions

/** A class responsible for handling [WINDOWING_LAYER_NORMAL_APP] requests. */
class NormalAppLayerHandler(
    private val userRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController,
) : Transitions.TransitionHandler {

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val triggerTask = request.triggerTask ?: return null
        val windowingLayerChange = request.windowingLayerChange ?: return null

        val wct = WindowContainerTransaction()
        val desktopRepository = userRepositories.getProfile(triggerTask.userId)

        // TODO(b/449681882): It can be TO_FRONT or OPEN.
        // TODO(b/449681882): Add isPinned and a log for pinned + DW as unsupported state.
        if (!windowingLayerChange.isNormalLayerRequest(triggerTask)) {
            return null
        }

        // This handler is only valid for desktop windowing mode, therefore, it's safe to assume
        // it can be placed in a desk.
        val displayId = triggerTask.displayId
        val deskId = desktopRepository.getActiveDeskId(displayId)
        if (deskId != null) {
            desktopTasksController.moveTaskToDesk(
                deskId = deskId,
                taskId = triggerTask.taskId,
                userId = triggerTask.userId,
                wct = wct,
                transitionSource = LAYER_SWITCH,
                targetTransition = transition,
            )
        } else {
            desktopTasksController.moveTaskToDefaultDeskAndActivate(
                taskId = triggerTask.taskId,
                wct = wct,
                transitionSource = LAYER_SWITCH,
                targetTransition = transition,
            )
        }

        // TODO(b/449681882): Check display supports desktop windowing and reject if it's not.
        // TODO(b/449681882): DTC can be null, reject the request.
        // TODO(b/449681882): Save windowing layer request callback to notify about the result.
        sendWindowingLayerResult(RESULT_APPROVED, windowingLayerChange.remoteCallback)

        return wct
    }

    private fun WindowingLayerChange?.isNormalLayerRequest(task: TaskInfo): Boolean {
        val desktopRepository = userRepositories.getProfile(task.userId)
        return this != null &&
            !desktopRepository.isActiveTask(task.taskId) &&
            windowingLayer == WINDOWING_LAYER_NORMAL_APP
    }

    private fun sendWindowingLayerResult(result: Int, callback: IRemoteCallback) {
        val bundle = Bundle()
        bundle.putInt(REMOTE_CALLBACK_RESULT_KEY, result)
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            Slog.w(TAG, "Failed to invoke callback", e)
        }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean = false

    private companion object {
        private const val TAG = "NormalAppLayerHandler"
    }
}
