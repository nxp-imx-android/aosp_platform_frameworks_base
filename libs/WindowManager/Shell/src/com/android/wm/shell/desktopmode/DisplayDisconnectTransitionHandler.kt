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

import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.Optional

/**
 * Handler to animate the transition from disconnecting a display.
 *
 * TODO: b/391652399 Consider moving this out of desktop package as it becomes less
 *   desktop-specific.
 */
class DisplayDisconnectTransitionHandler(
    val transitions: Transitions,
    shellInit: ShellInit,
    private val desktopTasksController: Optional<DesktopTasksController>,
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
) : Transitions.TransitionHandler {

    private val pendingTransitions = mutableSetOf<IBinder>()

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    fun addPendingTransition(transition: IBinder) = pendingTransitions.add(transition)

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (!pendingTransitions.contains(transition)) return false
        // TODO: b/391652399 - Animate transitions
        startTransaction.apply()
        finishCallback.onTransitionFinished(null)
        pendingTransitions.remove(transition)
        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val displayChange = request.displayChange
        if (
            DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue &&
                displayChange != null
        ) {
            var reparentDisplay = displayChange.disconnectReparentDisplay
            if (reparentDisplay == INVALID_DISPLAY) {
                val display = displayController.getDisplay(displayChange.displayId)
                // If the display is connected but can't host tasks, we should still handle this
                // as a disconnect transition, so determine the reparentDisplay here.
                if (display != null && !display.canHostTasks()) {
                    reparentDisplay =
                        rootTaskDisplayAreaOrganizer.defaultDisplayArea?.displayId
                            ?: DEFAULT_DISPLAY
                }
            }
            if (reparentDisplay == INVALID_DISPLAY) return null
            // This is a disconnect transition; flag it so we handle animation here later.
            // We need to do this here to prevent default handler from attempting to animate
            // disconnect transitions as this potentially crashes.
            addPendingTransition(transition)
            if (desktopTasksController.isPresent) {
                return desktopTasksController
                    .get()
                    .onDisplayDisconnect(displayChange.displayId, reparentDisplay, transition)
            }
        }
        // Return null since another handler may want to make specific task changes.
        return null
    }
}
