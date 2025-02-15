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
package com.android.wm.shell.desktopmode.multidesks

import android.os.IBinder
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/**
 * Observer of desk-related transitions, such as adding, removing or activating a whole desk. It
 * tracks pending transitions and updates repository state once they finish.
 */
class DesksTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
) {
    private val deskTransitions = mutableMapOf<IBinder, DeskTransition>()

    /** Adds a pending desk transition to be tracked. */
    fun addPendingTransition(transition: DeskTransition) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        deskTransitions[transition.token] = transition
    }

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val deskTransition = deskTransitions.remove(transition) ?: return
        logD("Desk transition ready: %s", deskTransition)
        val desktopRepository = desktopUserRepositories.current
        when (deskTransition) {
            is DeskTransition.RemoveDesk -> {
                check(info.type == TRANSIT_CLOSE) { "Expected close transition for desk removal" }
                // TODO: b/362720497 - consider verifying the desk was actually removed through the
                //  DesksOrganizer. The transition info won't have changes if the desk was not
                //  visible, such as when dismissing from Overview.
                val deskId = deskTransition.deskId
                val displayId = deskTransition.displayId
                desktopRepository.removeDesk(deskTransition.deskId)
                deskTransition.onDeskRemovedListener?.onDeskRemoved(displayId, deskId)
            }
            is DeskTransition.ActivateDesk -> {
                val activeDeskChange =
                    info.changes.find { change ->
                        desksOrganizer.isDeskActiveAtEnd(change, deskTransition.deskId)
                    }
                activeDeskChange?.let {
                    desktopRepository.setActiveDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                    )
                }
            }
            is DeskTransition.ActiveDeskWithTask -> {
                val withTask =
                    info.changes.find { change ->
                        change.taskInfo?.taskId == deskTransition.enterTaskId &&
                            change.taskInfo?.isVisibleRequested == true &&
                            desksOrganizer.getDeskAtEnd(change) == deskTransition.deskId
                    }
                withTask?.let {
                    desktopRepository.setActiveDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                    )
                    desktopRepository.addTaskToDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                        taskId = deskTransition.enterTaskId,
                        isVisible = true,
                    )
                }
            }
            is DeskTransition.DeactivateDesk -> {
                var visibleDeactivation = false
                for (change in info.changes) {
                    val isDeskChange = desksOrganizer.isDeskChange(change, deskTransition.deskId)
                    if (isDeskChange) {
                        visibleDeactivation = true
                        continue
                    }
                    val taskId = change.taskInfo?.taskId ?: continue
                    val removedFromDesk =
                        desktopRepository.getDeskIdForTask(taskId) == deskTransition.deskId &&
                            desksOrganizer.getDeskAtEnd(change) == null
                    if (removedFromDesk) {
                        desktopRepository.removeTaskFromDesk(
                            deskId = deskTransition.deskId,
                            taskId = taskId,
                        )
                    }
                }
                // Always deactivate even if there's no change that confirms the desk was
                // deactivated. Some interactions, such as the desk deactivating because it's
                // occluded by a fullscreen task result in a transition change, but others, such
                // as transitioning from an empty desk to home may not.
                if (!visibleDeactivation) {
                    logD("Deactivating desk without transition change")
                }
                desktopRepository.setDeskInactive(deskId = deskTransition.deskId)
            }
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesksTransitionObserver"
    }
}
