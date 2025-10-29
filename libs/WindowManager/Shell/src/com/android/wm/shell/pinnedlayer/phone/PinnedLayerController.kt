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
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerPinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerUnpinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getRemovedFromLayerWct
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A controller that is responsible for managing [WINDOWING_LAYER_PINNED] layer that has PiP
 * policies like single window and always-on-top.
 *
 * The controller is the main decider when the task is pinned and it's responsible for dispatching
 * callbacks.
 */
class PinnedLayerController(shellInit: ShellInit, private val transitions: Transitions) :
    Transitions.TransitionObserver {

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
        transitions.registerObserver(this)
    }

    /**
     * Provides transitions that are currently being processed by this controller.
     *
     * @param transition an [IBinder] token for the given transitions.
     * @return a [Set] of [ActiveTransition], never returns `null`.
     */
    fun getActiveTransitions(transition: IBinder): Set<ActiveTransition> =
        activeTransitions.getOrDefault(transition, emptySet())

    /**
     * Checks whether a task with [taskId] is pinned.
     *
     * The task is considered to be pinned when the API call has triggered a transition and it
     * reported to be ready, marking that the Core has applied changes to the task.
     *
     * @param taskId an id of task to check for pinned state.
     * @return `true` when the task is pinned, `false` otherwise.
     * @see Transitions.TransitionObserver.onTransitionReady
     */
    fun isPinned(taskId: Int): Boolean = pinnedTasks.contains(taskId)

    /**
     * Pins a task and adds it to the active transitions for the given transition token.
     *
     * @param transition a transition token.
     * @param task a task to pin.
     * @param remoteCallback a callback to notify about the result of the operation.
     * @return a [WindowContainerTransaction] that contains pinned properties.
     */
    fun pinTask(
        transition: IBinder,
        task: TaskInfo,
        remoteCallback: IRemoteCallback?,
    ): WindowContainerTransaction =
        WindowContainerTransaction().apply {
            val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
            transitions += ActiveTransition.Pin(task, remoteCallback)
            merge(getLayerPinnedWct(task.token), /* transfer= */ true)

            val pinnedTask = currentPinnedTask
            if (pinnedTask != null && pinnedTask.token != task.token) {
                merge(unpinTask(transition, pinnedTask, UnpinStrategy.CLOSE), /* transfer= */ true)
            }
        }

    /**
     * Unpins a specific task and adds it to the active transitions for the given transition token.
     *
     * @param transition a transition token.
     * @param task a task to unpin.
     * @param unpinStrategy a strategy for unpinning.
     * @return a [WindowContainerTransaction] that performs unpinning.
     */
    fun unpinTask(
        transition: IBinder,
        task: TaskInfo,
        unpinStrategy: UnpinStrategy,
    ): WindowContainerTransaction {
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition.Unpin(task)

        return when (unpinStrategy) {
            UnpinStrategy.HIDE,
            UnpinStrategy.CLOSE ->
                getLayerUnpinnedWct(task.token, isMinimizing = unpinStrategy == UnpinStrategy.HIDE)
            UnpinStrategy.CLEAN -> getRemovedFromLayerWct(task.token)
        }
    }

    /**
     * Starts a transition to close a pinned task.
     *
     * @param task a [TaskInfo] of the task to close.
     * @return `true` if the task closing transition has been started, `false` otherwise.
     */
    fun closeTask(task: TaskInfo): Boolean {
        if (isNotPinned(task.taskId)) {
            logW("closeTask: the task in question is not pinned")
            return false
        }

        val wct = WindowContainerTransaction()
        wct.removeTask(task.token)
        transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        return true
    }

    // TODO(b/449681882): Remove when Handler introduces its own state management for animations.
    fun cleanup(transition: IBinder) {
        activeTransitions.remove(transition)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        // TODO(b/449681882): Clean transitions here. Handler should track animation data
        // separately.
        val transitions = activeTransitions[transition] ?: return
        transitions.forEach { transition ->
            when (transition) {
                is ActiveTransition.Pin -> {
                    pin(transition.taskInfo)

                    // Only send result if the transition caused by an API request.
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

    private fun sendWindowingLayerResult(result: Int, callback: IRemoteCallback) {
        val bundle = Bundle()
        bundle.putInt(REMOTE_CALLBACK_RESULT_KEY, result)
        try {
            callback.sendResult(bundle)
        } catch (e: RemoteException) {
            logW("Failed to invoke callback, error=%s", e)
        }
    }

    /** A set of strategies that changes how the task is unpinned. */
    enum class UnpinStrategy {
        /**
         * The task should be unpinned visually and hidden from the display. The controller keeps
         * the task as pinned.
         */
        HIDE,

        /** The task should be unpinned and closed. The task is not kept as pinned. */
        CLOSE,

        /**
         * The task should be unpinned and kept visible on the display. This is useful when another
         * handler wants to decide where to put the task.
         */
        CLEAN,
    }
}
