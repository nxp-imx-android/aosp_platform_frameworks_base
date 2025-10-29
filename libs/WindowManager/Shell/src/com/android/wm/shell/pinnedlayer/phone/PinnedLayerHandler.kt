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
import android.app.ActivityManager.AppTask.WINDOWING_LAYER_UNDEFINED
import android.app.TaskInfo
import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.NormalAppLayerHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController.UnpinStrategy
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logV
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A [WINDOWING_LAYER_PINNED] [Transitions.TransitionHandler] that handles incoming transition
 * requests.
 */
class PinnedLayerHandler(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val normalAppLayerHandler: NormalAppLayerHandler,
    private val pinnedLayerController: PinnedLayerController,
) : Transitions.TransitionHandler {

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

        if (
            pinnedLayerController.isNotPinned(triggerTask.taskId) &&
                !windowingLayerChange.isLayerPinningRequest()
        ) {
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
            wct.merge(
                pinnedLayerController.pinTask(
                    transition,
                    triggerTask,
                    windowingLayerChange?.remoteCallback,
                ),
                true,
            )
        }

        val isClosePinnedRequest = request.isClosingPinnedRequest()
        val isSwitchingToAnotherLayer =
            request.windowingLayerChange.isSwitchingToAnotherLayerRequest()
        logV(
            "Creating an unpinning transition: isClosePinnedRequest=%b, " +
                "isSwitchingToAnotherLayer=%b",
            isClosePinnedRequest,
            isSwitchingToAnotherLayer,
        )
        if (isClosePinnedRequest || isSwitchingToAnotherLayer) {
            val targetUnpinType = resolveTargetUnpinType(request)
            wct.merge(
                pinnedLayerController.unpinTask(transition, triggerTask, targetUnpinType),
                true,
            )
        }

        // TODO(b/449681882): not quite what we need, normal layer should be self sufficient.
        normalAppLayerHandler.handleRequest(transition, request)?.let { normalLayerWct ->
            wct.merge(normalLayerWct, /* transfer= */ true)
        }

        return wct
    }

    private fun resolveTargetUnpinType(request: TransitionRequestInfo): UnpinStrategy {
        val isClosePinnedRequest = request.isClosingPinnedRequest()
        val isSwitchingToAnotherLayer =
            request.windowingLayerChange.isSwitchingToAnotherLayerRequest()
        return when {
            isClosePinnedRequest -> UnpinStrategy.CLOSE
            isSwitchingToAnotherLayer -> UnpinStrategy.CLEAN
            else ->
                UnpinStrategy.CLOSE.also {
                    logW(
                        "Defaulting to the unpin type=%s. Check that your unpin " +
                            "condition is added to the heuristics.",
                        it,
                    )
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
        val transitions = pinnedLayerController.getActiveTransitions(transition)
        var hasFinishedTransition = false

        // Should accept all the TransitionInfo.Change if there's an unpin transition.
        // Required to properly handle animation for mixed transitions with pip.
        // TODO(b/449681882): Do not rely on transitions, introduce separate animations state.
        if (transitions.any { it is ActiveTransition.Unpin }) {
            finishCallback.onTransitionFinished(null)
            hasFinishedTransition = true
        }

        pinnedLayerController.cleanup(transition)

        return hasFinishedTransition
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        pinnedLayerController.cleanup(transition)
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
        pinnedLayerController.currentPinnedTask?.let {
            outWct.merge(
                pinnedLayerController.unpinTask(transition, it, UnpinStrategy.CLOSE),
                /* transfer= */ true,
            )
        }
    }

    /** @return `true` if there is a task that is currently pinned and visible. */
    fun hasActivePinnedTask(): Boolean {
        return pinnedLayerController.currentPinnedTask != null
    }

    /**
     * Checks whether controller is observing a transition. This is used to determine whether
     * controller is expecting to receive a [startAnimation] call for a given transition.
     *
     * @return `true` if the transition is active within the controller, `false` otherwise.
     */
    fun observes(transition: IBinder): Boolean {
        return pinnedLayerController.getActiveTransitions(transition).isNotEmpty()
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
        val activeTransitions = pinnedLayerController.getActiveTransitions(transition)
        return activeTransitions.any { it.taskInfo.taskId == taskId }
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
        return task != null &&
            pinnedLayerController.isPinned(task.taskId) &&
            TransitionUtil.isOpeningType(type)
    }

    private fun TransitionRequestInfo.isClosingPinnedRequest(): Boolean {
        val task = triggerTask
        return task != null &&
            pinnedLayerController.isPinned(task.taskId) &&
            TransitionUtil.isClosingType(type)
    }
}
