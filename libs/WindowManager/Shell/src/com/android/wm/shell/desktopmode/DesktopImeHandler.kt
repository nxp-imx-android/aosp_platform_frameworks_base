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

import android.animation.Animator
import android.animation.AnimatorSet
import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayImeController.ImePositionProcessor.IME_ANIMATION_DEFAULT
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.shared.animation.WindowAnimator
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions

/** Handles the interactions between IME and desktop tasks */
class DesktopImeHandler(
    private val tasksController: DesktopTasksController,
    private val userRepositories: DesktopUserRepositories,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val displayImeController: DisplayImeController,
    private val displayController: DisplayController,
    private val transitions: Transitions,
    private val mainExecutor: ShellExecutor,
    private val animExecutor: ShellExecutor,
    private val context: Context,
    shellInit: ShellInit,
) : DisplayImeController.ImePositionProcessor, Transitions.TransitionHandler {

    init {
        shellInit.addInitCallback(::onInit, this)
    }

    private fun onInit() {
        if (Flags.enableDesktopImeBugfix()) {
            displayImeController.addPositionProcessor(this)
        }
    }

    var topTask: ActivityManager.RunningTaskInfo? = null
    var previousBounds: Rect? = null

    override fun onImeStartPositioning(
        displayId: Int,
        hiddenTop: Int,
        shownTop: Int,
        showing: Boolean,
        isFloating: Boolean,
        t: Transaction?,
    ): Int {
        if (!tasksController.isAnyDeskActive(displayId) || isFloating) {
            return IME_ANIMATION_DEFAULT
        }

        if (showing) {
            // Only get the top task when the IME will be showing. Otherwise just restore
            // previously manipulated task.
            val currentTopTask =
                if (Flags.enableDisplayFocusInShellTransitions()) {
                    shellTaskOrganizer.getRunningTaskInfo(
                        focusTransitionObserver.globallyFocusedTaskId
                    )
                } else {
                    shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo ->
                        taskInfo.isFocused
                    }
                } ?: return IME_ANIMATION_DEFAULT
            if (!userRepositories.current.isActiveTask(currentTopTask.taskId))
                return IME_ANIMATION_DEFAULT

            topTask = currentTopTask
            val taskBounds =
                currentTopTask.configuration.windowConfiguration?.bounds
                    ?: return IME_ANIMATION_DEFAULT
            val token = currentTopTask.token

            // Save the previous bounds to restore after IME disappears
            previousBounds = Rect(taskBounds)
            val taskHeight = taskBounds.height()
            val stableBounds = Rect()
            val displayLayout =
                displayController.getDisplayLayout(displayId)
                    ?: error("Expected non-null display layout for displayId")
            displayLayout.getStableBounds(stableBounds)
            var finalBottom = 0
            var finalTop = 0
            // If the IME will be covering some part of the task, we need to move the task.
            if (taskBounds.bottom > shownTop) {
                if ((shownTop - stableBounds.top) > taskHeight) {
                    // If the distance between the IME and the top of stable bounds is greater
                    // than the height of the task, keep the task right on top of IME.
                    finalBottom = shownTop
                    finalTop = shownTop - taskHeight
                } else {
                    // Else just move the task up to the top of stable bounds.
                    finalTop = stableBounds.top
                    finalBottom = stableBounds.top + taskHeight
                }
            }

            val finalBounds = Rect(taskBounds.left, finalTop, taskBounds.right, finalBottom)

            logD("Moving task %d due to IME", currentTopTask.taskId)
            val wct = WindowContainerTransaction().setBounds(token, finalBounds)
            transitions.startTransition(TRANSIT_CHANGE, wct, this)
        } else {

            // Restore the previous bounds if they exist
            val finalBounds = previousBounds ?: return IME_ANIMATION_DEFAULT
            val previousTask = topTask ?: return IME_ANIMATION_DEFAULT

            logD("Restoring bounds of task %d due to IME", previousTask.taskId)
            val wct = WindowContainerTransaction().setBounds(previousTask.token, finalBounds)
            transitions.startTransition(TRANSIT_CHANGE, wct, this)
        }
        return IME_ANIMATION_DEFAULT
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val animations = mutableListOf<Animator>()
        val onAnimFinish: (Animator) -> Unit = { animator ->
            mainExecutor.execute {
                // Animation completed
                animations.remove(animator)
                if (animations.isEmpty()) {
                    // All animations completed, finish the transition
                    finishCallback.onTransitionFinished(/* wct= */ null)
                }
            }
        }

        val checkChangeMode = { change: TransitionInfo.Change -> change.mode == TRANSIT_CHANGE }
        animations +=
            info.changes
                .filter {
                    checkChangeMode(it) &&
                        it.taskInfo?.taskId?.let { taskId ->
                            userRepositories.current.isActiveTask(taskId)
                        } == true
                }
                .mapNotNull { createAnimation(it, finishTransaction, onAnimFinish) }
        if (animations.isEmpty()) return false
        animExecutor.execute { animations.forEach(Animator::start) }
        return true
    }

    private fun createAnimation(
        change: TransitionInfo.Change,
        finishTransaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator? {
        val t = Transaction()
        val sc = change.leash
        finishTransaction.show(sc)
        val displayContext =
            change.taskInfo?.let { displayController.getDisplayContext(it.displayId) }
        if (displayContext == null) return null

        val boundsAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics = context.resources.displayMetrics,
                boundsAnimDef = boundsChangeAnimatorDef,
                change = change,
                transaction = t,
            )

        val listener =
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}

                override fun onAnimationCancel(animator: Animator) {}

                override fun onAnimationRepeat(animator: Animator) = Unit

                override fun onAnimationEnd(animator: Animator) {
                    onAnimFinish(animator)
                }
            }
        return AnimatorSet().apply {
            play(boundsAnimator)
            addListener(listener)
        }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesktopImeHandler"

        private val boundsChangeAnimatorDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = RESIZE_DURATION_MS,
                interpolator = Interpolators.STANDARD_ACCELERATE,
            )
        private const val RESIZE_DURATION_MS = 300L
    }
}
