/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.desktopmode.homescreenpeeking

import android.animation.ValueAnimator
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.Transitions
import java.util.function.Supplier

/** Class to manage home screen peeking transitions and animations in desktop mode. */
class DesktopHomeScreenPeekTransitionHandler(
    private val mainExecutor: ShellExecutor,
    private val transitions: Transitions,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private val shellController: ShellController,
    private val userRepositories: DesktopUserRepositories,
) : Transitions.TransitionHandler {
    private var onTransitionAnimationFinished: (() -> Unit)? = null

    /**
     * Starts a TRANSIT_CHANGE transition for the provided WindowContainerTransaction, with an
     * optional callback to run when the transition animation is finished.
     */
    fun startTransition(
        wct: WindowContainerTransaction,
        onTransitionAnimationFinishedCallback: (() -> Unit)? = null,
    ) {
        onTransitionAnimationFinished = onTransitionAnimationFinishedCallback
        transitions.startTransition(TRANSIT_CHANGE, wct, this)
    }

    // Setting this as active handler directly in startTransition.
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: startAnimation", TAG)
        val desktopChanges = getDesktopChanges(info)
        if (desktopChanges.isEmpty()) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "%s: Not animating transition, as no desktop TransitionInfo.Change exist.",
                TAG,
            )
            return false
        }
        val transaction = transactionSupplier.get()
        val animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { animation ->
                    val fraction = animation.animatedFraction

                    desktopChanges.forEach { change ->
                        val start = change.startAbsBounds
                        val end = change.endAbsBounds
                        val x = start.left + (end.left - start.left) * fraction
                        transaction.setPosition(change.leash, x, start.top.toFloat())
                    }
                    transaction.apply()
                }
                addListener(
                    onEnd = {
                        onTransitionAnimationFinished?.invoke()
                        onTransitionAnimationFinished = null
                        finishCallback.onTransitionFinished(/* wct= */ null)
                        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: startAnimation finished", TAG)
                    }
                )
            }
        desktopChanges.forEach { change ->
            val start = change.startAbsBounds
            val end = change.endAbsBounds
            startTransaction.setPosition(change.leash, start.left.toFloat(), start.top.toFloat())
            finishTransaction.setPosition(change.leash, end.left.toFloat(), end.top.toFloat())
        }
        startTransaction.apply()
        mainExecutor.execute { animator.start() }
        return true
    }

    private fun getDesktopChanges(info: TransitionInfo): List<TransitionInfo.Change> =
        info.changes.filter { change ->
            val taskId = change.taskInfo?.taskId
            taskId != null &&
                userRepositories.getProfile(shellController.currentUserId).isActiveTask(taskId)
        }

    companion object {
        private const val TAG = "DesktopHomeScreenPeekTransitionHandler"
    }
}
