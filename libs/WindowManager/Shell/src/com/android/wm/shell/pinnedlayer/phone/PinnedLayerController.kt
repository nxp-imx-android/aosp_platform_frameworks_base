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

package com.android.wm.shell.pinnedlayer.phone

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.app.TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.util.Slog
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A pinned layer [Transitions.TransitionHandler] that handles [Transitions] and can start new
 * [Transitions] on the Shell request. It's responsible for managing PINNED layer that has PiP
 * policies like single window and always-on-top.
 */
class PinnedLayerController(shellInit: ShellInit, private val transitions: Transitions) :
    Transitions.TransitionHandler {

    private var pinnedTask: TaskInfo? = null
    private var resultCallback: IRemoteCallback? = null
    private var ongoingTransition: IBinder? = null

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val windowingLayerChange = request.windowingLayerChange ?: return null
        val triggerTask = request.triggerTask ?: return null

        if (
            isNotPinned(triggerTask.taskId) &&
                windowingLayerChange.windowingLayer != WINDOWING_LAYER_PINNED
        ) {
            return null
        }

        // TODO(b/444435367): Do nothing for already visible pinned task.

        if (windowingLayerChange.windowingLayer == WINDOWING_LAYER_PINNED) {
            // TODO(b/444434453): Repin trigger, unpin pinned.
            ongoingTransition = transition
            resultCallback = windowingLayerChange.remoteCallback
            pinnedTask = triggerTask
            return getLayerPinnedWct(triggerTask.token)
        }

        // TODO(b/444434453): Unpin actively visible task.

        return null
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (ongoingTransition != transition) return false
        dispatchLayerResultWithCleanup(RESULT_APPROVED)

        // This handler doesn't animate anything.
        return false
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        if (!aborted) return
        dispatchLayerResultWithCleanup(RESULT_FAILED_BAD_STATE)
    }

    private fun dispatchLayerResultWithCleanup(result: Int) {
        sendWindowingLayerResult(
            result,
            requireNotNull(resultCallback) {
                "Results callback is null during ongoing transition. Did you forget to save a callback?"
            },
        )
        cleanOngoingTransition()
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

    private fun cleanOngoingTransition() {
        ongoingTransition = null
        resultCallback = null
    }

    /**
     * Checks whether a task with [taskId] is pinned.
     *
     * @param taskId an id of task to check for pinned state.
     * @return `true` when the task is pinned, `false` otherwise.
     */
    fun isPinned(taskId: Int): Boolean = taskId == pinnedTask?.taskId

    companion object {
        private const val TAG = "PinnedLayerController"

        /**
         * Populates a [WindowContainerTransaction] with pinning operations.
         *
         * @param windowContainerToken a token that represents a window on which hierarchy
         *   operations should be applied.
         * @param bounds a [Rect] that represents bounds which a window should have after pinning.
         * @return [WindowContainerTransaction] that encapsulates enter hierarchy operations.
         */
        @JvmStatic
        fun getLayerPinnedWct(
            windowContainerToken: WindowContainerToken,
            bounds: Rect? = null,
        ): WindowContainerTransaction {
            return WindowContainerTransaction()
                .reparent(windowContainerToken, null, true)
                .apply { if (bounds != null) setBounds(windowContainerToken, bounds) }
                .setAlwaysOnTop(windowContainerToken, true)
                .setDisablePip(windowContainerToken, true)
        }
    }
}
