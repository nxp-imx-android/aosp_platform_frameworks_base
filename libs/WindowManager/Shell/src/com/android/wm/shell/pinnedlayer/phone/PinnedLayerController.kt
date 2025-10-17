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
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.shared.TransitionUtil
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
    var currentPinnedTask: TaskInfo? = null
        private set

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

        // There's either a pin request or an already pinned task is brought to foreground.
        val wct = WindowContainerTransaction()
        if (windowingLayerChange.isLayerPinningRequest() || request.isOpeningPinnedRequest()) {
            createPinTransition(transition, triggerTask, windowingLayerChange?.remoteCallback, wct)
        }

        // Unpinning can occur as a side-effect of another action, check if a task (not necessary
        // the pinned one) is eligible to be unpinned.
        val isUnpinningNeeded =
            containsActivePinningTransition(transition) || request.isClosingPinnedRequest()
        val candidateTaskForUnpin =
            when {
                triggerTask.token != currentPinnedTask?.token -> currentPinnedTask
                request.isClosingPinnedRequest() -> triggerTask
                else -> null
            }
        if (isUnpinningNeeded && candidateTaskForUnpin != null) {
            createUnpinTransition(transition, candidateTaskForUnpin, wct,
                isMinimizing = request.type != TRANSIT_CLOSE)
        }

        return wct.takeUnless { activeTransitions[transition].isNullOrEmpty() }
    }

    /** Pins a task and adds it to the active transitions for the given transition token. */
    private fun createPinTransition(
        transition: IBinder,
        task: TaskInfo,
        remoteCallback: IRemoteCallback?,
        wct: WindowContainerTransaction,
    ) {
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition.Pin(task, remoteCallback)
        wct.merge(getLayerPinnedWct(task.token), /* transfer= */ true)
    }

    /**
     * Unpins a specific task and adds it to the active transitions for the given transition token.
     */
    private fun createUnpinTransition(
        transition: IBinder,
        task: TaskInfo,
        wct: WindowContainerTransaction,
        isMinimizing: Boolean = false,
    ) {
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition.Unpin(task)
        wct.merge(
            getLayerUnpinnedWct(task.token, isMinimizing),
            /* transfer= */ true,
        )
    }

    private fun WindowingLayerChange?.isLayerPinningRequest(): Boolean {
        return this != null && windowingLayer == WINDOWING_LAYER_PINNED
    }

    private fun TransitionRequestInfo.isOpeningPinnedRequest(): Boolean {
        val task = triggerTask
        return task != null && isPinned(task.taskId) && TransitionUtil.isOpeningType(type)
    }

    private fun TransitionRequestInfo.isClosingPinnedRequest(): Boolean {
        val task = triggerTask
        return task != null && isPinned(task.taskId) && TransitionUtil.isClosingType(type)
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
                    val isMovingToBack =
                        info.changes.any {
                            it.taskInfo?.token == transition.taskInfo.token &&
                                it.mode == TRANSIT_TO_BACK
                        }
                    unpin(transition.taskInfo, rememberAsPinned = isMovingToBack)
                }
            }
        }

        // This handler doesn't animate anything.
        return false
    }

    private fun pin(taskInfo: TaskInfo) {
        pinnedTasks += taskInfo.taskId
        currentPinnedTask = taskInfo
    }

    private fun unpin(taskInfo: TaskInfo, rememberAsPinned: Boolean = false) {
        if (!rememberAsPinned) {
            pinnedTasks -= taskInfo.taskId
        }

        if (currentPinnedTask?.taskId == taskInfo.taskId) {
            currentPinnedTask = null
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
                // An already pinned task can re-request to be pinned again, since this that leads
                // to a no-op the transition is aborted. In this case we want to send approved
                // result.
                val result =
                    if (isPinned(transition.taskInfo.taskId)) RESULT_APPROVED
                    else RESULT_FAILED_BAD_STATE
                sendWindowingLayerResult(result, transition.resultCallback)
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
