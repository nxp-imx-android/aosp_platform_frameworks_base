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
import android.view.Display.INVALID_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.transition.Transitions

/**
 * Observer of desk-related transitions, such as adding, removing or activating a whole desk. It
 * tracks pending transitions and updates repository state once they finish.
 */
class DesksTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val transitions: Transitions,
) {
    private val deskTransitions = mutableMapOf<IBinder, MutableSet<DeskTransition>>()

    /** Adds a pending desk transition to be tracked. */
    fun addPendingTransition(transition: DeskTransition) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val transitions = deskTransitions[transition.token] ?: mutableSetOf()
        transitions += transition
        deskTransitions[transition.token] = transitions
        logD("Added pending desk transition: %s", transition)
    }

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val deskTransitions = deskTransitions.remove(transition)
        deskTransitions?.forEach { deskTransition -> handleDeskTransition(info, deskTransition) }
        if (deskTransitions == null || deskTransitions.isEmpty()) {
            // A desk transition could also occur without shell having started it or intercepting
            // it, check for that here in case launch roots need to be updated.
            handleIndependentDeskTransitionIfNeeded(info)
        }
    }

    /**
     * Called when a transition is merged with another transition, which may include transitions not
     * tracked by this observer.
     */
    fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val transitions = deskTransitions.remove(merged) ?: return
        deskTransitions[playing] =
            transitions
                .map { deskTransition -> deskTransition.copyWithToken(token = playing) }
                .toMutableSet()
    }

    /**
     * Called when any transition finishes, which may include transitions not tracked by this
     * observer.
     *
     * Most [DeskTransition]s are not handled here because [onTransitionReady] handles them and
     * removes them from the map. However, there can be cases where the transition was added after
     * [onTransitionReady] had already been called and they need to be handled here, such as the
     * swipe-to-home recents transition when there is no book-end transition.
     */
    fun onTransitionFinished(transition: IBinder) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val deskTransitions = deskTransitions.remove(transition) ?: return
        deskTransitions.forEach { deskTransition ->
            if (deskTransition is DeskTransition.DeactivateDesk) {
                handleDeactivateDeskTransition(null, deskTransition)
            } else {
                logW(
                    "Unexpected desk transition finished without being handled: %s",
                    deskTransition,
                )
            }
        }
    }

    private fun handleDeskTransition(info: TransitionInfo, deskTransition: DeskTransition) {
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
                deskTransition.runOnTransitEnd?.invoke()
                desktopRepository.removeDesk(deskTransition.deskId)
                deskTransition.onDeskRemovedListener?.onDeskRemoved(displayId, deskId)
            }
            is DeskTransition.ActivateDesk -> {
                val activateDeskChange =
                    info.changes.find { change ->
                        desksOrganizer.isDeskActiveAtEnd(change, deskTransition.deskId)
                    }
                if (activateDeskChange == null) {
                    // Always activate even if there is no change in the transition for the
                    // activated desk. This is necessary because some activation requests, such as
                    // those involving empty desks, may not contain visibility changes that are
                    // reported in the transition change list.
                    logD("Activating desk without transition change")
                }
                desktopRepository.setActiveDesk(
                    displayId = deskTransition.displayId,
                    deskId = deskTransition.deskId,
                )
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.ActivateDeskWithTask -> {
                val deskId = deskTransition.deskId
                val deskChange =
                    info.changes.find { change -> desksOrganizer.isDeskChange(change, deskId) }
                if (deskChange != null) {
                    val deskChangeDisplayId = deskChange.taskInfo?.displayId ?: INVALID_DISPLAY
                    if (deskChangeDisplayId != deskTransition.displayId) {
                        logW(
                            "ActivateDeskWithTask: expected displayId=%d but got displayId=%d",
                            deskTransition.displayId,
                            deskChangeDisplayId,
                        )
                    }
                } else {
                    logW("ActivateDeskWithTask: did not find desk change")
                }
                val taskChange =
                    info.changes.find { change ->
                        change.taskInfo?.taskId == deskTransition.enterTaskId &&
                            change.taskInfo?.isVisibleRequested == true &&
                            desksOrganizer.getDeskAtEnd(change) == deskTransition.deskId
                    }
                if (taskChange != null) {
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
                } else {
                    logW("ActivateDeskWithTask: did not find task change")
                }
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.DeactivateDesk -> handleDeactivateDeskTransition(info, deskTransition)
            is DeskTransition.ChangeDeskDisplay -> handleChangeDeskDisplay(info, deskTransition)
            is DeskTransition.RemoveDisplay -> handleRemoveDisplay(deskTransition)
        }
    }

    private fun handleDeactivateDeskTransition(
        info: TransitionInfo?,
        deskTransition: DeskTransition.DeactivateDesk,
    ) {
        logD("handleDeactivateDeskTransition: %s", deskTransition)
        val desktopRepository = desktopUserRepositories.current
        var deskChangeFound = false

        deskTransition.runOnTransitEnd?.invoke()
        val changes = info?.changes ?: emptyList()
        for (change in changes) {
            val isDeskChange = desksOrganizer.isDeskChange(change, deskTransition.deskId)
            if (isDeskChange) {
                deskChangeFound = true
                continue
            }
        }
        // Always deactivate even if there's no change that confirms the desk was
        // deactivated. Some interactions, such as the desk deactivating because it's
        // occluded by a fullscreen task result in a transition change, but others, such
        // as transitioning from an empty desk to home may not.
        if (!deskChangeFound) {
            logD("Deactivating desk without transition change")
        }
        desktopRepository.setDeskInactive(deskId = deskTransition.deskId)
    }

    private fun handleChangeDeskDisplay(
        info: TransitionInfo,
        deskTransition: DeskTransition.ChangeDeskDisplay,
    ) {
        logD("handleChangeDeskDisplay: %s", deskTransition)
        val deskId = deskTransition.deskId
        val deskChange = info.changes.find { change -> desksOrganizer.isDeskChange(change, deskId) }
        if (deskChange != null) {
            desktopUserRepositories.getRepositoriesWithDeskId(deskId).forEach { desktopRepository ->
                desktopRepository.onDeskDisplayChanged(deskId, deskTransition.displayId)
            }
        }
    }

    private fun handleRemoveDisplay(deskTransition: DeskTransition.RemoveDisplay) {
        logD("handleRemoveDisplay: %s", deskTransition)
        desktopUserRepositories.forAllRepositories { desktopRepository ->
            desktopRepository.removeDisplay(deskTransition.displayId)
        }
    }

    private fun handleIndependentDeskTransitionIfNeeded(info: TransitionInfo) {
        val deskChanges = info.changes.filter { c -> desksOrganizer.isDeskChange(c) }
        if (deskChanges.isEmpty()) return
        logD(
            "handleIndependentDeskTransitionIfNeeded %d desk related changes found",
            deskChanges.size,
        )
        val wct = WindowContainerTransaction()
        for (change in deskChanges.reversed()) {
            val deskId = desksOrganizer.getDeskIdFromChange(change) ?: continue
            when (change.mode) {
                TRANSIT_TO_BACK -> {
                    logD("handleIndependentDeskTransitionIfNeeded desk=%d moved to back", deskId)
                    desksOrganizer.deactivateDesk(wct, deskId, skipReorder = true)
                }
                TRANSIT_TO_FRONT -> {
                    logD("handleIndependentDeskTransitionIfNeeded desk=%d moved to front", deskId)
                    desksOrganizer.activateDesk(wct, deskId, skipReorder = true)
                }
                else -> {
                    logW(
                        "Unexpected change for desk=%d with mode=%",
                        deskId,
                        TransitionInfo.modeToString(change.mode),
                    )
                }
            }
        }
        if (wct.isEmpty) {
            return
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesksTransitionObserver"
    }
}
