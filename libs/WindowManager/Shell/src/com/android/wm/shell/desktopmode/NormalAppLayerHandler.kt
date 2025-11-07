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
import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOWING_LAYER
import com.android.wm.shell.transition.Transitions

/** A class responsible for handling [WINDOWING_LAYER_NORMAL_APP] requests. */
class NormalAppLayerHandler(
    private val normalAppLayerController: NormalAppLayerController,
    private val userRepositories: DesktopUserRepositories,
) : Transitions.TransitionHandler {

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val triggerTask = request.triggerTask ?: return null
        val windowingLayerChange = request.windowingLayerChange ?: return null

        // TODO(b/449681882): It can be TO_FRONT or OPEN.
        if (!windowingLayerChange.isNormalLayerRequest(triggerTask)) {
            return null
        }

        return normalAppLayerController.moveTaskToNormalLayer(
            transition,
            triggerTask,
            windowingLayerChange.remoteCallback,
        )
    }

    private fun WindowingLayerChange?.isNormalLayerRequest(task: TaskInfo): Boolean {
        val desktopRepository = userRepositories.getProfile(task.userId)
        return this != null &&
            !desktopRepository.isActiveTask(task.taskId) &&
            windowingLayer == WINDOWING_LAYER_NORMAL_APP
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean = false

    internal companion object {
        private const val TAG = "NormalAppLayer"

        @JvmStatic
        internal fun logV(message: String, vararg args: Any?) {
            ProtoLog.v(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
        }

        @JvmStatic
        internal fun logW(message: String, vararg args: Any?) {
            ProtoLog.w(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
        }
    }
}
