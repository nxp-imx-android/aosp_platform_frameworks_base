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
import android.window.TransitionRequestInfo.WindowingLayerChange
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

    // Stores ids of pinned TaskInfo.
    private val pinnedTasks = mutableSetOf<Int>()
    private var pinnedTask: TaskInfo? = null

    // Stores pin layer transitions that are in progress.
    private val activeTransitions = mutableMapOf<IBinder, MutableSet<ActiveTransition>>()

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
        val triggerTask = request.triggerTask ?: return null
        val windowingLayerChange = request.windowingLayerChange

        if (isNotPinned(triggerTask.taskId) && !windowingLayerChange.isLayerPinningRequest()) {
            return null
        }

        // TODO(b/444435367): Do nothing for already visible pinned task.
        val wct = WindowContainerTransaction()
        if (windowingLayerChange.isLayerPinningRequest()) {
            checkNotNull(windowingLayerChange) {
                "WindowingLayerChange can't be null on the confirmed pin request."
            }

            val transitions = mutableSetOf<ActiveTransition>()
            activeTransitions[transition] = transitions

            wct.merge(getLayerPinnedWct(triggerTask.token), /* transfer= */ true)
            transitions += ActiveTransition.Pin(triggerTask, windowingLayerChange.remoteCallback)
        }

        // The task is pinned, but this is not a direct PINNED layer request. It might be
        // TRANSIT_TO_FRONT or TRANSIT_TO_BACK.
        // TODO(b/444434453): Handle TRANSIT_TO_FRONT, TRANSIT_OPEN

        // Unpinning can occur as a side-effect of another action, check if a task (not necessary
        // the pinned one) is eligible to be unpinned.
        if (containsActivePinningTransition(transition)) {
            // TODO(b/444434453): handle TRANSIT_TO_BACK, TRANSIT_CLOSE
            pinnedTask?.let { pinned ->
                val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
                transitions += ActiveTransition.Unpin(pinned)
                wct.merge(getLayerUnpinnedWct(pinned.token), true)
            }
        }

        return wct.takeUnless { it.isEmpty }
    }

    private fun WindowingLayerChange?.isLayerPinningRequest(): Boolean {
        return this != null && windowingLayer == WINDOWING_LAYER_PINNED
    }

    private fun containsActivePinningTransition(transition: IBinder): Boolean {
        return activeTransitions
            .getOrElse(transition) { emptySet() }
            .any { it is ActiveTransition.Pin }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val transitions = activeTransitions.remove(transition) ?: return false
        transitions.forEach { transition ->
            when (transition) {
                is ActiveTransition.Pin -> {
                    pin(transition.taskInfo)

                    // Only send result if this transition caused by API request.
                    if (transition.resultCallback != null) {
                        sendWindowingLayerResult(RESULT_APPROVED, transition.resultCallback)
                    }
                }
                is ActiveTransition.Unpin -> {
                    unpin(transition.taskInfo, rememberAsPinned = true)
                }
            }
        }

        // This handler doesn't animate anything.
        return false
    }

    private fun pin(taskInfo: TaskInfo) {
        pinnedTasks += taskInfo.taskId
        pinnedTask = taskInfo
    }

    private fun unpin(taskInfo: TaskInfo, rememberAsPinned: Boolean = false) {
        if (!rememberAsPinned) {
            pinnedTasks -= taskInfo.taskId
        }

        if (pinnedTask?.taskId == taskInfo.taskId) {
            pinnedTask = null
        }
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        if (!aborted) return
        val transitions = activeTransitions.remove(transition) ?: return
        transitions.forEach { transition ->
            if (transition is ActiveTransition.Pin && transition.resultCallback != null) {
                sendWindowingLayerResult(RESULT_FAILED_BAD_STATE, transition.resultCallback)
            }
        }
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

    /**
     * Checks whether a task with [taskId] is pinned.
     *
     * @param taskId an id of task to check for pinned state.
     * @return `true` when the task is pinned, `false` otherwise.
     */
    fun isPinned(taskId: Int): Boolean = pinnedTasks.contains(taskId)

    private sealed class ActiveTransition {

        /**
         * A transition that represents pin operation on a specific task. Pinning can happen via
         * direct [ActivityManager.AppTask.requestWindowingLayer] call or when the window becomes
         * visible again.
         *
         * @property taskInfo a [TaskInfo] that current transition going to pin.
         * @property resultCallback a [IRemoteCallback] that should be invoked to notify the client
         *   about pin request result. Can be `null` during re-pining.
         * @see WINDOWING_LAYER_PINNED
         * @see [ActivityManager.AppTask.requestWindowingLayer]
         */
        data class Pin(val taskInfo: TaskInfo, val resultCallback: IRemoteCallback?) :
            ActiveTransition()

        /**
         * A transition that represents unpin operation on a specific task. Unpinning can happen
         * while switching to another layer or a window is simply closed or minimized.
         *
         * @property taskInfo a [TaskInfo] that current transition going to pin.
         */
        data class Unpin(val taskInfo: TaskInfo) : ActiveTransition()
    }

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

        /**
         * Populates a [WindowContainerTransaction] that unpins a window from display. Minimizes a
         * [windowContainerToken] by default.
         *
         * @param windowContainerToken a window token that unpinning operation targets.
         * @param isMinimizing whether a window should be minimized or closed.
         * @return [WindowContainerTransaction] that holds unpinning hierarchy operations.
         */
        @JvmStatic
        @JvmOverloads
        fun getLayerUnpinnedWct(
            windowContainerToken: WindowContainerToken,
            isMinimizing: Boolean = true,
        ): WindowContainerTransaction {
            return WindowContainerTransaction()
                .setAlwaysOnTop(windowContainerToken, /* alwaysOnTop= */ false)
                .apply {
                    if (isMinimizing) {
                        reorder(windowContainerToken, /* onTop= */ false)
                    } else {
                        removeTask(windowContainerToken)
                    }
                }
        }
    }
}
