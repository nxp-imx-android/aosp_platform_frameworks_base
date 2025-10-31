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

import android.app.ActivityManager
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_UNDEFINED
import android.app.TaskInfo
import android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY
import android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.NormalAppLayerHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController.Companion.getLayerPinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController.Companion.getLayerUnpinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logV
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A pinned layer [Transitions.TransitionHandler] that handles [Transitions] and can start new
 * [Transitions] on the Shell request. It's responsible for managing PINNED layer that has PiP
 * policies like single window and always-on-top.
 */
class PinnedLayerController(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val normalAppLayerHandler: Lazy<NormalAppLayerHandler>,
) : Transitions.TransitionHandler, Transitions.TransitionObserver {

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
        transitions.registerObserver(this)
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

        val wct = WindowContainerTransaction()
        val isLayerPinningRequest = windowingLayerChange.isLayerPinningRequest()
        val isLayerOpeningPinnedRequest = request.isOpeningPinnedRequest()
        logV(
            "Creating a pin transition: isLayerPinningRequest=%b, isOpeningPinnedRequest=%b",
            isLayerPinningRequest,
            isLayerOpeningPinnedRequest,
        )
        // There's either a pin request or an already pinned task is brought to foreground.
        if (isLayerPinningRequest || isLayerOpeningPinnedRequest) {
            createPinTransition(transition, triggerTask, windowingLayerChange?.remoteCallback, wct)
        }

        // Find a task to unpin after pinning.
        val candidateTaskForUnpin = resolveUnpinningTarget(transition, request)
        if (candidateTaskForUnpin != null) {
            createUnpinTransition(
                transition,
                candidateTaskForUnpin,
                wct,
                isMinimizing = request.type != TRANSIT_CLOSE,
                isSwitchingToAnotherLayer = windowingLayerChange.isSwitchingToAnotherLayerRequest(),
            )
        }

        // TODO(b/449681882): not quite what we need, normal layer should be self sufficient.
        normalAppLayerHandler.value.handleRequest(transition, request)?.let { normalLayerWct ->
            wct.merge(normalLayerWct, /* transfer= */ true)
        }

        return wct.takeUnless { activeTransitions[transition].isNullOrEmpty() }
    }

    /**
     * Finds a task to be unpinned in scope of the given [Transition].
     *
     * The task can be unpinned as a side-effect of another action, for example, pinning a new task
     * when there's already a pinned one.
     *
     * @param transition a [Transition] token in which unpinning can happen.
     * @param request a request that contains info about current transition.
     * @return a [TaskInfo] to unpin or `null` if unpinning is not needed or there's nothing to
     *   unpin.
     */
    private fun resolveUnpinningTarget(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): TaskInfo? {
        val hasActivePinningTransition = containsActivePinningTransition(transition)
        val isClosePinnedRequest = request.isClosingPinnedRequest()
        val isSwitchingToAnotherLayer =
            request.windowingLayerChange.isSwitchingToAnotherLayerRequest()
        val isUnpinningNeeded =
            hasActivePinningTransition || isClosePinnedRequest || isSwitchingToAnotherLayer

        logV(
            "Check whether unpinning is needed: isSwitchingToAnotherLayer=%b, " +
                "hasActivePinningTransition=%b, isClosePinnedRequest=%b, isUnpinningNeeded=%b",
            isSwitchingToAnotherLayer,
            hasActivePinningTransition,
            isClosePinnedRequest,
            isSwitchingToAnotherLayer,
        )
        if (!isUnpinningNeeded) {
            logV("Unpinning is not needed for any task, skipping.")
            return null
        }

        val triggerTask = request.triggerTask
        val isTriggerVisiblyPinned =
            triggerTask != null && triggerTask.token == currentPinnedTask?.token
        val candidateTaskForUnpin =
            when {
                hasActivePinningTransition && !isTriggerVisiblyPinned -> currentPinnedTask
                isClosePinnedRequest || isSwitchingToAnotherLayer -> triggerTask
                else -> {
                    logW(
                        "Unpinning is needed, but the task is not found. " +
                            "Do you want to check for a new unpinning condition?"
                    )
                    null
                }
            }

        return candidateTaskForUnpin.also { logV("Found a task=%s for unpinning", it) }
    }

    /**
     * Augments a [WindowContainerTransaction] to dismiss a task if it is pinned.
     *
     * @param transition the transition that may dismiss a pinned task.
     * @param request the transition request.
     * @param outWct the [WindowContainerTransaction] to augment.
     */
    fun augmentRequestDismissPinnedTask(
        transition: IBinder,
        request: TransitionRequestInfo,
        outWct: WindowContainerTransaction,
    ) {
        currentPinnedTask?.let {
            createUnpinTransition(
                transition,
                it,
                outWct,
                isMinimizing = DEFAULT_MINIMIZE_WHEN_UNPINNING,
            )
        }
    }

    /** @return `true` if there is a task that is currently pinned and visible. */
    fun hasActivePinnedTask(): Boolean {
        return currentPinnedTask != null
    }

    /**
     * Checks whether controller is observing a transition. This is used to determine whether
     * controller is expecting to receive a [startAnimation] call for a given transition.
     *
     * @return `true` if the transition is active within the controller, `false` otherwise.
     */
    fun observes(transition: IBinder): Boolean {
        return activeTransitions.containsKey(transition)
    }

    /**
     * Checks whether controller is observing a transition and awaits changes for a particular task
     * in this transition.
     *
     * Used to determine what changes should be propagated to this handler.
     *
     * @return `true` if the transition is active within the controller and contains a task, `false`
     *   otherwise.
     */
    fun awaitsChangesFor(taskInfo: TaskInfo?, transition: IBinder): Boolean {
        val taskId = taskInfo?.taskId ?: return false
        return activeTransitions[transition]?.any { it.taskInfo.taskId == taskId } ?: false
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
        isSwitchingToAnotherLayer: Boolean = false,
    ) {
        val transitions = activeTransitions.getOrPut(transition) { mutableSetOf() }
        transitions += ActiveTransition.Unpin(task)

        val unpinWct =
            if (isSwitchingToAnotherLayer) {
                getRemovedFromLayerWct(task.token)
            } else {
                getLayerUnpinnedWct(task.token, isMinimizing = isMinimizing)
            }
        wct.merge(unpinWct, /* transfer= */ true)
    }

    fun isPinningRequest(request: TransitionRequestInfo): Boolean =
        request.windowingLayerChange?.isLayerPinningRequest() ?: false ||
            request.isOpeningPinnedRequest()

    private fun WindowingLayerChange?.isLayerPinningRequest(): Boolean {
        return this != null && windowingLayer == WINDOWING_LAYER_PINNED
    }

    private fun WindowingLayerChange?.isSwitchingToAnotherLayerRequest(): Boolean {
        return this != null &&
            !isLayerPinningRequest() &&
            windowingLayer > WINDOWING_LAYER_UNDEFINED
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

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
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

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val transitions = activeTransitions[transition]
        var hasFinishedTransition = false

        if (transitions != null) {
            // Should accept all the TransitionInfo.Change if there's an unpin transition.
            // Required to properly handle animation for mixed transitions with pip.
            if (transitions.any { it is ActiveTransition.Unpin }) {
                finishCallback.onTransitionFinished(null)
                hasFinishedTransition = true
            }
        }

        cleanup(transition)
        return hasFinishedTransition
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        cleanup(transition)
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

    /** Cleans internal states, should be used on terminal transition state. */
    private fun cleanup(transition: IBinder) {
        activeTransitions.remove(transition)
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
     * Closes a pinned task.
     *
     * @param task a [TaskInfo] of the task to close
     * @return `true` if the task closing transition has been started, `false` otherwise
     */
    fun closeTask(task: TaskInfo): Boolean {
        if (isNotPinned(task.taskId)) {
            logW("closeTask: the task=%s is not pinned", task)
            return false
        }

        val wct = WindowContainerTransaction()
        wct.removeTask(task.token)
        transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        return true
    }

    private sealed class ActiveTransition {
        abstract val taskInfo: TaskInfo

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
        data class Pin(override val taskInfo: TaskInfo, val resultCallback: IRemoteCallback?) :
            ActiveTransition()

        /**
         * A transition that represents unpin operation on a specific task. Unpinning can happen
         * while switching to another layer or a window is simply closed or minimized.
         *
         * @property taskInfo a [TaskInfo] that current transition going to pin.
         */
        data class Unpin(override val taskInfo: TaskInfo) : ActiveTransition()
    }

    companion object {
        /**
         * Whether to minimize a pinned task when it is unpinned, e.g. when dismissed by media PiP.
         */
        private const val DEFAULT_MINIMIZE_WHEN_UNPINNING = false

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
                .setWindowingMode(windowContainerToken, WINDOWING_MODE_FREEFORM)
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

        /**
         * Populates a [WindowContainerTransaction] that removes all pin related properties from the
         * target window container. The difference with [getLayerUnpinnedWct] is current method
         * cleans up properties set with [getLayerPinnedWct].
         *
         * @param windowContainerToken a window token that clean operations target.
         * @return [WindowContainerTransaction] that holds clean hierarchy operations.
         * @see [getLayerUnpinnedWct]
         */
        @JvmStatic
        fun getRemovedFromLayerWct(
            windowContainerToken: WindowContainerToken
        ): WindowContainerTransaction {
            return WindowContainerTransaction()
                .setAlwaysOnTop(windowContainerToken, false)
                .setDisablePip(windowContainerToken, false)
        }
    }
}
